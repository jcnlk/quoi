package quoi.module.impl.dungeon.puzzlesolvers

import quoi.utils.center

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.util.Mth
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import quoi.QuoiMod.mc
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonRoom
import quoi.utils.Scheduler.scheduleTask
import quoi.utils.aabb
import quoi.utils.bounds
import quoi.utils.getDirection
import quoi.utils.isXZInterceptable
import quoi.utils.render.drawFilledBox
import quoi.utils.render.drawTracer
import quoi.utils.skyblock.player.MovementUtils.movementTask
import quoi.utils.skyblock.player.MovementUtils.resetInput
import quoi.utils.skyblock.player.RotationUtils.rotate
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.math.abs
import kotlin.math.atan2

/**
 * modified OdinFabric (BSD 3-Clause)
 * copyright (c) 2025-2026 odtheking
 * original: https://github.com/odtheking/Odin/blob/main/src/main/kotlin/com/odtheking/odin/features/impl/dungeon/puzzlesolvers/TPMazeSolver.kt
 */
object MazeSolver {
    private var tpPads = setOf<BlockPos>()
    private var correctPortals = listOf<BlockPos>()
    private var visited = CopyOnWriteArraySet<BlockPos>()
    private var best: BlockPos? = null

    private var walking = false
    private var nextMove = false
    private var realCells = listOf<Set<BlockPos>>()

    fun onRoomEnter(room: OdonRoom?) = with(room) {
        if (this?.name != "Teleport Maze") return@with
        reset()
        realCells = cells.map { set -> set.map { getRealCoords(it) }.toSet() }
        tpPads = endPortalFrameLocations.map { getRealCoords(it) }.toSet()
    }

    fun onPosition(packet: ClientboundPlayerPositionPacket) = with (packet.change.position) {
        if (Dungeon.currentRoom?.name != "Teleport Maze" || x % 0.5 != 0.0 || y != 69.5 || z % 0.5 != 0.0 || tpPads.isEmpty()) return@with
        visited.addAll(tpPads.filter { AABB.unitCubeFromLowerCorner(Vec3(x, y, z)).inflate(1.0, 0.0, 1.0).intersects(AABB(it)) ||
                mc.player?.boundingBox?.inflate(1.0, 0.0, 1.0)?.intersects(AABB(it)) == true })
        getCorrectPortals(Vec3(x, y, z), packet.change.yRot, packet.change.xRot)
        best = getBestPad(Vec3(x, y, z), packet.change.yRot)

        stop()
        scheduleTask {
            nextMove = true
        }
    }

    private fun getCorrectPortals(pos: Vec3, yaw: Float, pitch: Float) {
        if (correctPortals.isEmpty()) correctPortals = correctPortals.plus(tpPads)

        correctPortals = correctPortals.filter {
            it !in visited &&
                    isXZInterceptable(
                        AABB(it.x.toDouble(), it.y.toDouble(), it.z.toDouble(), it.x + 1.0, it.y + 4.0, it.z + 1.0).inflate(0.75, 0.0, 0.75),
                        32.0, pos, yaw, pitch
                    ) && !it.aabb.inflate(0.5, 0.0, 0.5).intersects(mc.player?.boundingBox ?: return@filter false)
        }
    }

    fun onRenderWorld(ctx: LevelRenderContext, mazeColourOne: Colour, mazeColourMultiple: Colour, mazeColourVisited: Colour, showTracer: Boolean, tracerColour: Colour) {
        if (Dungeon.currentRoom?.name != "Teleport Maze") return
        tpPads.forEach {
            val aabb = it.bounds?.move(it) ?: it.aabb
            when (it) {
                in correctPortals -> ctx.drawFilledBox(aabb, if (correctPortals.size == 1) mazeColourOne else mazeColourMultiple, false)
                in visited -> ctx.drawFilledBox(aabb, mazeColourVisited, true)
                else -> ctx.drawFilledBox(aabb, Colour.WHITE.withAlpha(0.5f), true)
            }
        }

        if (showTracer) {
            val target = best ?: return
            ctx.drawTracer(Vec3(target.x + 0.5, target.y + 0.8, target.z + 0.5), tracerColour, depth = false)
        }
    }

