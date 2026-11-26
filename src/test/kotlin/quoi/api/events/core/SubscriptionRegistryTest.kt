package quoi.api.events.core

import org.junit.jupiter.api.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertContentEquals

class SubscriptionRegistryTest {
    private class TestEvent : Event()

    private val listener = object : EventListener {}

    private fun subscription(priority: Int) = Subscription<TestEvent>(listener, TestEvent::class.java, priority) {}

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
