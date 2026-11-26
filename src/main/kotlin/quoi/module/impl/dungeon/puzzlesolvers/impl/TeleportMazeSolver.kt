package quoi.module.impl.dungeon.puzzlesolvers.impl

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction.Axis
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.util.Mth
import net.minecraft.world.entity.PositionMoveRotation
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.events.*
import quoi.api.events.core.Event
import quoi.api.events.core.on
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonRoom
import quoi.api.skyblock.dungeon.odonscanning.tiles.RoomState
import quoi.module.impl.dungeon.autoclear.executor.ClearExecutor
import quoi.module.impl.dungeon.puzzlesolvers.PuzzleSolvers
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.group.SettingGroup
import quoi.utils.*
import quoi.utils.WorldUtils.collisionShape
import quoi.utils.render.drawFilledBox
import quoi.utils.render.drawTracer
import quoi.utils.skyblock.player.MovementUtils.cancelMovementTask
import quoi.utils.skyblock.player.MovementUtils.moveTo
import quoi.utils.skyblock.player.RotationUtils.rotate
import kotlin.math.abs
import kotlin.math.atan2

// based on https://github.com/Noamm9/NoammAddons/blob/bf040bdcce6346390ad2989e61b261b58e03402d/src/main/kotlin/com/github/noamm9/features/impl/dungeon/solvers/puzzles/TeleportMazeSolver.kt
object TeleportMazeSolver : SettingGroup(PuzzleSolvers, "Teleport maze") {
    private val solver by switch("Solver", desc = "Shows the solution for the TP maze puzzle.")
    private val colourOne by colourPicker(
        "Colour for one",
        Colour.MINECRAFT_GREEN.withAlpha(0.5f),
        true,
        desc = "Colour for when there is a single solution."
    ).childOf(::solver)
    private val colourMultiple by colourPicker(
        "Colour for multiple",
        Colour.MINECRAFT_GOLD.withAlpha(0.5f),
        true,
        desc = "Colour for when there are multiple solutions."
    ).childOf(::solver)
    private val colourVisited by colourPicker(
        "Colour for visited",
        Colour.MINECRAFT_RED.withAlpha(0.5f),
        true,
        desc = "Colour for the already used TP pads."
    ).childOf(::solver)
    private val tracer by switch("Tracer", true, desc = "Shows a tracer to the best next TP pad.").childOf(::solver)
    private val tracerColour by colourPicker(
        "Tracer colour",
        Colour.MINECRAFT_AQUA,
        true,
        desc = "Colour for the TP maze tracer."
    ).childOf(::tracer)
    private val auto by switch("Auto").asParent()

    private val start = BlockPos(15, 69, 12)
    private val exit = BlockPos(15, 69, 14)
    private val waypoint = BlockPos(15, 69, 16)
    private val chest = BlockPos(15, 70, 20)
    private val doorway = BlockPos(15, 69, -2)
    private val neutral = Colour.WHITE.withAlpha(0.5f)

    private data class Walk(val target: BlockPos, val usesMoveTo: Boolean)

    private var maze: TeleportMaze? = null
    private var exitTarget: BlockPos? = null
    private var walk: Walk? = null

    override fun shouldHandle(event: Event): Boolean = when (event) {
        is WorldEvent.Change, is DungeonEvent.Room.Enter -> true
        is TickEvent.End -> walk != null || (super.shouldHandle(event) && Dungeon.currentRoom?.name == "Teleport Maze")
        else -> super.shouldHandle(event) && maze != null
    }

