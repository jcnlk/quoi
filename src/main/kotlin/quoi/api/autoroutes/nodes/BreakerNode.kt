package quoi.api.autoroutes.nodes

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.core.BlockPos
import quoi.api.autoroutes.RouteNode
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonRoom
import quoi.api.vec.MutableVec3
import quoi.config.TypeName
import quoi.module.impl.dungeon.autoclear.impl.AutoRoutes
import quoi.utils.WorldUtils.state
import quoi.utils.aabb
import quoi.utils.render.drawFilledBox
import quoi.utils.render.drawWireFrameBox
import quoi.utils.skyblock.item.BreakerUtils.getBreakerCharges
import quoi.utils.skyblock.item.ItemUtils.skyblockId
import quoi.utils.skyblock.player.PlayerUtils
import quoi.utils.skyblock.player.SwapManager
import quoi.utils.skyblock.player.interact.AuraManager

@TypeName("dungeon_breaker")
class BreakerNode : RouteNode() {
    var blocks = emptyList<BlockPos>()

    @Transient
    private var batch = emptyList<BlockPos>()

    @Transient
    private var batchIndex = 0

    @Transient
    private var cooldown = 0

    @Transient
    private var realBlocks = emptyList<BlockPos>()

    override val colour: Colour
        get() = Colour.ORANGE

    override val priority: Int
        get() = 150

    override fun update(room: OdonRoom) {
        super.update(room)
        realBlocks = blocks.map { room.getRealCoords(it) }
    }

    override fun checkAwaits(pos: MutableVec3): Boolean {
        if (!inside()) return false
        return super.checkAwaits(pos)
    }

    override fun reset() {
        super.reset()
        batch = emptyList()
        batchIndex = 0
        cooldown = 0
    }

    override fun execute(pos: MutableVec3): Boolean {
        if (blocks.isEmpty()) return true
        if (realBlocks.none { level.isLoaded(it) && !it.state.isAir }) return true
        if (cooldown > 0) {
            cooldown--
            return false
        }

        val breakerSlot = PlayerUtils.breakerSlot ?: run {
            val hasBreaker = (0..8).any { player.inventory.getItem(it).skyblockId == "DUNGEONBREAKER" }
            return !hasBreaker
        }

        if (player.inventory.selectedSlot != breakerSlot) {
            if (!SwapManager.swapToSlot(breakerSlot).success) return false
        }

        val initialCharges = getBreakerCharges(player.inventory.getItem(breakerSlot))
        if (initialCharges == 0) return false

        if (AutoRoutes.zeroTickDb) {
            val batch = realBlocks.asSequence().filter { level.isLoaded(it) && !it.state.isAir }.take(initialCharges)
            batch.forEach { AuraManager.breakBlock(it, immediate = true) }
            cooldown = 9
        } else {
            if (batchIndex >= batch.size) {
                batch = realBlocks.filter { level.isLoaded(it) && !it.state.isAir }.take(initialCharges)
                batchIndex = 0
            }
            val block = batch[batchIndex++]
            if (level.isLoaded(block) && !block.state.isAir) AuraManager.breakBlock(block, immediate = true)
            if (batchIndex == batch.size) cooldown = 1
        }
        return false
    }

    override fun render(ctx: LevelRenderContext, style: String, colour: Colour, fillColour: Colour, activeColour: Colour, thickness: Float) {
        super.render(ctx, style, colour, fillColour, activeColour, thickness)
        if (!inside() && AutoRoutes.breakerRing == null) return
        for (pos in realBlocks) {
            val aabb = pos.aabb
            if (pos.state.isAir) ctx.drawWireFrameBox(aabb, Colour.RED.withAlpha(125), depth = true)
            else ctx.drawFilledBox(aabb, Colour.WHITE.withAlpha(125), depth = true)
        }
    }
}