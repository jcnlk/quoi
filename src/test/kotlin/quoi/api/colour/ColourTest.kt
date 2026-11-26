package quoi.api.colour

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ColourTest {
    @Test
    fun `RGBA packing preserves all channels including the signed alpha byte`() {
        val packed = getRGBA(0x12, 0x34, 0x56, 0xAB)

        assertEquals(0xAB123456.toInt(), packed)
        assertEquals(0x12, packed.red)
        assertEquals(0x34, packed.green)
        assertEquals(0x56, packed.blue)
        assertEquals(0xAB, packed.alpha)
        assertEquals(0x80123456.toInt(), getRGBA(0x12, 0x34, 0x56, 0.5f))
    }

    @ParameterizedTest
    @CsvSource(
        "'#123456', '#123456FF'",
        "'#aBcDeF', '#ABCDEFFF'",
        "'#12345680', '#12345680'",
        "'#00000000', '#00000000'",
        "'#FFFFFFFF', '#FFFFFFFF'"
    )
    fun `hex colors roundtrip with explicit alpha and normalized casing`(input: String, expected: String) {
        val colour = Colour.RGB(hexToRGBA(input))

        assertEquals(expected, colour.toHexString(returnAlpha = true))
        assertEquals(expected.take(7), colour.toHexString())
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "#123", "#1234567", "#GG0000", "#123456ZZ"])
    fun `invalid hex lengths and digits are rejected`(input: String) {
        assertFailsWith<IllegalArgumentException> { hexToRGBA(input) }
    }

    @Test
    fun `HSB updates invalidate cached RGB values for every component`() {
        val colour = Colour.HSB(0f, 1f, 1f)
        assertEquals("#FF0000FF", colour.toHexString(returnAlpha = true))

        colour.hue = 1f / 3f
        assertEquals("#00FF00FF", colour.toHexString(returnAlpha = true))
        colour.saturation = 0f
        assertEquals("#FFFFFFFF", colour.toHexString(returnAlpha = true))
        colour.brightness = 0f
        assertEquals("#000000FF", colour.toHexString(returnAlpha = true))
        colour.alpha = 0f
        assertEquals("#00000000", colour.toHexString(returnAlpha = true))
    }

    @Test
    fun `RGB to HSB conversion preserves color and transparency`() {
        val original = Colour.RGB(0x12, 0xA4, 0xE7, 128 / 255f)

        assertEquals(original.rgb, original.toHSB().rgb)
    }
}
