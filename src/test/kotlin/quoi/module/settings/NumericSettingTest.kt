package quoi.module.settings

import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import quoi.module.settings.impl.RangeSliderComponent
import quoi.module.settings.impl.SliderComponent
import kotlin.test.assertEquals

class NumericSettingTest {
    @ParameterizedTest
    @CsvSource("-100.0, -1.0", "-0.26, -0.3", "0.0, 0.0", "0.26, 0.3", "0.30000000000000004, 0.3", "100.0, 1.0")
    fun `slider snaps to its increment and clamps to its bounds`(input: Double, expected: Double) {
        val setting = SliderComponent("Value", default = 0.0, min = -1.0, max = 1.0, increment = 0.1)

        setting.set(input)

        assertEquals(expected, setting.value, 1e-9)
    }

    @Test
    fun `slider assignments preserve each supported numeric type`() {
        val intSetting = SliderComponent("Int", default = 0, min = -10, max = 10, increment = 2)
        val longSetting = SliderComponent("Long", default = 0L, min = -10L, max = 10L, increment = 2)
        val floatSetting = SliderComponent("Float", default = 0f, min = -10f, max = 10f, increment = 0.25)
        val doubleSetting = SliderComponent("Double", default = 0.0, min = -10.0, max = 10.0, increment = 0.25)

        intSetting.set(3.2)
        longSetting.set(3.2)
        floatSetting.set(0.62)
        doubleSetting.set(0.62)

        assertEquals(4, intSetting.value)
        assertEquals(4L, longSetting.value)
        assertEquals(0.5f, floatSetting.value)
        assertEquals(0.5, doubleSetting.value)
    }

    @Test
    fun `slider JSON loading preserves numeric types`() {
        val intSetting = SliderComponent("Int", default = 1)
        val longSetting = SliderComponent("Long", default = 1L, min = 0L, max = Long.MAX_VALUE)
        val floatSetting = SliderComponent("Float", default = 1f)
        val doubleSetting = SliderComponent("Double", default = 1.0)

        intSetting.read(JsonParser.parseString("7"))
        longSetting.read(JsonParser.parseString("3000000000"))
        floatSetting.read(JsonParser.parseString("1.25"))
        doubleSetting.read(JsonParser.parseString("1.25"))

        assertEquals(7, intSetting.value)
        assertEquals(3_000_000_000L, longSetting.value)
        assertEquals(1.25f, floatSetting.value)
        assertEquals(1.25, doubleSetting.value)
    }

    @Test
    fun `slider values survive serialization and reset to their default`() {
        val source = SliderComponent("Value", default = 2.0, min = 0.0, max = 10.0, increment = 0.25)
        val restored = SliderComponent("Value", default = 2.0, min = 0.0, max = 10.0, increment = 0.25)
        source.set(3.74)

        val saved = source.write()
        assertEquals(JsonPrimitive(3.75), saved)
        restored.read(JsonParser.parseString(saved.toString()))
        assertEquals(3.75, restored.value)

        restored.reset()
        assertEquals(2.0, restored.value)
    }

    @ParameterizedTest
    @CsvSource("8.4, 2.6, 3.0, 8.0", "-10.0, 20.0, 0.0, 10.0", "4.4, 4.4, 4.0, 4.0", "2.0, 8.0, 2.0, 8.0")
    fun `range slider orders endpoints after snapping and clamping`(first: Double, second: Double, low: Double, high: Double) {
        val setting = RangeSliderComponent("Range", default = 1.0 to 9.0, min = 0.0, max = 10.0)

        setting.set(first, second)

        assertEquals(low to high, setting.value)
    }

    @Test
    fun `range slider preserves fractional increments without floating point artifacts`() {
        val setting = RangeSliderComponent("Range", default = 0.0 to 1.0, min = 0.0, max = 1.0, increment = 0.1)

        setting.set(0.30000000000000004, 0.76)

        assertEquals(0.3 to 0.8, setting.value)
    }

    @Test
    fun `range values survive serialization and reset to their default`() {
        val source = RangeSliderComponent("Range", default = 1 to 9, min = 0, max = 10)
        val restored = RangeSliderComponent("Range", default = 1 to 9, min = 0, max = 10)
        source.set(8, 3)

        val saved = source.write()
        assertEquals(JsonParser.parseString("[3,8]"), saved)
        restored.read(JsonParser.parseString(saved.toString()))
        assertEquals(3 to 8, restored.value)

        restored.reset()
        assertEquals(1 to 9, restored.value)
    }

    @Test
    fun `range JSON loading preserves each supported numeric type`() {
        val intSetting = RangeSliderComponent("Int", default = 1 to 9)
        val longSetting = RangeSliderComponent("Long", default = 0L to 4_000_000_000L, min = 0L, max = Long.MAX_VALUE)
        val floatSetting = RangeSliderComponent("Float", default = 0f to 1f)
        val doubleSetting = RangeSliderComponent("Double", default = 0.0 to 1.0)

        intSetting.read(JsonParser.parseString("[3,7]"))
        longSetting.read(JsonParser.parseString("[3000000000,4000000000]"))
        floatSetting.read(JsonParser.parseString("[0.25,0.75]"))
        doubleSetting.read(JsonParser.parseString("[0.25,0.75]"))

        assertEquals(3 to 7, intSetting.value)
        assertEquals(3_000_000_000L to 4_000_000_000L, longSetting.value)
        assertEquals(0.25f to 0.75f, floatSetting.value)
        assertEquals(0.25 to 0.75, doubleSetting.value)
        assertEquals(JsonParser.parseString("[0.25,0.75]"), doubleSetting.write())
    }
}
