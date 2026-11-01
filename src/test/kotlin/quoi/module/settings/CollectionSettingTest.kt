package quoi.module.settings

import com.google.gson.JsonParser
import org.junit.jupiter.api.Test
import quoi.module.settings.impl.ListSetting
import quoi.module.settings.impl.MapSetting
import kotlin.test.assertEquals
import kotlin.test.assertSame

class CollectionSettingTest {
    private data class Entry(val label: String, val count: Int)

    @Test
    fun `list JSON roundtrip preserves typed entries order and duplicates`() {
        val expected = listOf(Entry("first", 1), Entry("second", 2), Entry("first", 1))
        val source = ListSetting("Entries", expected.toMutableList())
        val backing = mutableListOf(Entry("stale", 0))
        val restored = ListSetting("Entries", backing)

        val saved = JsonParser.parseString(source.write().toString())
        restored.read(saved)

        assertSame(backing, restored.value)
        assertEquals(expected, backing)
        assertEquals(saved, restored.write())
        assertEquals(expected, source.value)
    }

    @Test
    fun `repeated list loading replaces contents and accepts an empty array`() {
        val backing = mutableListOf(99)
        val setting = ListSetting("Values", backing)

        setting.read(JsonParser.parseString("[1,2]"))
        setting.read(JsonParser.parseString("[3]"))
        assertEquals(listOf(3), backing)

        setting.read(JsonParser.parseString("[]"))
        assertSame(backing, setting.value)
        assertEquals(emptyList(), backing)
    }

    @Test
    fun `map JSON roundtrip preserves nested value types and the backing reference`() {
        val expected = mapOf("group" to listOf(Entry("first", 1), Entry("second", 2)))
        val source = MapSetting("Entries", expected.toMutableMap())
        val backing = mutableMapOf("stale" to listOf(Entry("old", 0)))
        val restored = MapSetting("Entries", backing)

        val saved = JsonParser.parseString(source.write().toString())
        restored.read(saved)

        assertSame(backing, restored.value)
        assertEquals(expected, backing)
        assertEquals(saved, restored.write())
        assertEquals(expected, source.value)
    }

    @Test
    fun `repeated map loading removes stale keys and accepts an empty object`() {
        val backing = mutableMapOf("old" to 99)
        val setting = MapSetting("Values", backing)

        setting.read(JsonParser.parseString("{\"first\":1,\"second\":2}"))
        setting.read(JsonParser.parseString("{\"first\":3}"))
        assertEquals(mapOf("first" to 3), backing)

        setting.read(JsonParser.parseString("{}"))
        assertSame(backing, setting.value)
        assertEquals(emptyMap(), backing)
    }
}
