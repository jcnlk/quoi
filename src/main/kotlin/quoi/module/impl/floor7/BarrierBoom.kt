package quoi.module.impl.floor7

import net.minecraft.world.InteractionHand
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import quoi.api.events.TickEvent
import quoi.api.events.core.on
import quoi.api.skyblock.dungeon.Dungeon.isDead
import quoi.api.skyblock.dungeon.Floor7Utils
import quoi.api.skyblock.dungeon.Phase
import quoi.api.skyblock.location.Island
import quoi.api.skyblock.location.invoke
import quoi.module.Module
import quoi.utils.skyblock.player.SwapManager
import quoi.utils.skyblock.player.SwapResult

// Kyleen
object BarrierBoom : Module( // todo move to triggerbot module
    "Barrier Boom",
    desc = "Automatically blows up Goldor fight gates.",
    area = Island.Dungeon(7, inBoss = true)
) {

    private var hasClickedBarrier = false

    init {
        on<TickEvent.Start> {
            val currentStage = Floor7Utils.getStage()
            if (mc.screen != null || isDead || !Floor7Utils.inPhaseAt(Phase.P3) || currentStage.number !in 1..3 || currentStage.gate) return@on

            val result = mc.hitResult
            if (result is BlockHitResult && result.type == HitResult.Type.BLOCK) {
                val state = level.getBlockState(result.blockPos)

                if (state.block == Blocks.BARRIER && !hasClickedBarrier) {
                    val swap = SwapManager.swapById("INFINITE_SUPERBOOM_TNT", "SUPERBOOM_TNT")

                    if (swap == SwapResult.SUCCESS || swap == SwapResult.ALREADY_SELECTED) {
                        gameMode.startDestroyBlock(result.blockPos, result.direction)
                        player.swing(InteractionHand.MAIN_HAND)
                        hasClickedBarrier = true
                    }
                }
            } else {
                hasClickedBarrier = false
            }
        }
    }
}
