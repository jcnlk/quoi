package quoi.api.events.core

import org.junit.jupiter.api.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class SubscriptionRegistryTest {
    private class TestEvent : Event()
    private val listener = object : EventListener {}

    private fun subscription(priority: Int) = Subscription<TestEvent>(listener, TestEvent::class.java, priority) {}

    @Test
    fun `adding and removing subscriptions does not change a previously captured dispatch snapshot`() {
        val registry = SubscriptionRegistry<TestEvent>()
        assertTrue(registry.dispatchSnapshot.isEmpty())
        val original = subscription(0)
        val added = subscription(Priority.HIGH)
        registry.add(original)
        val snapshot = registry.dispatchSnapshot

        registry.add(added)
        registry.remove(original)

        assertContentEquals(listOf(original), snapshot.asList())
        assertContentEquals(listOf(added), registry.dispatchSnapshot.asList())
    }

    @Test
    fun `removing an absent subscription preserves the remaining registrations`() {
        val registry = SubscriptionRegistry<TestEvent>()
        val remaining = subscription(0)
        registry.add(remaining)

        registry.remove(subscription(0))
        assertContentEquals(listOf(remaining), registry.dispatchSnapshot.asList())

        registry.remove(remaining)
        assertTrue(registry.dispatchSnapshot.isEmpty())
    }

    @Test
    fun `concurrent registration and removal preserve every remaining subscription in priority order`() {
        val registry = SubscriptionRegistry<TestEvent>()
        val entries = List(200) { subscription(it) }
        val workers = Executors.newFixedThreadPool(4)
        try {
            val tasks = entries.mapIndexed { index, sub ->
                workers.submit {
                    registry.add(sub)
                    if (index % 2 == 0) registry.remove(sub)
                }
            }
            tasks.forEach { it.get(5, TimeUnit.SECONDS) }
        } finally {
            workers.shutdownNow()
        }

        assertContentEquals(entries.filterIndexed { i, _ -> i % 2 != 0 }.reversed(), registry.dispatchSnapshot.asList())
    }
}
