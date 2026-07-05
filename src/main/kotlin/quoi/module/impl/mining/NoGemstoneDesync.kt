package quoi.module.impl.mining

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.StainedGlassBlock
import net.minecraft.world.level.block.StainedGlassPaneBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import quoi.api.events.BlockEvent
import quoi.api.events.TickEvent
import quoi.api.events.core.on
import quoi.api.skyblock.location.Island
import quoi.api.skyblock.location.Location.currentArea
import quoi.module.Module

object NoGemstoneDesync : Module(
    "No Gemstone Desync",
    desc = "Fixes adjacent gemstone blocks not updating correctly after mining."
) {
    private val affectedIslands = setOf(
        Island.DwarvenMines,
        Island.CrystalHollows,
        Island.Mineshaft,
        Island.CrimsonIsle,
        Island.Rift,
    )
    private val pendingUpdates = mutableSetOf<BlockPos>()

    init {
        on<BlockEvent.Update> {
            if (!active || currentArea !in affectedIslands) return@on
            if (updated.isAir && isStainedGlass(old)) pendingUpdates += pos.immutable()
        }

        // BlockEvent.Update is emitted before LevelChunk applies the new state. Waiting until
        // the next tick ensures neighbours observe air at the mined block position.
        on<TickEvent.Start> {
            if (pendingUpdates.isEmpty()) return@on
            val updates = pendingUpdates.toList()
            pendingUpdates.clear()

            if (!active || currentArea !in affectedIslands) return@on
            updates.forEach { pos -> level.getBlockState(pos).updateNeighbourShapes(level, pos, Block.UPDATE_ALL) }
        }
    }

    @JvmStatic
    fun shouldFillDisconnectedPane(state: BlockState): Boolean =
        active && currentArea in affectedIslands && state.block is StainedGlassPaneBlock &&
            !state.getValue(BlockStateProperties.NORTH) &&
            !state.getValue(BlockStateProperties.EAST) &&
            !state.getValue(BlockStateProperties.SOUTH) &&
            !state.getValue(BlockStateProperties.WEST)

    @JvmStatic
    fun fillDisconnectedPane(state: BlockState): BlockState =
        state
            .setValue(BlockStateProperties.NORTH, true)
            .setValue(BlockStateProperties.EAST, true)
            .setValue(BlockStateProperties.SOUTH, true)
            .setValue(BlockStateProperties.WEST, true)

    private fun isStainedGlass(state: BlockState): Boolean =
        state.block is StainedGlassBlock || state.block is StainedGlassPaneBlock

    override fun onDisable() {
        pendingUpdates.clear()
    }
}