    fun onTick(player: LocalPlayer) {
        if (Dungeon.currentRoom?.name != "Teleport Maze") return
        if (visited.isEmpty()) return
        if (mc.gui.screen() != null) stop().also { return }

        if (nextMove) {
            val targetPos = getPad(player.position())

            if (targetPos != null) {
                val dir = getDirection(player.eyePosition, Vec3.atCenterOf(targetPos))
                player.rotate(dir)

                movementTask { input ->
                    input.forward = true
                    false
                }
                walking = true
            } else {
                stop()
            }

            nextMove = false

        } else if (walking) {
            movementTask { input ->
                input.forward = true
                false
            }
        }
    }

    private fun getPad(pos: Vec3): BlockPos? {

        val currentPad = tpPads.minByOrNull { pos.distanceToSqr(Vec3.atCenterOf(it)) } ?: return null
        val currentCell = realCells.find { currentPad in it } ?: return null
        best?.takeIf { it in currentCell && it !in visited }?.let { return it }

        if (correctPortals.size == 1) {
            val correctPad = correctPortals.first()
            if (correctPad in currentCell) return correctPad
        }

        val unvisited = currentCell.filter { it !in visited }
        return unvisited.find { it.x != currentPad.x && it.z != currentPad.z }
            ?: unvisited.maxByOrNull { pos.distanceToSqr(Vec3.atCenterOf(it)) }
    }

    private fun getBestPad(pos: Vec3, yaw: Float): BlockPos? {
        val currentPad = tpPads.minByOrNull { pos.distanceToSqr(Vec3.atCenterOf(it)) } ?: return null
        val currentCell = realCells.find { currentPad in it } ?: return null

        if (currentCell.size == 1) return null
        val candidates = currentCell.filter { it != currentPad && it !in visited }

        return candidates.firstOrNull { it in correctPortals }
            ?: candidates.minByOrNull {
                val targetYaw = (atan2(it.center.z - pos.z, it.center.x - pos.x) * 180.0 / Math.PI).toFloat() - 90f
                abs(Mth.wrapDegrees(targetYaw) - Mth.wrapDegrees(yaw))
            }
    }

    private fun stop() {
        if (walking) {
            mc.player?.resetInput()
            walking = false
        }
    }

    fun reset() {
        stop()
        correctPortals = listOf()
        visited = CopyOnWriteArraySet()
        best = null
        nextMove = false
    }


    private val endPortalFrameLocations = setOf(
        BlockPos(4, 69, 14), BlockPos(10, 69, 14), BlockPos(10, 69, 20), BlockPos(4, 69, 20), // emerald

        BlockPos(4, 69, 12), BlockPos(4, 69, 6), BlockPos(10, 69, 6), BlockPos(10, 69, 12), //

        BlockPos(12, 69, 28), BlockPos(12, 69, 22), BlockPos(18, 69, 22), BlockPos(18, 69, 28), // lapis

        BlockPos(26, 69, 14), BlockPos(20, 69, 20), BlockPos(20, 69, 14), BlockPos(26, 69, 20), // iron

        BlockPos(26, 69, 28), BlockPos(26, 69, 22), BlockPos(20, 69, 28), BlockPos(20, 69, 22), // coal

        BlockPos(10, 69, 22), BlockPos(10, 69, 28), BlockPos(4, 69, 28), BlockPos(4, 69, 22), // diamond

        BlockPos(20, 69, 6), BlockPos(20, 69, 12), BlockPos(26, 69, 12), BlockPos(26, 69, 6), // gold

        BlockPos(15, 69, 14), // end
        BlockPos(15, 69, 12), // start
    )

    private val cells = listOf(
        setOf(BlockPos(4, 69, 14), BlockPos(10, 69, 14), BlockPos(10, 69, 20), BlockPos(4, 69, 20)),
        setOf(BlockPos(4, 69, 12), BlockPos(4, 69, 6), BlockPos(10, 69, 6), BlockPos(10, 69, 12)),
        setOf(BlockPos(12, 69, 28), BlockPos(12, 69, 22), BlockPos(18, 69, 22), BlockPos(18, 69, 28)),
        setOf(BlockPos(26, 69, 14), BlockPos(20, 69, 20), BlockPos(20, 69, 14), BlockPos(26, 69, 20)),
        setOf(BlockPos(26, 69, 28), BlockPos(26, 69, 22), BlockPos(20, 69, 28), BlockPos(20, 69, 22)),
        setOf(BlockPos(10, 69, 22), BlockPos(10, 69, 28), BlockPos(4, 69, 28), BlockPos(4, 69, 22)),
        setOf(BlockPos(20, 69, 6), BlockPos(20, 69, 12), BlockPos(26, 69, 12), BlockPos(26, 69, 6))
    )

}