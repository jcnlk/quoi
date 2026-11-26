package quoi.config

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.File
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ConfigSystemTest {
    private data class Entry(val label: String, val count: Int)

    @Test
    fun `loading a missing config creates it with typed defaults`(@TempDir directory: File) {
        val file = File(directory, "nested/config.json")
        val defaults = listOf(Entry("arrows", 64))

        assertEquals(defaults, ConfigSystem.load(file) { defaults })
        assertTrue(file.isFile)
        assertEquals(defaults, ConfigSystem.load<List<Entry>>(file) { error("Expected saved defaults") })
    }

    @ParameterizedTest
    @ValueSource(strings = ["{", "", "null"])
    fun `unreadable config preserves the original and clears its failure after a valid reload`(
        contents: String,
        @TempDir directory: File,
    ) {
        val file = File(directory, "config.json").apply { writeText(contents) }
        val key = file.absoluteFile.normalize()
        val defaults = listOf(Entry("fallback", 1))

        try {
            assertEquals(defaults, ConfigSystem.load(file) { defaults })
            assertEquals(contents, file.readText())
            assertTrue(key in ConfigSystem.failedLoads)

            file.writeText("""[{"label":"recovered","count":2}]""")
            assertEquals(listOf(Entry("recovered", 2)), ConfigSystem.load(file) { defaults })
            assertFalse(key in ConfigSystem.failedLoads)

            val updated = listOf(Entry("updated", 3))
            ConfigSystem.save(file, updated).getOrThrow()
            assertEquals(updated, ConfigSystem.load(file) { defaults })
        } finally {
            ConfigSystem.failedLoads.remove(key)
        }
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
}
