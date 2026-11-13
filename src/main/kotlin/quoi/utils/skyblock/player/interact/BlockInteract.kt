package quoi.utils.skyblock.player.interact

import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.world.InteractionHand
import quoi.utils.WorldUtils.startPrediction
import quoi.utils.gameMode
import quoi.utils.getHitResult

class BlockInteract(private val pos: BlockPos, private val force: Boolean) {
    fun execute() {
        val hitResult = pos.getHitResult(force) ?: return

        gameMode.startPrediction { sequence ->
            ServerboundUseItemOnPacket(
                InteractionHand.MAIN_HAND,
                hitResult,
                sequence
            )
        }
    }
}