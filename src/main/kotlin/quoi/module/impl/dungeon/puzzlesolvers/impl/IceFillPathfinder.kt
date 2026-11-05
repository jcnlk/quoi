package quoi.module.impl.dungeon.puzzlesolvers.impl

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.phys.Vec3

/**
 * Modified NoammAddons (CC0-1.0).
 * Original: https://github.com/Noamm9/NoammAddons/blob/3cde736b2f1c6cd1459c4b270f7475dd68bb7d65/src/main/kotlin/com/github/noamm9/features/impl/dungeon/solvers/puzzles/IceFillSolver.kt
 */
internal object IceFillPathfinder {
    private val entrances = listOf(BlockPos(15, 70, 7), BlockPos(15, 71, 12), BlockPos(15, 72, 19))
    private val exits = listOf(BlockPos(15, 70, 10), BlockPos(15, 71, 17), BlockPos(15, 72, 26))
    private val directions = Direction.Plane.HORIZONTAL.toList()

    fun scan(isWalkableIce: (BlockPos) -> Boolean?): Set<BlockPos>? {
        val spaces = HashSet<BlockPos>()
        for (x in 0..30) for (z in 0..30) for (y in 70..72) {
            val pos = BlockPos(x, y, z)
            if (isWalkableIce(pos) ?: return null) spaces.add(pos)
        }
        return spaces
    }

    suspend fun solve(spaces: Set<BlockPos>): List<Vec3>? {
        val path = ArrayList<Vec3>()
        for ((index, start) in entrances.withIndex()) {
            if (start !in spaces) return null
            val floor = mutableSetOf(start)
            val queue = ArrayDeque<BlockPos>().apply { add(start) }
            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                for (dir in directions) {
                    val next = current.relative(dir)
                    if (next in spaces && floor.add(next)) queue.add(next)
                }
            }

            val exit = exits[index]
            val end = floor.minBy { it.distSqr(exit) }
            val solution = Puzzle(floor, start, end).solve() ?: return null
            val first = Vec3(start).add(0.5, 0.1, 0.5)
            if (path.isNotEmpty()) {
                val previous = path.last()
                path.add(Vec3((previous.x + first.x) / 2, first.y, (previous.z + first.z) / 2))
            }
            path.addAll(solution.map { Vec3(it).add(0.5, 0.1, 0.5) })
            if (end != exit) path.add(Vec3(exit).add(0.5, 0.1, 0.5))
        }
        return path
    }

    private class Puzzle(private val spaces: Set<BlockPos>, private val start: BlockPos, private val end: BlockPos) {
        private val graph = spaces.associateWith { pos -> directions.filter { pos.relative(it) in spaces } }

        private fun remainingConnected(current: BlockPos, visited: Set<BlockPos>): Boolean {
            val remaining = spaces.size - visited.size + 1
            val seen = hashSetOf(current)
            val stack = ArrayDeque<BlockPos>().apply { add(current) }
            while (stack.isNotEmpty()) {
                val pos = stack.removeLast()
                for (dir in graph.getValue(pos)) {
                    val next = pos.relative(dir)
                    if ((next == current || next !in visited) && seen.add(next)) stack.add(next)
                }
            }
            return seen.size == remaining
        }

        suspend fun solve(): List<BlockPos>? {
            val context = currentCoroutineContext()

            fun search(stopAtFirst: Boolean, costBound: Int): Pair<List<BlockPos>, Int>? {
                var bestCost = costBound
                var bestPath: List<BlockPos>? = null
                val visited = mutableSetOf(start)
                val path = arrayListOf(start)

                fun dfs(current: BlockPos, lastDir: Direction?, lastWasTurn: Boolean, cost: Int): Boolean {
                    context.ensureActive()
                    if (cost >= bestCost) return false
                    if (visited.size == spaces.size) {
                        if (current != end) return false
                        bestCost = cost
                        bestPath = path.toList()
                        return stopAtFirst
                    }
                    if (current == end) return false

                    val candidates = graph.getValue(current)
                        .map { it to current.relative(it) }
                        .filter { (_, pos) -> pos !in visited }
                        .sortedWith(compareBy(
                            { (_, pos) -> graph.getValue(pos).count { pos.relative(it) !in visited } },
                            { (dir, _) -> if (dir == lastDir) 0 else 1 }
                        ))

                    for ((dir, next) in candidates) {
                        val turned = lastDir != null && dir != lastDir
                        val stepCost = cost + if (turned) {
                            1 + (if (current.relative(lastDir) in spaces) 5 else 0) + (if (lastWasTurn) 2 else 0)
                        } else 0
                        if (stepCost >= bestCost) continue

                        visited.add(next)
                        path.add(next)
                        val stop = remainingConnected(next, visited) && dfs(next, dir, turned, stepCost)
                        path.removeAt(path.lastIndex)
                        visited.remove(next)
                        if (stop) return true
                    }
                    return false
                }

                dfs(start, null, false, 0)
                return bestPath?.let { it to bestCost }
            }

            val fallback = search(stopAtFirst = true, costBound = Int.MAX_VALUE) ?: return null
            return search(stopAtFirst = false, costBound = fallback.second)?.first ?: fallback.first
        }
    }
}
