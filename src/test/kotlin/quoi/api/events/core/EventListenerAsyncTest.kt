package quoi.api.events.core

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import quoi.api.events.TickEvent
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@Timeout(5)
class EventListenerAsyncTest {
    private class MenuOpened : Event()

    private val listener = object : EventListener {}

    @AfterEach
    fun `wait subscriptions are removed after completion or cancellation`() {
        for (eventClass in listOf(MenuOpened::class.java, TickEvent.Server::class.java, TickEvent.Start::class.java)) {
            assertTrue(EventManager.getRegistry<Event>(eventClass).dispatchSnapshot.none { it.listener === listener })
        }
    }

    @Test
    fun `zero timeout returns immediately without leaving subscriptions`() = runBlocking {
        assertNull(listener.await<MenuOpened>(timeout = 0))
    }

    @Test
    fun `indefinite waits ignore ticks until an event matches their predicate`() = runBlocking {
        val expected = MenuOpened()
        val result = async(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            listener.await<MenuOpened> { this === expected }
        }
        try {
            repeat(120) {
                TickEvent.Start().post()
                TickEvent.Server(it).post()
            }
            MenuOpened().post()
            assertFalse(result.isCompleted)

            expected.post()
            assertSame(expected, result.await())
        } finally {
            result.cancel()
        }
    }

    @Test
    fun `predicate failures propagate to the waiter and remove its subscriptions`() = runBlocking {
        supervisorScope {
            val expected = IllegalStateException("predicate failed")
            val result = async(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
                listener.await<MenuOpened>(timeout = 2) { throw expected }
            }
            try {
                MenuOpened().post()

                val error = assertFailsWith<IllegalStateException> { result.await() }
                assertEquals(expected.message, error.message)
            } finally {
                result.cancel()
            }
        }
    }

    @Test
    fun `buffered server ticks do not time out a menu request before its response`() = runBlocking {
        val result = async(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            listener.await<MenuOpened>(timeout = 60, serverTicks = true)
        }
        try {
            repeat(120) { TickEvent.Server(it).post() }

            assertFalse(result.isCompleted)
            val menu = MenuOpened()
            menu.post()
            assertSame(menu, result.await())
        } finally {
            result.cancel()
        }
    }

    @Test
    fun `server timeout waits for its tick budget even when real time has elapsed`() = runBlocking {
        val result = async(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            listener.await<MenuOpened>(timeout = 2, serverTicks = true)
        }
        try {
            delay(110)
            TickEvent.Server(1).post()
            assertFalse(result.isCompleted)

            TickEvent.Server(2).post()
            assertNull(result.await())
        } finally {
            result.cancel()
        }
    }

    @Test
    fun `elapsed time and client ticks do not expire a paused server timeout`() = runBlocking {
        val result = async(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            listener.await<MenuOpened>(timeout = 1, serverTicks = true)
        }
        try {
            delay(60)
            repeat(20) { TickEvent.Start().post() }

            assertFalse(result.isCompleted)
            val menu = MenuOpened()
            menu.post()
            assertSame(menu, result.await())
        } finally {
            result.cancel()
        }
    }

    @Test
    fun `client timeouts still expire after the requested client ticks`() = runBlocking {
        val result = async(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            listener.await<MenuOpened>(timeout = 2)
        }
        try {
            repeat(20) { TickEvent.Server(it).post() }
            TickEvent.Start().post()
            assertFalse(result.isCompleted)

            TickEvent.Start().post()
            assertNull(result.await())
        } finally {
            result.cancel()
        }
    }

    @Test
    fun `cancellation removes a server wait after a tick burst`() = runBlocking {
        val result = async(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            listener.await<MenuOpened>(timeout = 60, serverTicks = true)
        }
        try {
            repeat(120) { TickEvent.Server(it).post() }
            assertFalse(result.isCompleted)
        } finally {
            result.cancel()
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `tick waits count only their selected clock and unsubscribe on completion`(server: Boolean) = runBlocking {
        val eventType = if (server) TickEvent.Server::class.java else TickEvent.Start::class.java
        val registry = EventManager.getRegistry<Event>(eventType)
        val before = registry.dispatchSnapshot.toList()
        val wait = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            listener.wait(2, server)
        }

        try {
            repeat(3) { if (server) TickEvent.Start().post() else TickEvent.Server(it).post() }
            if (server) TickEvent.Server(3).post() else TickEvent.Start().post()
            assertFalse(wait.isCompleted)

            if (server) TickEvent.Server(4).post() else TickEvent.Start().post()
            assertTrue(wait.isCompleted)
            assertEquals(before, registry.dispatchSnapshot.toList())
        } finally {
            wait.cancelAndJoin()
        }
    }

    @Test
    fun `cancelling a tick wait removes its subscription before another tick`() = runBlocking {
        val registry = EventManager.getRegistry<Event>(TickEvent.Start::class.java)
        val before = registry.dispatchSnapshot.toList()
        val wait = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            listener.wait(2)
        }

        try {
            assertEquals(before.size + 1, registry.dispatchSnapshot.size)
        } finally {
            wait.cancelAndJoin()
        }
        assertEquals(before, registry.dispatchSnapshot.toList())
    }
}
