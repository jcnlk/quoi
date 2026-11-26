package quoi.utils.skyblock.item

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.tags.BlockTags
import net.minecraft.util.Mth.wrapDegrees
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.*
import net.minecraft.world.level.block.piston.PistonHeadBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import quoi.api.world.Direction
import quoi.api.world.RaycastResult
import quoi.utils.*
import quoi.utils.skyblock.player.PlayerUtils.eyePosition
import kotlin.math.*
import net.minecraft.core.Direction as McDirection

// todo cleanup
object TeleportUtils : Shortcuts {

    fun Vec3.getEtherPos(yaw: Float, pitch: Float, distance: Double = 61.0): RaycastResult {
        val to = getLook(wrapDegrees(yaw), wrapDegrees(pitch)).scale(distance).add(this)
        return traverseVoxels(this, to, true)
    }

    /**
     * Returns a triple of distance, yaw, pitch to rotate to the given position with etherwarp physics, or null if etherwarp is not possible.
     *
     * @param to The position to rotate to.
     * @return A triple of distance, yaw, pitch to rotate to the given position with etherwarp physics, or null if etherwarp is not possible
     * @see quoi.utils.getDirection
     * @author Aton
     */
    fun getEtherwarpDirection(from: Vec3, to: BlockPos, dist: Double = 61.0): Direction? {
        if (from.distanceToSqr(to.vec3) > (dist + 2) * (dist + 2)) return null

        val visibleVec = getVisiblePoint(from, to) ?: return null

        return getDirection(from, visibleVec)
    }

    fun getEtherwarpDirection(to: BlockPos, dist: Double = 61.0) =
        getEtherwarpDirection(player.eyePosition(true), to, dist)

    fun predictTransmission(
        x0: Double,
        y0: Double,
        z0: Double,
        dx: Double,
        dy: Double,
        dz: Double,
        distance: Double
    ): RaycastResult {
        val world = mc.level ?: return RaycastResult.NONE
        val landing = TransmissionPredictor.predict(world, Vec3(x0, y0, z0), Vec3(dx, dy, dz), distance)
            ?: return RaycastResult.NONE
        val pos = BlockPos.containing(landing)
        return RaycastResult(true, pos, world.getBlockState(pos)).apply { vec = landing }
    }

    /** [from] is the player's feet position. */
    fun predictTransmission(from: Vec3, yaw: Float, pitch: Float, distance: Double = 12.0): RaycastResult {
        val (dx, dy, dz) = Vec3.directionFromRotation(pitch, yaw)
        return predictTransmission(from.x, from.y, from.z, dx, dy, dz, distance)
    }

    /** Aims from the eyes at [to], starting with the player's feet at [from]. */
    fun predictTransmission(from: Vec3, to: Vec3, distance: Double = 12.0): RaycastResult {
        val direction = getDirection(from.add(0.0, TransmissionPredictor.EYE_HEIGHT, 0.0), to)
        return predictTransmission(from, direction.yaw, direction.pitch, distance)
    }

