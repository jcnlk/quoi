package quoi.module.impl.dungeon.puzzlesolvers.impl

import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.decoration.ItemFrame
import net.minecraft.world.item.MapItem
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.events.DungeonEvent
import quoi.api.events.RenderEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.Event
import quoi.api.events.core.on
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonRoom
import quoi.module.impl.dungeon.autoclear.executor.ClearExecutor
import quoi.module.impl.dungeon.puzzlesolvers.PuzzleSolvers
import quoi.module.impl.dungeon.puzzlesolvers.Repositionable
import quoi.module.impl.dungeon.secrets.impl.SecretAura
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.group.SettingGroup
import quoi.utils.*
import quoi.utils.render.drawWireFrameBox
import quoi.utils.skyblock.player.PlayerUtils.at
import quoi.utils.skyblock.player.interact.AuraManager

/**
 * modified Skyblocker (LGPL-3.0) (c) kevinthegreat1
 * original:
 *          https://github.com/SkyblockerMod/Skyblocker/blob/master/src/main/java/de/hysky/skyblocker/skyblock/dungeon/puzzle/TicTacToe.java
 *          https://github.com/SkyblockerMod/Skyblocker/blob/master/src/main/java/de/hysky/skyblocker/utils/tictactoe/TicTacToeUtils.java
 */
object TicTacToeSolver : SettingGroup(PuzzleSolvers, "Tic tac toe"), Repositionable {
    private val solver by switch("Solver", desc = "Shows the solution for the Tic tac toe puzzle.")
    private val colour by colourPicker("Colour", Colour.MINECRAFT_GREEN.withAlpha(0.7f), true, desc = "Colour for the tic tac toe solver").childOf(::solver)
    private val prediction by switch("Prediction", desc = "try and see").childOf(::solver)
    private val pColour by colourPicker("Prediction colour", Colour.MINECRAFT_YELLOW.withAlpha(0.7f), true).childOf(::prediction)
    private val auto by switch("Auto").asParent()
    private val autoReposition by switch("Auto reposition", desc = "Moves to the chest after the last player move, then moves on once Secret Aura opens it.").childOf(::auto)
    private val triggerbot by switch("Triggerbot", desc = "Clicks the best move when you look at its button.").asParent()

    private var lastBoardHash = 0
    private var bestMove: BlockPos? = null
    private var predictedMove: BlockPos? = null

    private var lastClick = 0L
    private var shouldReposition = false
    private var repositionStep = 0
    override var repositionTicker: Ticker? = null

    init {
        on<DungeonEvent.Room.Enter> {
            if (room?.name != "Tic Tac Toe") reset()
        }

        on<RenderEvent.World> {
            if (!solver) return@on
            bestMove?.let {
                ctx.drawWireFrameBox(it.aabb, colour, depth = true)
            }
            if (prediction) predictedMove?.let {
                ctx.drawWireFrameBox(it.aabb, pColour, depth = true)
            }
        }

        on<RenderEvent.World> {
            if (!auto || !autoReposition) return@on
            val room = Dungeon.currentRoom ?: return@on
            val spots = repositionSpots(room) ?: return@on
            spots.forEachIndexed { index, spot ->
                val colour = if (index == 0) Colour.CYAN else Colour.MINECRAFT_YELLOW
                ctx.drawWireFrameBox(room.getRealCoords(spot).aabb, colour, depth = true)
            }
        }

        on<TickEvent.End> {
            if (!solver && !auto && !triggerbot) return@on
            if (ClearExecutor.active) return@on
            val room = Dungeon.currentRoom ?: return@on

            if (auto && autoReposition && shouldReposition) {
                val spots = repositionSpots(room) ?: return@on
                repositionTicker?.let { if (it.tick()) repositionTicker = null }
                if (repositionTicker != null) return@on

                when (repositionStep) {
                    0 -> {
                        val spot = room.getRealCoords(spots[0])
                        if (player.at(spot)) repositionStep = 2
                        else {
                            reposition(spot, bow = false)
                            if (repositionTicker != null) repositionStep = 1
                        }
                    }

                    1 -> repositionStep = if (player.at(room.getRealCoords(spots[0]))) 2 else 0

                    2 -> if (chestOpened(room)) {
                        val spot = room.getRealCoords(spots[1])
                        if (player.at(spot)) repositionStep = 4
                        else {
                            reposition(spot, bow = false)
                            if (repositionTicker != null) repositionStep = 3
                        }
                    }

                    3 -> repositionStep = if (player.at(room.getRealCoords(spots[1]))) 4 else 2

                    4 -> if (spots.size > 2) {
                        val spot = room.getRealCoords(spots[2])
                        if (player.at(spot)) repositionStep = 6
                        else {
                            reposition(spot, bow = false)
                            if (repositionTicker != null) repositionStep = 5
                        }
                    }

                    5 -> repositionStep = if (player.at(room.getRealCoords(spots[2]))) 6 else 4
                }
                return@on
            }

            val searchBox = AABB.ofSize(Vec3.atCenterOf(room.getRealCoords(BlockPos(8, 71, 16))), 12.0, 12.0, 12.0)
            val frames = EntityUtils.getEntities<ItemFrame>(searchBox) { it.item.item is MapItem && it.item.has(
                DataComponents.MAP_ID) }

            val board = CharArray(9) { EMPTY }
            var validFrames = 0

            for (frame in frames) {
                val (_, y, z) = room.getRelativeCoords(frame.blockPosition())

                val row = 72 - y
                val col = 17 - z

                if (row !in 0..2 || col !in 0..2) continue

                val mapId = frame.item.get(DataComponents.MAP_ID) ?: continue
                val mapData = level.getMapData(mapId) ?: continue

                val colour = mapData.colors.getOrNull(8256)?.toInt()?.and(0xFF) ?: continue
                if (colour == 114 || colour == 33) {
                    board[row * 3 + col] = if (colour == 114) 'X' else 'O'
                    validFrames++
                }
            }

            if (getScore(board) != 0 || validFrames >= 8) {
                bestMove = null
                predictedMove = null
                if (auto && autoReposition && validFrames > 0 && repositionSpots(room) != null) shouldReposition = true
                return@on
            }

            val boardHash = board.contentHashCode()
            if (boardHash != lastBoardHash) {
                lastBoardHash = boardHash

                if (validFrames % 2 != 0) {
                    predictedMove = null
                    bestMove = getBestMove(board, true)?.let { indexToPos(it, room) }
                } else if (prediction) {
                    bestMove = null
                    getBestMove(board, false)?.let { i ->
                        board[i] = 'X'
                        predictedMove = if (getScore(board) == 0) getBestMove(board, true)?.let { indexToPos(it, room) } else null
                        board[i] = EMPTY
                    } ?: run { predictedMove = null }
                } else {
                    bestMove = null
                    predictedMove = null
                }
            }

            bestMove?.let {
                if (PuzzleSolvers.screenBlocksAuto || System.currentTimeMillis() - lastClick < 500L) return@let
                if (auto) {
                    if (player.eyePosition.distanceToSqr(it.vec3) > 30) return@let
                    AuraManager.interactBlock(it)
                    lastClick = System.currentTimeMillis()
                } else if (triggerbot && PuzzleSolvers.triggerBlock(it)) lastClick = System.currentTimeMillis()
            }
        }

        on<WorldEvent.Change> {
            reset()
        }
    }