    init {
        on<DungeonEvent.Room.Enter> { reset() }
        on<WorldEvent.Change> { reset() }

        on<PacketEvent.Received, ClientboundPlayerPositionPacket> {
            val room = Dungeon.currentRoom ?: return@on
            val maze = maze ?: return@on
            val source = player.position()
            val change =
                PositionMoveRotation.calculateAbsolute(PositionMoveRotation.of(player), packet.change, packet.relatives)
            val destination = change.position
            val returning = inCenterLane(room, destination, -1..13) &&
                (exitTarget == exit || inCenterLane(room, source, 14..20))
            val mazeTeleport = destination.y == 69.5 && destination.x % 0.5 == 0.0 && destination.z % 0.5 == 0.0

            when {
                returning -> {
                    if (exitTarget != exit && room.data.state != RoomState.GREEN) return@on reset()
                    exitTarget = doorway
                }

                exitTarget == null && inCenterLane(room, destination, 14..20) -> exitTarget = waypoint
                mazeTeleport -> if (!maze.recordTeleport(source, destination, change.yRot)) return@on
                else -> return@on
            }
            stopWalking()
        }

        on<DungeonEvent.Secret.Interact> {
            if (exitTarget != waypoint) return@on
            val room = Dungeon.currentRoom ?: return@on
            if (blockPos == room.getRealCoords(chest)) exitTarget = exit
        }

        on<RenderEvent.World> {
            if (!solver) return@on
            val maze = maze ?: return@on
            val leaders = if (exitTarget == null) maze.leadingPads else emptySet()
            val leaderColour = if (leaders.size == 1) colourOne else colourMultiple
            for (pad in maze.pads) {
                val isLeader = pad in leaders
                val colour = when {
                    isLeader -> leaderColour
                    maze.wasVisited(pad) -> colourVisited
                    else -> neutral
                }
                ctx.drawFilledBox(pad.bounds?.move(pad) ?: pad.aabb, colour, depth = !isLeader)
            }

            if (tracer && exitTarget == null) {
                val target = maze.nextPad(player.position()) ?: return@on
                ctx.drawTracer(target.center.add(0.0, 0.3, 0.0), tracerColour, depth = false)
            }
        }

        on<TickEvent.End> {
            val room = Dungeon.currentRoom
            if (room?.name != "Teleport Maze" || !inEnvironment() || Dungeon.isDead) return@on reset()

            if (maze == null && (solver || auto)) maze = scan(room)
            val maze = maze ?: return@on
            if (!auto || ClearExecutor.active || PuzzleSolvers.screenBlocksAuto) return@on stopWalking()

            val position = player.position()
            val inFinalCell = inCenterLane(room, position, 14..20)
            if (exitTarget == null && inFinalCell) exitTarget = waypoint
            if (exitTarget == waypoint && room.data.state == RoomState.GREEN) exitTarget = exit
            when (exitTarget) {
                waypoint -> {
                    val target = room.getRealCoords(waypoint)
                    if (inFinalCell && position.distanceTo2DSqr(target.center) > 0.25) walkTo(target)
                    else stopWalking()
                }

                exit -> walkTo(maze.exitPad, room.getRealCoords(waypoint))
                doorway -> walkForwardTo(room.getRealCoords(doorway))
                else -> {
                    if (room.data.state == RoomState.GREEN) return@on stopWalking()
                    val target = walk?.takeUnless { it.usesMoveTo }?.target ?: maze.nextPad(position)
                    ?: maze.startPad.takeIf {
                        room.getRelativeCoords(player.blockPosition()).z < start.z && !maze.wasVisited(
                            it
                        )
                    }
                    when (target) {
                        maze.startPad -> walkTo(maze.startPad, room.getRealCoords(doorway))
                        null -> Unit
                        else -> walkForwardTo(target)
                    }
                }
            }
        }
    }

    private fun scan(room: OdonRoom): TeleportMaze? {
        val center = room.tiles.singleOrNull()?.blockPos?.atY(69) ?: return null
        val min = center.offset(-15, 0, -15)
        val max = center.offset(15, 0, 15)
        val startPad = room.getRealCoords(start)
        val exitPad = room.getRealCoords(exit)
        for (x in (min.x shr 4)..(max.x shr 4)) for (z in (min.z shr 4)..(max.z shr 4)) {
            if (!level.chunkSource.hasChunk(x, z)) return null
        }
        if (!level.getBlockState(startPad).`is`(Blocks.END_PORTAL_FRAME)) return null

        val pads = BlockPos.betweenClosed(min, max)
            .asSequence()
            .filter { level.getBlockState(it).`is`(Blocks.END_PORTAL_FRAME) }
            .map { it.immutable() }
            .toList()
        return if (exitPad in pads) TeleportMaze(pads, startPad, exitPad) else null
    }

