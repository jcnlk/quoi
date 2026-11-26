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
        "999, 999", "1000, 1k", "1250, 1.3k", "999949, 999.9k", "999950, 1m", "-1234, -1.2k",
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
        "'1,234', 1234.0", "2.3M, 2300000.0", "-4.2k, -4200.0", "' 7 ', 7.0", "0.5t, 500000000000.0",
    )
    fun `parses short numbers`(input: String, expected: Double) {
        assertEquals(expected, input.parseShortNumberOrNull()!!, 1e-6)
    }

    @ParameterizedTest
    @CsvSource("''", "k", "abc", "1.2.3k", "12kk", "1x")
    fun `rejects invalid short numbers`(input: String) {
        assertNull(input.parseShortNumberOrNull())
    }
}
