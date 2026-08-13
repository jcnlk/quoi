package quoi.module.impl.floor7

import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LeverBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import quoi.api.events.PacketEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.on
import quoi.api.skyblock.dungeon.Floor7Utils
import quoi.api.skyblock.dungeon.Phase
import quoi.api.skyblock.location.Island
import quoi.api.skyblock.location.invoke
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.childOf

object LightsDevice : Module(
    "Lights Device",
    area = Island.Dungeon(7, inBoss = true),
    desc = "Adds triggerbot and hides useless levers for the lights device."
) {
    private val triggerBot by switch("Triggerbot", desc = "Automatically toggles the correct levers when you look at them.")
    private val delay by slider("Delay", 200L, 70L, 500L, 10L, unit = "ms", desc = "Delay between triggerbot clicks.")
        .childOf(::triggerBot)
    private val hideUselessLevers by switch("Hide useless levers", desc = "Turns useless lights device levers into ghost blocks.")

    private var lastTriggerAt = 0L
    private val pendingLevers = hashMapOf<BlockPos, Long>()

    private val deviceLevers = setOf(
        BlockPos(58, 136, 142),
        BlockPos(58, 133, 142),
        BlockPos(60, 135, 142),
        BlockPos(60, 134, 142),
        BlockPos(62, 136, 142),
        BlockPos(62, 133, 142),
    )

    private val uselessLevers = listOf(
        BlockPos(61, 136, 142),
        BlockPos(60, 136, 142),
        BlockPos(59, 136, 142),
        BlockPos(58, 135, 142),
        BlockPos(59, 135, 142),
        BlockPos(61, 135, 142),
        BlockPos(62, 135, 142),
        BlockPos(62, 134, 142),
        BlockPos(61, 134, 142),
        BlockPos(59, 134, 142),
        BlockPos(58, 134, 142),
        BlockPos(59, 133, 142),
        BlockPos(60, 133, 142),
        BlockPos(61, 133, 142),
    )

    @JvmStatic
    fun shouldGhostLever(pos: BlockPos): Boolean {
        return enabled && hideUselessLevers && Floor7Utils.inPhaseAt(Phase.P3) && pos in uselessLevers
    }

    private fun BlockState.isUnpoweredDeviceLever(): Boolean {
        return block == Blocks.LEVER && hasProperty(LeverBlock.POWERED) && !getValue(LeverBlock.POWERED)
    }

    override fun onDisable() {
        lastTriggerAt = 0L
        pendingLevers.clear()
        super.onDisable()
    }

    init {
        on<WorldEvent.Change> {
            lastTriggerAt = 0L
            pendingLevers.clear()
        }

        on<TickEvent.End> {
            if (!Floor7Utils.inPhaseAt(Phase.P3)) return@on
            val now = System.currentTimeMillis()

            pendingLevers.entries.removeIf { (pos, triggeredAt) ->
                !level.getBlockState(pos).isUnpoweredDeviceLever() || now - triggeredAt >= delay + 150L
            }

            if (hideUselessLevers) {
                uselessLevers.forEach { pos ->
                    if (!level.getBlockState(pos).isAir) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 0)
                    }
                }
            }

            if (!triggerBot) return@on

            val hitResult = mc.hitResult as? BlockHitResult ?: return@on
            if (hitResult.type != HitResult.Type.BLOCK) return@on

            val pos = hitResult.blockPos
            if (pos !in deviceLevers) return@on
            if (pos in pendingLevers.keys) return@on

            if (!level.getBlockState(pos).isUnpoweredDeviceLever()) return@on

            if (now - lastTriggerAt < delay) return@on

            val result = mc.gameMode?.useItemOn(player, InteractionHand.MAIN_HAND, hitResult) ?: return@on
            if (!result.consumesAction()) return@on

            player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false)
            lastTriggerAt = now
            pendingLevers[pos.immutable()] = now
        }

        on<PacketEvent.Sent, ServerboundUseItemOnPacket> {
            if (shouldGhostLever(packet.hitResult.blockPos)) {
                cancel()
            }
        }

        on<PacketEvent.Received> {
            when (packet) {
                is ClientboundBlockUpdatePacket -> {
                    if (shouldGhostLever(packet.pos)) {
                        cancel()
                    }
                }
                is ClientboundSectionBlocksUpdatePacket -> {
                    var cancelPacket = false
                    packet.runUpdates { pos, _ ->
                        if (shouldGhostLever(pos.immutable())) {
                            cancelPacket = true
                        }
                    }
                    if (cancelPacket) {
                        cancel()
                    }
                }
            }
        }
    }
}
