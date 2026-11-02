package quoi.api.events.core

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
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
    fun `server timeout expires after ticks and elapsed time`() = runBlocking {
        val result = async(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            listener.await<MenuOpened>(timeout = 1, serverTicks = true)
        }
        try {
            delay(60)
            TickEvent.Server(1).post()

            assertNull(result.await())
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
}
