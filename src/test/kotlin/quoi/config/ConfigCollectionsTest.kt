package quoi.config

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.File
import java.io.IOException
import java.util.AbstractMap.SimpleEntry
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class ConfigCollectionsTest {
    @Test
    fun `list mutations update the backing collection before notifying persistence`() {
        val backing = mutableListOf("first", "third")
        val saved = mutableListOf<List<String>>()
        val list = ConfigList(backing, { saved.add(backing.toList()) }, {})

        list.add(1, "second")
        assertEquals("first", list.set(0, "updated"))
        assertEquals("third", list.removeAt(2))

        assertEquals(listOf("updated", "second"), list.toList())
        assertEquals(
            listOf(listOf("first", "second", "third"), listOf("updated", "second", "third"), listOf("updated", "second")),
            saved,
        )
    }

    @Test
    fun `bulk list operations persist their resulting contents`() {
        val backing = mutableListOf(1, 2)
        val saved = mutableListOf<List<Int>>()
        val list = ConfigList(backing, { saved.add(backing.toList()) }, {})

        list.addAll(listOf(3, 4))
        list.addAll(1, listOf(5, 6))
        list.removeAll(listOf(2, 5))
        list.retainAll(listOf(1, 3))
        list.clear()

        assertEquals(listOf(listOf(1, 2, 3, 4), listOf(1, 5, 6, 2, 3, 4), listOf(1, 6, 3, 4), listOf(1, 3), emptyList()), saved)
        assertEquals(emptyList(), list.toList())
    }

    @Test
    fun `list iterator mutations trigger persistence`() {
        var saves = 0
        val list = ConfigList(mutableListOf("first", "second"), { saves++ }, {})
        val iterator = list.listIterator()

        assertEquals("first", iterator.next())
        iterator.set("updated")
        iterator.add("inserted")
        assertEquals("second", iterator.next())
        iterator.remove()

        assertEquals(listOf("updated", "inserted"), list.toList())
        assertEquals(3, saves)
    }

    @Test
    fun `failed list mutations do not notify persistence or change the collection`() {
        var saves = 0
        val list = ConfigList(mutableListOf("kept"), { saves++ }, {})

        assertFailsWith<IndexOutOfBoundsException> { list.add(3, "invalid") }
        assertFailsWith<IndexOutOfBoundsException> { list.removeAt(3) }
        assertFailsWith<IndexOutOfBoundsException> { list[3] = "invalid" }

        assertEquals(listOf("kept"), list.toList())
        assertEquals(0, saves)
    }

    @Test
    fun `map mutations return previous values and notify persistence with updated contents`() {
        val backing = mutableMapOf("first" to 1)
        val saved = mutableListOf<Map<String, Int>>()
        val map = ConfigMap(backing, { saved.add(backing.toMap()) }, {})

        assertEquals(1, map.put("first", 2))
        map.putAll(mapOf("second" to 3, "third" to 4))
        assertEquals(3, map.remove("second"))
        map.clear()

        assertEquals(listOf(mapOf("first" to 2), mapOf("first" to 2, "second" to 3, "third" to 4), mapOf("first" to 2, "third" to 4), emptyMap()), saved)
        assertEquals(emptyMap(), map.toMap())
    }

    @ParameterizedTest
    @ValueSource(strings = ["entries", "keys", "values", "entry iterator", "key iterator", "value iterator", "removeAll", "retainAll"])
    fun `map view removals persist the updated contents`(view: String) {
        val backing = mutableMapOf("removed" to 1, "kept" to 2)
        val saved = mutableListOf<Map<String, Int>>()
        val map = ConfigMap(backing, { saved.add(backing.toMap()) }, {})

        when (view) {
            "entries" -> map.entries.remove(SimpleEntry("removed", 1))
            "keys" -> map.keys.remove("removed")
            "values" -> map.values.remove(1)
            "entry iterator" -> map.entries.iterator().apply { next() }.remove()
            "key iterator" -> map.keys.iterator().apply { next() }.remove()
            "value iterator" -> map.values.iterator().apply { next() }.remove()
            "removeAll" -> map.entries.removeAll(setOf(SimpleEntry("removed", 1)))
            "retainAll" -> map.entries.retainAll(setOf(SimpleEntry("kept", 2)))
            else -> error("Unknown view: $view")
        }

        assertEquals(mapOf("kept" to 2), backing)
        assertEquals(listOf(mapOf("kept" to 2)), saved)
    }

    @ParameterizedTest
    @ValueSource(strings = ["entries", "keys", "values"])
    fun `clearing a map view persists once after removing all entries`(view: String) {
        val backing = mutableMapOf("first" to 1, "second" to 2)
        val saved = mutableListOf<Map<String, Int>>()
        val map = ConfigMap(backing, { saved.add(backing.toMap()) }, {})

        when (view) {
            "entries" -> map.entries.clear()
            "keys" -> map.keys.clear()
            "values" -> map.values.clear()
            else -> error("Unknown view: $view")
        }

        assertEquals(emptyMap(), backing)
        assertEquals(listOf(emptyMap()), saved)
    }

    @Test
    fun `map entry value updates persist and preserve entry equality and hashing`() {
        val backing = mutableMapOf("key" to 1)
        val saved = mutableListOf<Map<String, Int>>()
        val map = ConfigMap(backing, { saved.add(backing.toMap()) }, {})
        val entry = map.entries.single()

        assertEquals(1, entry.setValue(2))

        assertEquals(mapOf("key" to 2), backing)
        assertEquals(listOf(mapOf("key" to 2)), saved)
        assertEquals(SimpleEntry("key", 2), entry)
        assertEquals(SimpleEntry("key", 2).hashCode(), entry.hashCode())
        assertEquals(backing.hashCode(), map.hashCode())
    }

    @Test
    fun `failed and absent map view mutations do not notify persistence`() {
        var saves = 0
        val map = ConfigMap(mutableMapOf("kept" to 1), { saves++ }, {})

        assertFailsWith<UnsupportedOperationException> { map.entries.add(SimpleEntry("added", 2)) }
        assertFailsWith<IllegalStateException> { map.entries.iterator().remove() }
        map.keys.remove("absent")
        map.values.remove(2)

        assertEquals(mapOf("kept" to 1), map.toMap())
        assertEquals(0, saves)
    }

    @Test
    fun `atomic config writes replace the original only after writing completes`(@TempDir directory: File) {
        val file = File(directory, "config.json").apply { writeText("original") }

        writeConfigAtomically(file) { writer ->
            writer.write("replacement")
            assertEquals("original", file.readText())
        }

        assertEquals("replacement", file.readText())
        assertEquals(listOf("config.json"), directory.list()?.toList())
    }

    @Test
    fun `failed config writes preserve the original and remove temporary files`(@TempDir directory: File) {
        val file = File(directory, "config.json").apply { writeText("original") }
        val expected = IOException("write failed")

        val actual = assertFailsWith<IOException> {
            writeConfigAtomically(file) { writer ->
                writer.write("partial replacement")
                throw expected
            }
        }

        assertSame(expected, actual)
        assertEquals("original", file.readText())
        assertEquals(listOf("config.json"), directory.list()?.toList())
    }

    @Test
    fun `reads do not persist and explicit save and reload call their respective callbacks`() {
        var listSaves = 0
        var listReloads = 0
        var mapSaves = 0
        var mapReloads = 0
        val list = ConfigList(mutableListOf("old"), { listSaves++ }, { listReloads++ })
        val map = ConfigMap(mutableMapOf("key" to "old"), { mapSaves++ }, { mapReloads++ })

        assertEquals("old", list[0])
        assertEquals(1, list.size)
        assertEquals("old", map["key"])
        assertEquals(setOf("key"), map.keys)
        assertEquals(listOf(0, 0, 0, 0), listOf(listSaves, listReloads, mapSaves, mapReloads))

        list.save()
        list.reload()
        map.save()
        map.reload()

        assertEquals(listOf(1, 1, 1, 1), listOf(listSaves, listReloads, mapSaves, mapReloads))
    }
}
