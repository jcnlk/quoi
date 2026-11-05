package quoi.module.impl.dungeon.puzzlesolvers.impl

import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Test
import quoi.utils.blockPos
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IceFillSolverTest {
    private data class Fixtures(val easy: List<List<List<BlockPos>>>)

    private val fixtures = javaClass.getResourceAsStream("/assets/quoi/puzzles/iceFillFloors.json")!!.bufferedReader().use {
        Gson().fromJson(it, Fixtures::class.java).easy
    }

    @Test
    fun `every known floor combination visits each ice block once and connects the stairs`() = runBlocking {
        for (first in fixtures[0]) for (second in fixtures[1]) for (third in fixtures[2]) {
            val floors = listOf(first, second, third)
            val spaces = iceBlocks(floors)
            val path = assertNotNull(IceFillPathfinder.solve(spaces))

            assertValidPath(spaces, floors.map { it.last() }, path)
        }
    }

    @Test
    fun `solver handles layouts without a stored pattern`() = runBlocking {
        val floors = straightFloors()
        val path = assertNotNull(IceFillPathfinder.solve(iceBlocks(floors)))

        assertValidPath(iceBlocks(floors), floors.map { it.last() }, path)
    }

    @Test
    fun `exit ice is included once when it belongs to the floor`() = runBlocking {
        val floors = fixtures.map { it.first() }
        val exits = floors.map { it.last() }
        val spaces = iceBlocks(floors) + exits
        val path = assertNotNull(IceFillPathfinder.solve(spaces))

        assertValidPath(spaces, exits, path)
    }

    @Test
    fun `incomplete chunk snapshot is discarded and rescanned when loaded`() {
        val spaces = iceBlocks(fixtures.map { it.first() })
        var loaded = false
        val isWalkableIce: (BlockPos) -> Boolean? = {
            if (!loaded && it.x >= 16) null else it in spaces
        }

        assertNull(IceFillPathfinder.scan(isWalkableIce))
        loaded = true
        assertEquals(spaces, IceFillPathfinder.scan(isWalkableIce))
    }

    @Test
    fun `loaded empty room produces no solution`() = runBlocking {
        val spaces = assertNotNull(IceFillPathfinder.scan { false })

        assertTrue(spaces.isEmpty())
        assertNull(IceFillPathfinder.solve(spaces))
    }

    @Test
    fun `missing final floor does not publish the first two floors`() = runBlocking {
        val spaces = iceBlocks(fixtures.map { it.first() })

        assertNull(IceFillPathfinder.solve(spaces.filterTo(mutableSetOf()) { it.y != 72 }))
    }

    @Test
    fun `unsolvable final floor does not publish a partial solution`() = runBlocking {
        val spaces = iceBlocks(straightFloors()).toMutableSet()
        spaces.add(BlockPos(14, 72, 20))
        spaces.add(BlockPos(16, 72, 20))

        assertNull(IceFillPathfinder.solve(spaces))
    }

    @Test
    fun `unconnected decorative ice does not become part of the puzzle`() = runBlocking {
        val floors = fixtures.map { it.first() }
        val spaces = iceBlocks(floors)
        val decoration = setOf(BlockPos(2, 70, 2), BlockPos(3, 70, 2))
        val path = assertNotNull(IceFillPathfinder.solve(spaces + decoration))

        assertValidPath(spaces, floors.map { it.last() }, path)
    }

    @Test
    fun `search cooperates with cancellation on a room change`() = runBlocking {
        val spaces = iceBlocks(fixtures.map { it.first() })
        var checked = false
        val job = launch {
            currentCoroutineContext().cancel()
            assertFailsWith<CancellationException> { IceFillPathfinder.solve(spaces) }
            checked = true
        }

        job.join()
        assertTrue(checked)
    }

    private fun assertValidPath(spaces: Set<BlockPos>, exits: List<BlockPos>, path: List<Vec3>) {
        val expected = (spaces + exits).mapTo(mutableSetOf()) { Vec3(it).add(0.5, 0.1, 0.5) }
        expected.add(Vec3(15.5, 71.1, 11.5))
        expected.add(Vec3(15.5, 72.1, 18.5))
        assertEquals(expected, path.toSet())
        assertEquals(expected.size, path.size)
        assertEquals(Vec3(15.5, 70.1, 7.5), path.first())
        assertEquals(Vec3(15.5, 72.1, 26.5), path.last())
        for ((from, to) in path.zipWithNext()) {
            assertEquals(1.0, abs(to.x - from.x) + abs(to.z - from.z))
            assertTrue(to.y - from.y in 0.0..1.0)
        }
        for (y in 70..72) {
            assertEquals(spaces.count { it.y == y }, path.count { it.blockPos.y == y && it.blockPos in spaces })
        }
    }

    private fun straightFloors() = listOf(
        (7..10).map { BlockPos(15, 70, it) },
        (12..17).map { BlockPos(15, 71, it) },
        (19..26).map { BlockPos(15, 72, it) },
    )

    private fun iceBlocks(floors: List<List<BlockPos>>): Set<BlockPos> = floors.flatMap { floor ->
        floor.zipWithNext().flatMap { (from, to) ->
            BlockPos.betweenClosed(from, to).map { it.immutable() }
        }.filter { it != floor.last() }
    }.toSet()
}