    private fun inCenterLane(room: OdonRoom, position: Vec3, zRange: IntRange): Boolean {
        val relative = room.getRelativeCoords(BlockPos.containing(position))
        return relative.x in 12..18 && relative.y in 68..70 && relative.z in zRange
    }

    private fun walkForwardTo(target: BlockPos) {
        if (walk?.target != target) {
            stopWalking()
            player.rotate(getDirection(player.eyePosition, target.center))
            walk = Walk(target, usesMoveTo = false)
        }
        mc.options.keyUp.isDown = true
    }

    private fun walkTo(target: BlockPos, approachFrom: BlockPos? = null) {
        val center = target.center
        val destination = if (approachFrom == null) {
            center
        } else {
            val shape = target.collisionShape
            if (shape.isEmpty) return
            val padTop = Vec3(center.x, target.y + shape.max(Axis.Y), center.z)
            val position = player.position()
            if (player.onGround() && abs(position.y - padTop.y) < 0.01 && position.distanceTo2DSqr(padTop) <= 0.0625) {
                stopWalking()
                return
            }

            padTop.add(center.subtract(approachFrom.center).normalize().scale(0.375))
        }

        if (walk?.target == target) return
        stopWalking()
        walk = Walk(target, usesMoveTo = true)
        player.moveTo(destination) { walk = null }
    }

    private fun stopWalking() {
        val current = walk ?: return
        if (current.usesMoveTo) cancelMovementTask() else mc.options.keyUp.isDown = false
        walk = null
    }

    internal fun reset() {
        stopWalking()
        maze = null
        exitTarget = null
    }
}

internal class TeleportMaze(
    val pads: List<BlockPos>,
    val startPad: BlockPos,
    val exitPad: BlockPos,
) {
    private val minX = pads.minOf { it.x }
    private val minZ = pads.minOf { it.z }
    private val cells = pads.groupBy(::cellAt)
    private val visited = mutableSetOf<BlockPos>()
    private val scores = pads.filter { it != startPad && it != exitPad }.associateWith { 0.0 }.toMutableMap()

    var leadingPads: Set<BlockPos> = emptySet()
        private set

    fun wasVisited(pad: BlockPos): Boolean = pad in visited

    fun recordTeleport(from: Vec3, to: Vec3, yaw: Float): Boolean {
        val source = padNear(from) ?: return false
        val destination = padNear(to) ?: return false
        if (destination == source || destination == startPad || destination == exitPad) return false

        visited += source
        visited += destination
        scores.remove(source)
        scores.remove(destination)
        for ((pad, score) in scores) {
            val dx = pad.x + 0.5 - to.x
            val dz = pad.z + 0.5 - to.z
            val targetYaw = Math.toDegrees(atan2(dz, dx)) - 90.0
            scores[pad] = score + abs(Mth.wrapDegrees(targetYaw - yaw))
        }
        val best = scores.values.minOrNull()
        leadingPads = scores.filterValues { it == best }.keys
        return true
    }

    fun nextPad(position: Vec3): BlockPos? =
        padsInCell(position)
            .filter { it != exitPad && it !in visited }
            .minWithOrNull(compareBy<BlockPos> { scores[it] ?: 0.0 }.thenBy { position.distanceToSqr(it.center) })

    private fun padNear(position: Vec3): BlockPos? =
        padsInCell(position).find { abs(position.x - it.x - 0.5) + abs(position.z - it.z - 0.5) <= 3.0 }

    private fun padsInCell(position: Vec3): List<BlockPos> = cells[cellAt(BlockPos.containing(position))].orEmpty()

    private fun cellAt(pos: BlockPos): Int? {
        val x = pos.x - minX
        val z = pos.z - minZ
        return if (x in 0..23 && z in 0..23) (x / 8) * 3 + z / 8 else null
    }
}
