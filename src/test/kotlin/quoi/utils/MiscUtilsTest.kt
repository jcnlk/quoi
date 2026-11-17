package quoi.utils

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MiscUtilsTest {
    private enum class Mode { A, B, C }

    @Test
    fun `timestamps report elapsed time`() {
        val now = System.currentTimeMillis()
        assertTrue((now - 1_000).millisSince() >= 1_000)
        assertTrue((now - 1_000).hasPassed(500))
        assertFalse((now + 60_000).hasPassed(0))

        val (result, millis) = measureMillis { 42 }
        assertEquals(42, result)
        assertTrue(millis >= 0)
    }

    @Test
    fun `lists wrap around`() {
        val list = listOf("a", "b", "c")
        assertEquals("c", list.getWrapped(-1))
        assertEquals("a", list.getWrapped(3))
        assertEquals("b", list.nextAfter("a"))
        assertEquals("a", list.nextAfter("c"))
        assertEquals("a", list.nextAfter("missing"))
        assertEquals("c", list.previousBefore("a"))
        assertEquals("c", list.previousBefore("missing"))
        assertFailsWith<IllegalArgumentException> { emptyList<String>().getWrapped(0) }
    }

    @Test
    fun `enums cycle and parse ignoring case`() {
        assertEquals(Mode.B, Mode.A.next())
        assertEquals(Mode.A, Mode.C.next())
        assertEquals(Mode.C, Mode.A.previous())
        assertEquals(Mode.B, enumValueOfOrNull<Mode>("b"))
        assertNull(enumValueOfOrNull<Mode>("d"))
    }

    @Test
    fun `maps accumulate counts`() {
        val ints = mutableMapOf<String, Int>()
        ints.addCount("x")
        ints.addCount("x", 4)
        val longs = mutableMapOf<String, Long>()
        longs.addCount("x", 3L)
        val doubles = mutableMapOf<String, Double>()
        doubles.addCount("x", 1.5)
        doubles.addCount("x", 1.5)

        assertEquals(5, ints["x"])
        assertEquals(3L, longs["x"])
        assertEquals(3.0, doubles["x"])
    }
}
