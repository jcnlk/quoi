package quoi.api.events.core

import kotlinx.coroutines.*
import net.minecraft.network.protocol.Packet
import quoi.QuoiMod.mc
import quoi.api.events.PacketEvent
import quoi.api.events.TickEvent
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.*

class ListenerInactiveException(listener: EventListener) :
    CancellationException("listener ${listener::class.simpleName} is no longer active")

/**
 * prevents suspended code from executing after [EventListener.running] is set to false
 */
private class ActiveStateInterceptor(
    private val cont: ContinuationInterceptor?,
    private val listener: EventListener,
) : AbstractCoroutineContextElement(ContinuationInterceptor), ContinuationInterceptor {

    override fun <T> interceptContinuation(continuation: Continuation<T>): Continuation<T> {

        val cont = cont?.interceptContinuation(continuation) ?: continuation

        return object : Continuation<T> {

            override val context get() = continuation.context

            override fun resumeWith(result: Result<T>) {
                val res = if (listener.running) result
                else Result.failure(ListenerInactiveException(listener))
                cont.resumeWith(res)
            }
        }
    }
}

private val asyncScopes = ConcurrentHashMap<EventListener, CoroutineScope>()

val EventListener.asyncScope: CoroutineScope
    get() = asyncScopes.compute(this) { listener, scope ->
        if (scope != null && scope.coroutineContext[Job]?.isActive == true) {
            return@compute scope
        }

        val parentJob = listener.parent()?.asyncScope?.coroutineContext?.get(Job)
        val job = if (parentJob != null) SupervisorJob(parentJob) else SupervisorJob()

        job.invokeOnCompletion {
            asyncScopes.remove(listener)
        }

        CoroutineScope(
            job +
            CoroutineExceptionHandler { _, t -> if (t !is ListenerInactiveException) t.printStackTrace() } +
            ActiveStateInterceptor(mc.asCoroutineDispatcher(), listener)
        )
    }!!

/**
 * cancels all running async tasks for this [EventListener]
 */
fun EventListener.removeAsyncScope() {
    asyncScopes.remove(this)?.cancel(ListenerInactiveException(this))
}


/**
 * asynccronouseaelye subscribes to [Event]
 * if the job is active, the new event dispatch is discarded
 */
inline fun <reified T : Event> EventListener.onAsync(
    priority: Int = 0,
    acceptCancelled: Boolean = false,
    crossinline block: suspend CoroutineScope.(T) -> Unit
): Subscription<T> {
    var job: Job? = null

    return on<T>(priority, acceptCancelled) {
        if (job?.isActive == true) return@on // maybe make this part customisable

        job = asyncScope.launch {
            block(this@on)
        }
    }
}

/**
 * Suspends execution for [ticks] amount
 */
suspend fun EventListener.wait(ticks: Int) {
    if (ticks <= 0) return
    var remaining = ticks

    suspendCancellableCoroutine { cont ->
        val subscription = until<TickEvent.Start> {
            remaining--
            if (remaining <= 0) {
                if (cont.isActive) cont.resume(Unit)
                true
            } else {
                false
            }
        }

        cont.invokeOnCancellation { subscription.unregister() }
    }
}

/**
 * Suspends until an event matches [predicate]
 * @param timeout ticks to wait. negative waits indefinitely, 0 returns immediately
 * @param serverTicks count server ticks instead of client ticks for the timeout
 * @return the matching event, or `null` on timeout
 */
suspend inline fun <reified T : Event> EventListener.await(
    priority: Int = 0,
    acceptCancelled: Boolean = false,
    timeout: Int = -1,
    serverTicks: Boolean = false,
    crossinline predicate: T.() -> Boolean = { true },
): T? {
    if (timeout == 0) return null

    return suspendCancellableCoroutine { cont ->
        var ticks = 0
        val settled = AtomicBoolean()
        var timeoutSub: Subscription<*>? = null
        lateinit var eventSub: Subscription<T>
        eventSub = on<T>(priority, acceptCancelled, register = false) {
            if (settled.get()) return@on
            try {
                if (predicate(this) && settled.compareAndSet(false, true)) {
                    eventSub.unregister()
                    timeoutSub?.unregister()
                    cont.resume(this)
                }
            } catch (e: Exception) {
                if (settled.compareAndSet(false, true)) {
                    eventSub.unregister()
                    timeoutSub?.unregister()
                    cont.resumeWithException(e)
                }
            }
        }
        if (timeout > 0) {
            val onTimeoutTick: () -> Unit = {
                if (++ticks >= timeout && settled.compareAndSet(false, true)) {
                    eventSub.unregister()
                    timeoutSub?.unregister()
                    cont.resume(null)
                }
            }
            timeoutSub = if (serverTicks) {
                on<TickEvent.Server>(priority, register = false) { onTimeoutTick() }
            } else {
                on<TickEvent.Start>(priority, register = false) { onTimeoutTick() }
            }
        }

        EventManager.register(eventSub)
        timeoutSub?.let(EventManager::register)
        cont.invokeOnCancellation {
            settled.set(true)
            eventSub.unregister()
            timeoutSub?.unregister()
        }
        // the event may have arrived before the timeout subscription was registered
        if (settled.get()) {
            eventSub.unregister()
            timeoutSub?.unregister()
        }
    }
}

/**
 * [await] filtered to packets
 */
@JvmName("awaitPacket")
suspend inline fun <reified E, reified P : Packet<*>> EventListener.await(
    priority: Int = 0,
    acceptCancelled: Boolean = false,
    timeout: Int = -1,
    serverTicks: Boolean = false,
    crossinline predicate: PacketScope<E, P>.() -> Boolean = { true },
): P? where E : Event, E : PacketEvent = await<E>(priority, acceptCancelled, timeout, serverTicks) {
    packet is P && predicate(PacketScope(this, packet as P))
}?.packet as? P