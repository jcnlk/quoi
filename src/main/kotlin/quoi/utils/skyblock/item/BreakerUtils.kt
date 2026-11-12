package quoi.utils.skyblock.item

import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks
import quoi.annotations.Init
import quoi.api.events.PacketEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.EventListener
import quoi.api.events.core.Priority
import quoi.api.events.core.on
import quoi.api.skyblock.dungeon.Dungeon
import quoi.utils.Shortcuts
import quoi.utils.equalsOneOf
import quoi.utils.skyblock.item.ItemUtils.skyblockId

/**
 * modified NoammAddons (CC0-1.0)
 * original: https://github.com/Noamm9/NoammAddons/blob/0923fdf7efdbbdded2e3eff989ecdbbb4e2f2649/src/main/kotlin/com/github/noamm9/features/impl/dungeon/BreakerHelper.kt
 */
@Init
object BreakerUtils : EventListener, Shortcuts {
    // tooltip says +2 every second, server actually gives +2 three times a second
    private const val REGEN_TIME = 1000L / 3
    private const val REGEN_AMOUNT = 2
    private const val MAX_CHARGES = 20

    private var trackedCharges = MAX_CHARGES
    private var lastRegen = System.currentTimeMillis()

    val charges: Int
        get() {
            val now = System.currentTimeMillis()
            val ticks = (now - lastRegen) / REGEN_TIME
            trackedCharges = (trackedCharges + ticks.toInt() * REGEN_AMOUNT).coerceAtMost(MAX_CHARGES)
            lastRegen = if (trackedCharges == MAX_CHARGES) now else lastRegen + ticks * REGEN_TIME
            return trackedCharges
        }

    init {
        on<WorldEvent.Change> {
            trackedCharges = MAX_CHARGES
            lastRegen = System.currentTimeMillis()
        }

        on<PacketEvent.Sent, ServerboundPlayerActionPacket>(Priority.LOWEST) { // lowest so cancelled packets don't count
            if (packet.action != ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK) return@on
            if (!Dungeon.inDungeons || !Dungeon.hasStarted) return@on
            if (player.mainHandItem.skyblockId != "DUNGEONBREAKER") return@on
            if (!consumesCharge(packet.pos)) return@on
            if (charges > 0) trackedCharges--
        }
    }

    fun getBreakerCharges(stack: ItemStack): Int { // lore doesn't update while mining so charges are tracked clientside
        if (stack.isEmpty || stack.skyblockId != "DUNGEONBREAKER") return 0
        return charges
    }

    fun consumesCharge(pos: BlockPos): Boolean {
        val block = level.getBlockState(pos).block
        return block !in Dungeon.blacklistedDBBlocks && !block.equalsOneOf(Blocks.OBSIDIAN, Blocks.AIR)
    }
}
