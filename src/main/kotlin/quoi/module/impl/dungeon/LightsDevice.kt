package quoi.module.impl.dungeon

import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LeverBlock
import quoi.api.events.PacketEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.skyblock.Island
import quoi.api.skyblock.invoke
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.M7Phases
import quoi.module.Module
import quoi.utils.skyblock.player.PlayerUtils.rightClick

object LightsDevice : Module(
    "Lights Device",
    area = Island.Dungeon(7, inBoss = true),
    desc = "Adds triggerbot and hides useless levers for the lights device."
) {
    private val triggerBot by switch("Triggerbot", desc = "Automatically toggles the correct levers when you look at them.")
    private val delay by slider("Delay", 200L, 70L, 500L, 10L, unit = "ms", desc = "Delay between triggerbot clicks.")
    private val hideUselessLevers by switch("Hide useless levers", desc = "Turns useless lights device levers into ghost blocks.")

    private var lastTriggerAt = 0L
    private val pendingLevers = hashSetOf<BlockPos>()

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
        return enabled && hideUselessLevers && Dungeon.getF7Phase() == M7Phases.P3 && pos in uselessLevers
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
            if (Dungeon.getF7Phase() != M7Phases.P3) return@on

            pendingLevers.removeIf { pos ->
                val state = level.getBlockState(pos)
                state.block !is LeverBlock || state.getValue(LeverBlock.POWERED)
            }

            if (hideUselessLevers) {
                uselessLevers.forEach { pos ->
                    if (!level.getBlockState(pos).isAir) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 0)
                    }
                }
            }

            if (!triggerBot) return@on

            val pos = (mc.hitResult as? BlockHitResult)?.blockPos ?: return@on
            if (pos !in deviceLevers) return@on
            if (pos in pendingLevers) return@on

            val state = level.getBlockState(pos)
            if (state.block !is LeverBlock || state.getValue(LeverBlock.POWERED)) return@on

            val now = System.currentTimeMillis()
            if (now - lastTriggerAt < delay) return@on

            player.rightClick()
            lastTriggerAt = now
            pendingLevers += pos.immutable()
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
