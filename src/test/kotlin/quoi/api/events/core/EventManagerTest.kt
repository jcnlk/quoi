package quoi.api.events.core

import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket
import net.minecraft.network.protocol.common.ClientboundPingPacket
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import quoi.api.events.PacketEvent
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class EventManagerTest {
    private class TestEvent : Event()
    private class OtherEvent : Event()
    private class CancelEvent : CancellableEvent()

    private val listener = object : EventListener {}
    private val subscriptions = mutableListOf<Subscription<*>>()

    private fun <T : Event> Subscription<T>.track() = apply { subscriptions.add(this) }

    @AfterEach
    fun unregisterSubscriptions() {
        subscriptions.forEach { it.unregister() }
    }

    @Test
    fun `dispatches higher priorities first and preserves registration order for ties`() {
        val calls = mutableListOf<String>()
        listener.on<TestEvent>(Priority.LOW) { calls.add("low") }.track()
        listener.on<TestEvent>(Priority.HIGH) { calls.add("first high") }.track()
        listener.on<TestEvent> { calls.add("normal") }.track()
        listener.on<TestEvent>(Priority.HIGH) { calls.add("second high") }.track()

        assertFalse(TestEvent().post())

        assertEquals(listOf("first high", "second high", "normal", "low"), calls)
    }

    @Test
    fun `only dispatches to subscriptions for the posted event type`() {
        var calls = 0
        listener.on<OtherEvent> { calls++ }.track()
        listener.on<Event> { calls++ }.track()
        listener.on<TestEvent> { calls++ }.track()

        TestEvent().post()

        assertEquals(1, calls)
    }

    @Test
    fun `cancellation skips ordinary handlers but delivers to handlers accepting cancellation`() {
        val calls = mutableListOf<String>()
        listener.on<CancelEvent>(Priority.HIGH) {
            calls.add("cancel")
            cancel()
        }.track()
        listener.on<CancelEvent> { calls.add("skipped") }.track()
        listener.on<CancelEvent>(Priority.LOW, acceptCancelled = true) { calls.add("observer") }.track()
        val event = CancelEvent()

        assertTrue(event.post())
        assertEquals(listOf("cancel", "observer"), calls)
        assertTrue(event.completed)
        assertNull(event.subscription)
    }

    @Test
    fun `completed events cannot be cancelled afterwards`() {
        listener.on<CancelEvent> {}.track()
        val event = CancelEvent()

        assertFalse(event.post())
        event.cancel()

        assertFalse(event.cancelled)
    }

    @Test
    fun `inactive listeners are skipped before checking their event filter`() {
        var filterCalls = 0
        var calls = 0
        val gated = object : EventListener {
            override var running = false
            override fun shouldHandle(event: Event): Boolean {
                filterCalls++
                return true
            }
        }
        gated.on<TestEvent> { calls++ }.track()

        TestEvent().post()
        assertEquals(0, filterCalls)
        assertEquals(0, calls)

        gated.running = true
        TestEvent().post()
        assertEquals(1, filterCalls)
        assertEquals(1, calls)
    }

    @Test
    fun `child listeners inherit running state and event filtering from their parent`() {
        var allowed = false
        val parent = object : EventListener {
            override var running = false
            override fun shouldHandle(event: Event) = allowed
        }
        val child = object : EventListener {
            override fun parent() = parent
        }
        var calls = 0
        child.on<TestEvent> { calls++ }.track()

        TestEvent().post()
        parent.running = true
        TestEvent().post()
        assertEquals(0, calls)

        allowed = true
        TestEvent().post()
        assertEquals(1, calls)
    }

    @Test
    fun `handlers can unregister themselves and dispatch state is cleared afterwards`() {
        var calls = 0
        val sub = listener.on<TestEvent> {
            assertFalse(completed)
            assertSame(subscriptions.single(), subscription)
            calls++
            unregister()
        }.track()
        val event = TestEvent()

        event.post()
        TestEvent().post()

        assertEquals(1, calls)
        assertTrue(event.completed)
        assertNull(event.subscription)
        assertFalse(EventManager.getRegistry<TestEvent>(TestEvent::class.java).dispatchSnapshot.contains(sub))
    }

    @Test
    fun `changes to subscriptions during dispatch take effect on the next event`() {
        val calls = mutableListOf<String>()
        lateinit var removed: Subscription<TestEvent>
        listener.once<TestEvent>(Priority.HIGH) {
            calls.add("mutate")
            removed.unregister()
            listener.on<TestEvent> { calls.add("added") }.track()
        }.track()
        removed = listener.on<TestEvent> { calls.add("removed") }.track()

        TestEvent().post()
        assertEquals(listOf("mutate", "removed"), calls)

        calls.clear()
        TestEvent().post()
        assertEquals(listOf("added"), calls)
    }

    @Test
    fun `once and repeated handlers unregister after their requested number of dispatches`() {
        var onceCalls = 0
        var repeatedCalls = 0
        listener.once<TestEvent> { onceCalls++ }.track()
        listener.repeated<TestEvent>(3) { repeatedCalls++ }.track()

        repeat(5) { TestEvent().post() }

        assertEquals(1, onceCalls)
        assertEquals(3, repeatedCalls)
    }

    @Test
    fun `once packet handlers wait for the first matching packet`() {
        val calls = mutableListOf<ClientboundPingPacket>()
        val firstMatch = ClientboundPingPacket(1)
        listener.once<PacketEvent.Received, ClientboundPingPacket> { calls.add(packet) }.track()

        PacketEvent.Received(ClientboundKeepAlivePacket(0L)).post()
        assertTrue(calls.isEmpty())

        PacketEvent.Received(firstMatch).post()
        assertEquals(listOf(firstMatch), calls)

        PacketEvent.Received(ClientboundPingPacket(2)).post()
        assertEquals(listOf(firstMatch), calls)
    }

    @Test
    fun `until remains subscribed until its condition succeeds`() {
        var calls = 0
        var finished = false
        listener.until<TestEvent> {
            calls++
            finished
        }.track()

        repeat(2) { TestEvent().post() }
        finished = true
        repeat(2) { TestEvent().post() }

        assertEquals(3, calls)
    }

    @Test
    fun `repeated rejects zero and negative counts`() {
        for (times in listOf(0, -1)) {
            assertFailsWith<IllegalArgumentException> {
                listener.repeated<TestEvent>(times) {}.track()
            }
        }
    }

    @Test
    fun `subscriptions created without registration only receive events after explicit registration`() {
        var calls = 0
        val sub = listener.on<TestEvent>(register = false) { calls++ }.track()

        TestEvent().post()
        assertEquals(0, calls)

        EventManager.register(sub)
        TestEvent().post()
        assertEquals(1, calls)

        sub.unregister()
        TestEvent().post()
        assertEquals(1, calls)
    }
}
