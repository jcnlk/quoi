package quoi.utils

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class NumberFormattingTest {
    @ParameterizedTest
    @CsvSource(
        "0, 0", "999, 999", "1000, 1k", "1234, 1.2k", "1250, 1.3k", "15000, 15k",
        "999949, 999.9k", "999950, 1m", "2500000, 2.5m", "1000000000, 1b", "-1234, -1.2k",
        "1500000000000000000, 1500q",
    )
    fun `short format uses compact suffixes`(value: Double, expected: String) {
        assertEquals(expected, value.shortFormat())
    }

    @Test
    fun `short format respects decimals and locale`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("1.23k", 1234.shortFormat(2))
            assertEquals("1k", 1234.shortFormat(0))
            assertEquals("-9223q", Long.MIN_VALUE.shortFormat(0))
        } finally {
            Locale.setDefault(original)
        }
        assertFailsWith<IllegalArgumentException> { 1.shortFormat(-1) }
    }

    @ParameterizedTest
    @CsvSource(
        "1234, 1234.0", "'1,234', 1234.0", "1.5k, 1500.0", "2.3M, 2300000.0", "1b, 1000000000.0",
        "-4.2k, -4200.0", "' 7 ', 7.0", "0.5t, 500000000000.0",
    )
    fun `parses short numbers`(input: String, expected: Double) {
        assertEquals(expected, input.parseShortNumberOrNull()!!, 1e-6)
    }

    @ParameterizedTest
    @CsvSource("''", "k", "abc", "1.2.3k", "12kk", "1x")
    fun `rejects invalid short numbers`(input: String) {
        assertNull(input.parseShortNumberOrNull())
    }

    @Test
    fun `roman numerals round-trip`() {
        assertEquals("MCMXCIV", 1994.toRoman())
        assertEquals("IV", 4.toRoman())
        for (n in 1..3999) assertEquals(n, romanToInt(n.toRoman()))
        assertFailsWith<IllegalArgumentException> { 0.toRoman() }
        assertFailsWith<IllegalArgumentException> { 4000.toRoman() }
    }

    @ParameterizedTest
    @CsvSource("1, 1st", "2, 2nd", "3, 3rd", "4, 4th", "11, 11th", "12, 12th", "13, 13th", "21, 21st", "112, 112th", "123, 123rd")
    fun `ordinals use English suffixes`(value: Int, expected: String) {
        assertEquals(expected, value.ordinal())
    }

    @Test
    fun `sign, fraction and remap helpers`() {
        assertEquals("+5", 5.withSign())
        assertEquals("0", 0.withSign())
        assertEquals("-5", (-5).withSign())

        assertEquals(0.5, 5.fractionOf(10))
        assertEquals(1.0, 15.fractionOf(10))
        assertEquals(0.0, (-1).fractionOf(10))
        assertEquals(1.0, 3.fractionOf(0))

        assertEquals(50.0, 5.remap(0.0, 10.0, 0.0, 100.0), 1e-9)
        assertEquals(100.0, 20.remap(0.0, 10.0, 0.0, 100.0), 1e-9)
        assertEquals(75.0, 2.5.remap(0.0, 10.0, 100.0, 0.0), 1e-9)
        assertEquals(3.0, 1.remap(1.0, 1.0, 3.0, 9.0), 1e-9)
    }
}
