package quoi.api.skyblock.dungeon.odonscanning

import org.junit.jupiter.api.Test
import quoi.api.vec.Vec2i
import kotlin.math.ceil
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MapTextLayoutTest {
    @Test
    fun `straight rooms use their full footprint in either direction`() {
        for (length in 1..4) {
            val horizontal = (0 until length).map { Vec2i(40 + it * 20, 60) }
            val vertical = (0 until length).map { Vec2i(40, 60 + it * 20) }
            assertEquals(Vec2i(length * 20 - 4, 16), MapTextLayout.roomBounds(horizontal))
            assertEquals(Vec2i(16, length * 20 - 4), MapTextLayout.roomBounds(vertical))
        }
    }

    @Test
    fun `L rooms keep text in their complete row for every rotation`() {
        val square = listOf(Vec2i(20, 40), Vec2i(40, 40), Vec2i(20, 60), Vec2i(40, 60))
        for (missing in square) {
            assertEquals(Vec2i(32, 16), MapTextLayout.roomBounds(square - missing))
        }
        assertEquals(Vec2i(32, 32), MapTextLayout.roomBounds(square))
    }

    @Test
    fun `long room names and secrets stay inside their room at every scale`() {
        val labels = listOf(
            listOf("Withermancer"), listOf("Scaffolding"), listOf("Quartz", "Knight"),
            listOf("Three", "Weirdos"), listOf("Water", "Board", "10/10"),
            listOf("Layers"), listOf("High", "Blaze"), listOf("Default"),
            listOf("Blue", "Skulls"), listOf("Deathmite", "Pit"), listOf("Teleport", "Maze"),
        )
        val footprints = listOf(Vec2i(16, 16), Vec2i(16, 56), Vec2i(76, 16), Vec2i(32, 16), Vec2i(32, 32))
        for (lines in labels) for (bounds in footprints) for (scale in listOf(0.25f, 1f, 3f, 5f)) {
            val size = MapTextLayout.fitSize(lines, 27f * scale, bounds.x * scale, bounds.z * scale, ::measure)
            assertTrue(lines.all { measure(it, size) <= bounds.x * scale })
            assertTrue(lines.size * size <= bounds.z * scale)
            val unscaled = MapTextLayout.fitSize(lines, 27f, bounds.x.toFloat(), bounds.z.toFloat(), ::measure)
            assertEquals(unscaled * scale, size, 0.001f)
        }
    }

    @Test
    fun `larger rooms allow larger text without exceeding the selected font size`() {
        val lines = listOf("Museum")
        val small = MapTextLayout.fitSize(lines, 9f, 16f, 16f, ::measure)
        val large = MapTextLayout.fitSize(lines, 9f, 76f, 16f, ::measure)
        assertTrue(small < large)
        assertEquals(9f, large)
    }

    @Test
    fun `stats shrink to the map width and grow back when their value becomes shorter`() {
        val long = listOf("Secrets: 123/456/789    C12 | B12")
        val short = listOf("Score: 300")
        val shrunk = MapTextLayout.fitSize(long, 18.9f, 348f, 18.9f, ::measure)
        assertTrue(shrunk < 18.9f)
        assertTrue(measure(long.single(), shrunk) <= 348f)
        assertEquals(18.9f, MapTextLayout.fitSize(short, 18.9f, 348f, 18.9f, ::measure))
    }

    @Test
    fun `quantized custom font metrics are measured at the final size`() {
        val size = MapTextLayout.fitSize(listOf("Scaffolding"), 9f, 16f, 16f) { line, height ->
            ceil(measure(line, height))
        }
        assertTrue(ceil(measure("Scaffolding", size)) <= 16f)
        assertTrue(ceil(measure("Scaffolding", size + 0.01f)) > 16f)
    }

    private fun measure(line: String, size: Float): Float = line.length * 6f * size / 9f
}