    override fun shouldHandle(event: Event): Boolean {
        if (!super.shouldHandle(event)) return false

        if (event is DungeonEvent.Room.Enter || event is WorldEvent.Change) return true

        return Dungeon.currentRoom?.name == "Tic Tac Toe"
    }

    private fun indexToPos(i: Int, room: OdonRoom) = room.getRealCoords(BlockPos(8, 72 - (i / 3), 17 - (i % 3)))

    private fun getBestMove(board: CharArray, isPlayer: Boolean): Int? {
        var bestScore = if (isPlayer) -1000 else 1000
        var bestIndex: Int? = null

        for (i in MOVE_ORDER) {
            if (board[i] == EMPTY) {
                board[i] = if (isPlayer) 'O' else 'X'
                val score = alphaBeta(board, 0, -1000, 1000, !isPlayer)
                board[i] = EMPTY

                if (isPlayer && score > bestScore) {
                    bestScore = score
                    bestIndex = i
                } else if (!isPlayer && score < bestScore) {
                    bestScore = score
                    bestIndex = i
                }
            }
        }
        return bestIndex
    }

    private fun alphaBeta(board: CharArray, depth: Int, alpha: Int, beta: Int, maximising: Boolean): Int {
        val score = getScore(board)
        if (score != 0) return score + if (score > 0) -depth else depth
        if (EMPTY !in board) return 0

        var a = alpha
        var b = beta
        var best = if (maximising) -1000 else 1000

        for (i in MOVE_ORDER) {
            if (board[i] == EMPTY) {
                board[i] = if (maximising) 'O' else 'X'
                val s = alphaBeta(board, depth + 1, a, b, !maximising)
                board[i] = EMPTY

                if (maximising) {
                    best = maxOf(best, s)
                    a = maxOf(a, best)
                } else {
                    best = minOf(best, s)
                    b = minOf(b, best)
                }

                if (b <= a) break
            }
        }
        return best
    }

    private fun getScore(board: CharArray): Int {
        for (i in WIN_SETS.indices step 3) {
            val c = board[WIN_SETS[i]]
            if (c != EMPTY && c == board[WIN_SETS[i + 1]] && c == board[WIN_SETS[i + 2]])
                return if (c == 'X') -10 else 10
        }
        return 0
    }

    private fun reset() {
        lastBoardHash = 0
        bestMove = null
        predictedMove = null
        lastClick = 0L
        repositionTicker = null
        shouldReposition = false
        repositionStep = 0
    }

    private val MOVE_ORDER = intArrayOf(4, 0, 2, 6, 8, 1, 3, 5, 7) // centre -> cornesr -> edges
    private val WIN_SETS = intArrayOf(
        0, 1, 2,  3, 4, 5,  6, 7, 8, // horiz
        0, 3, 6,  1, 4, 7,  2, 5, 8, // vert
        0, 4, 8,  2, 4, 6 // diag
    )
    private const val EMPTY = '\u0000'

    private val REPOSITION_SPOTS_NEW = listOf(BlockPos(16, 68, 25), BlockPos(8, 68, 16))
    private val REPOSITION_SPOTS_OLD = listOf(BlockPos(12, 69, 21), BlockPos(16, 68, 18), BlockPos(9, 68, 16))

    private fun repositionSpots(room: OdonRoom) = when (room.tiles.firstOrNull()?.core) {
        29612250 -> REPOSITION_SPOTS_NEW
        -738945309 -> REPOSITION_SPOTS_OLD
        else -> null
    }

    private fun chestOpened(room: OdonRoom) = SecretAura.blocksDone.any { packedPos ->
        val pos = BlockPos.of(packedPos)
        val relative = room.getRelativeCoords(pos)
        relative.x in 0..31 && relative.z in 0..31 && level.getBlockState(pos).block.let { it == Blocks.CHEST || it == Blocks.TRAPPED_CHEST }
    }
}