    /**
     * Traverses voxels from start to end and returns the first non-air block it hits.
     * @author unclambomb6
     */
    fun traverseVoxels(
        x0: Double, y0: Double, z0: Double, x1: Double, y1: Double, z1: Double,
        etherwarp: Boolean, world: BlockGetter = level
    ): RaycastResult {
        var x = floor(x0)
        var y = floor(y0)
        var z = floor(z0)

        val endX = floor(x1)
        val endY = floor(y1)
        val endZ = floor(z1)

        val dirX = x1 - x0
        val dirY = y1 - y0
        val dirZ = z1 - z0

        val stepX = sign(dirX).toInt()
        val stepY = sign(dirY).toInt()
        val stepZ = sign(dirZ).toInt()

        val invDirX = if (dirX != 0.0) 1.0 / dirX else Double.MAX_VALUE
        val invDirY = if (dirY != 0.0) 1.0 / dirY else Double.MAX_VALUE
        val invDirZ = if (dirZ != 0.0) 1.0 / dirZ else Double.MAX_VALUE

        val tDeltaX = abs(invDirX * stepX)
        val tDeltaY = abs(invDirY * stepY)
        val tDeltaZ = abs(invDirZ * stepZ)

        var tMaxX = abs((x + max(stepX, 0) - x0) * invDirX)
        var tMaxY = abs((y + max(stepY, 0) - y0) * invDirY)
        var tMaxZ = abs((z + max(stepZ, 0) - z0) * invDirZ)

        val mut = BlockPos.MutableBlockPos()

        var lastChunkX = Int.MIN_VALUE
        var lastChunkZ = Int.MIN_VALUE
        var chunk: BlockGetter? = null

        repeat(1000) {
            mut.set(x, y, z)

            val cx = x.toInt() shr 4
            val cz = z.toInt() shr 4

            if (cx != lastChunkX || cz != lastChunkZ) {
                chunk = if (world is ClientLevel) world.getChunk(cx, cz) else world
                lastChunkX = cx
                lastChunkZ = cz
            }

            val state = chunk?.getBlockState(mut) ?: return RaycastResult.NONE
            val passable = isEtherwarpPassable(world, mut, state)
            if (!passable || (!etherwarp && !state.isAir)) {
                val hitPos = mut.immutable()
                return RaycastResult(!passable && isValidEtherwarpBlock(world, hitPos), hitPos, state)
            }

            if (x == endX && y == endY && z == endZ) {
                return if (state.isAir) RaycastResult.NONE else RaycastResult(false, mut.immutable(), state)
            }

            when {
                tMaxX <= tMaxY && tMaxX <= tMaxZ -> {
                    tMaxX += tDeltaX
                    x += stepX
                }

                tMaxY <= tMaxZ -> {
                    tMaxY += tDeltaY
                    y += stepY
                }

                else -> {
                    tMaxZ += tDeltaZ
                    z += stepZ
                }
            }
        }

        return RaycastResult.NONE
    }

    fun traverseVoxels(from: Vec3, to: Vec3, etherwarp: Boolean): RaycastResult {
        val (x0, y0, z0) = from
        val (x1, y1, z1) = to
        return traverseVoxels(x0, y0, z0, x1, y1, z1, etherwarp)
    }

    // based on https://github.com/Noamm9/NoammAddons/blob/47781280ed21f85e9dc019e1556c46313c6eb69a/src/main/kotlin/com/github/noamm9/utils/items/EtherwarpHelper.kt
    fun isEtherwarpPassable(world: BlockGetter, pos: BlockPos, state: BlockState = world.getBlockState(pos)): Boolean {
        return when (state.block) {
            is SignBlock, is BannerBlock, is WallBannerBlock, is TripWireHookBlock,
            is PressurePlateBlock, is WeightedPressurePlateBlock -> false

            is ButtonBlock, is SkullBlock, is WallSkullBlock, is LadderBlock,
            is BubbleColumnBlock, is FlowerPotBlock, is PistonHeadBlock, is LeverBlock,
            is NetherWartBlock, is ComparatorBlock, is RedstoneTorchBlock, is RepeaterBlock -> true

            else -> state.getCollisionShape(world, pos).isEmpty
        }
    }

    fun getEtherwarpLandingY(pos: BlockPos, world: BlockGetter = level): Double {
        val state = world.getBlockState(pos)
        val tall = state.`is`(BlockTags.FENCES) || state.`is`(BlockTags.WALLS) || state.`is`(BlockTags.FENCE_GATES)
        return pos.y + if (tall) 2.0 else 1.0
    }

    fun isValidEtherwarpBlock(world: BlockGetter, pos: BlockPos): Boolean {
        if (isEtherwarpPassable(world, pos)) return false
        val top = world.getBlockState(pos).getCollisionShape(world, pos).max(McDirection.Axis.Y)
        val clearanceBaseY = pos.y + max(1, ceil(top).toInt())
        val feet = BlockPos(pos.x, clearanceBaseY, pos.z)
        return !blocksEtherwarpClearance(world, feet) && !blocksEtherwarpClearance(world, feet.above())
    }

    private fun blocksEtherwarpClearance(world: BlockGetter, pos: BlockPos): Boolean =
        when (world.getBlockState(pos).block) {
            is SkullBlock, is WallSkullBlock, is ComparatorBlock, is RepeaterBlock,
            is FlowerPotBlock, is LadderBlock -> true

            is SignBlock, is BannerBlock, is WallBannerBlock, is TripWireHookBlock,
            is PressurePlateBlock, is WeightedPressurePlateBlock -> false

            else -> !isEtherwarpPassable(world, pos)
        }
}
