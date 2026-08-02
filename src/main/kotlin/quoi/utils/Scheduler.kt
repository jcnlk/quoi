package quoi.utils

import quoi.QuoiMod.mc
import quoi.api.events.TickEvent
import kotlinx.coroutines.CompletableDeferred
import quoi.api.events.core.EventListener
import quoi.api.events.core.on

object Scheduler : EventListener {
    private val clientTasks = mutableListOf<Task>()
    private val serverTasks = mutableListOf<Task>()

    data class Task(
        var delay: Int,
        val repeat: Int = -1,
        val cb: (Task) -> Unit
    ) {
        fun cancel() {
            synchronized(clientTasks) { clientTasks.remove(this) }
            synchronized(serverTasks) { serverTasks.remove(this) }
        }
    }

    init {
        on<TickEvent.End> {
            tick(clientTasks)
        }

        on<TickEvent.Server> {
            tick(serverTasks)
        }
    }

    private fun tick(tasks: MutableList<Task>) {
        val dueTasks = synchronized(tasks) {
            buildList {
                for (i in tasks.size - 1 downTo 0) {
                    val task = tasks[i]

                    if (--task.delay > 0) continue

                    add(task)

                    if (task.repeat >= 0) task.delay = task.repeat
                    else tasks.removeAt(i)
                }
            }
        }

        dueTasks.forEach { task -> mc.submit { task.cb(task) } }
    }

    private fun addTask(task: Task, server: Boolean) {
        val tasks = if (server) serverTasks else clientTasks
        synchronized(tasks) { tasks.add(task) }
    }

    @JvmOverloads
    fun scheduleTask(delay: Int = 0, server: Boolean = false, cb: (Task) -> Unit) {
        addTask(Task(delay, cb = cb), server)
    }

    @JvmOverloads
    fun scheduleLoop(
        interval: Int = 1,
        server: Boolean = false,
        cb: (Task) -> Unit
    ): Task {
        val task = Task(interval, interval, cb)
        addTask(task, server)
        return task
    }

    /**
     * Suspends for [ticks] client or observed server ticks.
     * Server waits always resume on the client thread, including waits with a non-positive delay.
     */
    suspend fun wait(ticks: Int = 1, server: Boolean = false) {
        if (ticks <= 0) {
            if (server) awaitClientThread()
            return
        }

        val deferred = CompletableDeferred<Unit>()

        scheduleTask(ticks, server = server) {
            deferred.complete(Unit)
        }

        deferred.await()
    }

    private suspend fun awaitClientThread() {
        if (mc.isSameThread) return

        val deferred = CompletableDeferred<Unit>()
        mc.submit { deferred.complete(Unit) }
        deferred.await()
    }
}