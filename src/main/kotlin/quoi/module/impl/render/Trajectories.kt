package quoi.module.impl.render

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.minecraft.core.Direction
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.projectile.arrow.AbstractArrow
import net.minecraft.world.item.BowItem
import net.minecraft.world.item.EnderpearlItem
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import quoi.api.colour.Colour
import quoi.api.colour.multiplyAlpha
import quoi.api.events.RenderEvent
import quoi.api.events.TickEvent
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.utils.EntityUtils.renderPos
import quoi.utils.addVec
import quoi.utils.render.drawFilledBox
import quoi.utils.render.drawLine
import quoi.utils.render.drawWireFrameBox
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

object Trajectories : Module(
    "Trajectories",
    desc = "Shows the trajectories of bows and ender pearls."
) {
    private val bows by switch("Bows", false, desc = "Render trajectories of bow arrows.")
    private val pearls by switch("Pearls", true, desc = "Render trajectories of ender pearls.")
    private val plane by switch("Show plane", false, desc = "Shows a plane aligned to the block face that will be hit.")
    private val boxes by switch("Show boxes", true, desc = "Shows a box at the predicted impact point.")
    private val lines by switch("Show lines", true, desc = "Shows the trajectory as a line.")
    private val range by slider("Solver range", 30, 1, 120, 1, desc = "How many ticks are simulated.")
    private val lineWidth by slider("Line width", 1f, 0.1f, 5.0f, 0.1f, desc = "Width of the rendered trajectory.")
    private val planeSize by slider("Plane size", 2f, 0.1f, 5.0f, 0.1f, desc = "Size of the rendered impact plane.").childOf(::plane)
    private val boxSize by slider("Box size", 0.5f, 0.5f, 3.0f, 0.1f, desc = "Size of the impact box.").childOf(::boxes)
    private val colour by colourPicker("Colour", Colour.MINECRAFT_AQUA, desc = "Colour of the trajectory.")
    private val depth by switch("Depth check", true, desc = "Whether the trajectory should respect depth.")

    private var charge = 0f
    private var lastCharge = 0f
    private val impactBoxes = mutableListOf<AABB>()
    private var pearlImpactBox: AABB? = null

    init {
        on<TickEvent.End> {
            val player = mc.player ?: return@on
            lastCharge = charge
            val useCount = player.useItemRemainingTicks
            charge = min((72000 - useCount) / 20f, 1.0f) * 2f

            if ((lastCharge - charge) > 1f) {
                lastCharge = charge
            }
        }

        on<RenderEvent.World> {
            impactBoxes.clear()
            pearlImpactBox = null

            val player = mc.player ?: return@on
            val heldItem = player.mainHandItem

            if (bows && heldItem.item is BowItem) {
                renderTrajectory(ctx, isPearl = false, useCharge = true)
            }

            if (pearls && heldItem.item is EnderpearlItem) {
                if (heldItem.displayName.string.contains("Spirit", ignoreCase = true)) return@on
                renderTrajectory(ctx, isPearl = true)
            }
        }
    }

    private fun renderTrajectory(ctx: WorldRenderContext, isPearl: Boolean, useCharge: Boolean = false) {
        val (points, hit) = calculateTrajectory(isPearl = isPearl, useCharge = useCharge)

        if (lines) {
            ctx.drawLine(points, colour, depth, lineWidth)
        }

        if (boxes) {
            ctx.drawCollisionBoxes(isPearl)
        }

        if (plane && hit != null) {
            ctx.drawPlaneCollision(hit)
        }
    }

    private fun calculateTrajectory(isPearl: Boolean, useCharge: Boolean = false): Pair<List<Vec3>, BlockHitResult?> {
        val player = mc.player ?: return emptyList<Vec3>() to null
        val level = mc.level ?: return emptyList<Vec3>() to null

        val yawRadians = Math.toRadians(player.yRot.toDouble())
        val spawnOffset = Vec3(
            -cos(yawRadians) * 0.16,
            player.eyeHeight.toDouble() - 0.1,
            -sin(yawRadians) * 0.16
        )

        var position = player.renderPos.add(spawnOffset)
        val velocityMultiplier = if (isPearl) {
            1.5f
        } else {
            (if (useCharge) interpolatedCharge() else 2f) * 1.5f
        }
        var motion = getLook(player.yRot, player.xRot).normalize().scale(velocityMultiplier.toDouble())

        val points = mutableListOf<Vec3>()

        repeat(range) {
            points += position

            if (!isPearl) {
                val entityBox = AABB(
                    position.x - 0.5,
                    position.y - 0.5,
                    position.z - 0.5,
                    position.x + 0.5,
                    position.y + 0.5,
                    position.z + 0.5
                ).inflate(0.01)

                if (level.getEntities(player, entityBox) { it !is AbstractArrow && it !is ArmorStand }.isNotEmpty()) {
                    return points to null
                }
            }

            val nextPosition = position.add(motion)
            val hit = level.clip(
                ClipContext(
                    position,
                    nextPosition,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    player
                )
            )

            if (hit.type == HitResult.Type.BLOCK) {
                val blockHit = hit as BlockHitResult
                points += blockHit.location

                if (boxes) {
                    val impactBox = AABB(
                        blockHit.location.x - 0.15 * boxSize,
                        blockHit.location.y - 0.15 * boxSize,
                        blockHit.location.z - 0.15 * boxSize,
                        blockHit.location.x + 0.15 * boxSize,
                        blockHit.location.y + 0.15 * boxSize,
                        blockHit.location.z + 0.15 * boxSize
                    )

                    if (isPearl) {
                        pearlImpactBox = impactBox
                    } else {
                        impactBoxes += impactBox
                    }
                }

                return points to blockHit
            }

            position = nextPosition
            motion = if (isPearl) {
                Vec3(motion.x * 0.99, motion.y * 0.99 - 0.03, motion.z * 0.99)
            } else {
                Vec3(motion.x * 0.99, motion.y * 0.99 - 0.05, motion.z * 0.99)
            }
        }

        return points to null
    }

    private fun WorldRenderContext.drawPlaneCollision(hit: BlockHitResult) {
        val (from, to) = when (hit.direction) {
            Direction.DOWN, Direction.UP ->
                hit.location.addVec(-0.15 * planeSize, -0.02, -0.15 * planeSize) to
                    hit.location.addVec(0.15 * planeSize, 0.02, 0.15 * planeSize)
            Direction.NORTH, Direction.SOUTH ->
                hit.location.addVec(-0.15 * planeSize, -0.15 * planeSize, -0.02) to
                    hit.location.addVec(0.15 * planeSize, 0.15 * planeSize, 0.02)
            Direction.WEST, Direction.EAST ->
                hit.location.addVec(-0.02, -0.15 * planeSize, -0.15 * planeSize) to
                    hit.location.addVec(0.02, 0.15 * planeSize, 0.15 * planeSize)
        }

        drawFilledBox(
            AABB(from.x, from.y, from.z, to.x, to.y, to.z),
            colour.multiplyAlpha(0.5f),
            depth
        )
    }

    private fun WorldRenderContext.drawCollisionBoxes(isPearl: Boolean) {
        if (isPearl) {
            pearlImpactBox?.let { box ->
                drawWireFrameBox(box, colour, lineWidth, depth)
                drawFilledBox(box, colour.multiplyAlpha(0.3f), depth)
            }
            return
        }

        impactBoxes.forEach { box ->
            drawWireFrameBox(box, colour, lineWidth, depth)
            drawFilledBox(box, colour.multiplyAlpha(0.3f), depth)
        }
    }

    private fun interpolatedCharge(): Float {
        val partialTicks = mc.deltaTracker.getGameTimeDeltaPartialTick(true)
        return lastCharge + (charge - lastCharge) * partialTicks
    }

    private fun getLook(yaw: Float, pitch: Float): Vec3 {
        val horizontal = -cos(-pitch * 0.017453292) * 1.0
        return Vec3(
            sin(-yaw * 0.017453292 - Math.PI) * horizontal,
            sin(-pitch * 0.017453292) * 1.0,
            cos(-yaw * 0.017453292 - Math.PI) * horizontal
        )
    }
}
