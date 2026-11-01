package quoi.utils

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import quoi.utils.StringUtils.containsOneOf
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.StringUtils.startsWithOneOf
import quoi.utils.StringUtils.substringSafe
import quoi.utils.StringUtils.toFixed
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StringUtilsTest {
    @ParameterizedTest
    @CsvSource(
        "0, 0s",
        "1, 0.00s",
        "1250, 1.25s",
        "59990, 59.99s",
        "60000, 1m",
        "61250, 1m 1.25s",
        "3600000, 1h",
        "3661250, 1h 1m 1.25s",
        "86400000, 1d",
        "90061250, 1d 1h 1m 1.25s",
    )
    fun `formats millisecond durations across unit boundaries`(milliseconds: Long, expected: String) {
        assertEquals(expected, StringUtils.formatTime(milliseconds))
    }

    @Test
    fun `time formatting respects precision and hidden units`() {
        assertEquals("1m 1.3s", StringUtils.formatTime(61_250, decimalPlaces = 1))
        assertEquals("1m", StringUtils.formatTime(61_250, showSeconds = false))
        assertEquals("1.25s", StringUtils.formatTime(61_250, showMinutes = false))
        assertEquals("1m 1.25s", StringUtils.formatTime(90_061_250, showDays = false, showHours = false))
    }

    @Test
    fun `hidden time units fall back only when requested`() {
        assertEquals("1m", StringUtils.formatTime(60_000, showMinutes = false))
        assertEquals("", StringUtils.formatTime(60_000, showMinutes = false, forceIfEmpty = false))
        assertEquals("1h", StringUtils.formatTime(3_600_000, showHours = false))
        assertEquals("1d", StringUtils.formatTime(86_400_000, showDays = false))
    }

    @Test
    fun `fixed numbers and time formatting use decimal points regardless of default locale`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)

            assertEquals("12.50", 12.5.toFixed())
            assertEquals("-1.250", (-1.25).toFixed(3))
            assertEquals("1.25s", StringUtils.formatTime(1_250))
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun `prefix matching supports alternatives and optional case folding`() {
        assertTrue("Hello world".startsWithOneOf("missing", "Hello"))
        assertFalse("Hello world".startsWithOneOf("hello", "world"))
        assertTrue("Hello world".startsWithOneOf("hello", ignoreCase = true))
        assertFalse("Hello world".startsWithOneOf())
    }

    @Test
    fun `substring matching handles varargs collections and empty alternatives consistently`() {
        val text = "Hello world"
        val options = listOf("missing", "WORLD")

        assertFalse(text.containsOneOf(*options.toTypedArray()))
        assertFalse(text.containsOneOf(options))
        assertTrue(text.containsOneOf(*options.toTypedArray(), ignoreCase = true))
        assertTrue(text.containsOneOf(options, ignoreCase = true))
        assertTrue(text.containsOneOf("missing", "world"))
        assertFalse(text.containsOneOf())
        assertFalse(text.containsOneOf(emptyList()))
    }

    @ParameterizedTest
    @CsvSource("1, 4, bcd", "4, 1, bcd", "-2, 3, abc", "3, 99, def", "0, 6, abcdef", "2, 2, ''")
    fun `safe substring handles reversed bounds negative starts and oversized ends`(from: Int, to: Int, expected: String) {
        assertEquals(expected, "abcdef".substringSafe(from, to))
    }

    @Test
    fun `control code extension accepts null and preserves Unicode text`() {
        val absent: String? = null

        assertEquals("", absent.noControlCodes)
        assertEquals("猫 😺", "§a猫 &l😺§r".noControlCodes)
        assertEquals("Fish & chips", "Fish & chips".noControlCodes)
    }
}
