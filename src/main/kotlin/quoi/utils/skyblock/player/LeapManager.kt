package quoi.utils.skyblock.player

import net.minecraft.network.protocol.game.ClientboundContainerClosePacket
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import quoi.QuoiMod.mc
import quoi.annotations.Init
import quoi.api.events.ChatEvent
import quoi.api.events.PacketEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.EventListener
import quoi.api.events.core.on
import quoi.api.events.core.Priority
import quoi.api.skyblock.dungeon.Dungeon.dungeonTeammatesNoSelf
import quoi.api.skyblock.dungeon.Dungeon.getMageCooldownMultiplier
import quoi.api.skyblock.dungeon.Dungeon.inDungeons
import quoi.api.skyblock.dungeon.DungeonClass
import quoi.api.skyblock.dungeon.DungeonPlayer
import quoi.utils.ChatUtils.modMessage
import quoi.utils.Scheduler.scheduleTask
import quoi.utils.StringUtils.noControlCodes

@Init
object LeapManager : EventListener { // still schizophrenia
    private var menuOpened = false
    private var menuId = -1

    private data class PendingLeap(
        val target: DungeonPlayer,
        val onMenuOpen: (() -> Unit)?,
        val onMenuClose: (() -> Unit)?
    )

    private var activeLeap: PendingLeap? = null
    private var pendingLeap: PendingLeap? = null

    var lastLeap = 0L
        private set

    var leapCD = 0.0
        private set

    private val inProgress get() = activeLeap != null

    init {
        on<PacketEvent.Received> (Priority.LOWEST) {
            when (packet) {
                is ClientboundContainerSetSlotPacket -> {
                    if (!menuOpened || packet.containerId != menuId || packet.slot !in 0..35 || packet.item.isEmpty) return@on
                    cancel()
                    selectTarget(packet.slot, packet.item.displayName.string)
                }
                is ClientboundContainerSetContentPacket -> {
                    if (!menuOpened || packet.containerId != menuId) return@on
                    cancel()
                    packet.items.take(36).forEachIndexed { slot, stack ->
                        if (!stack.isEmpty) selectTarget(slot, stack.displayName.string)
                    }
                }
                is ClientboundOpenScreenPacket -> {
                    if (!inProgress) return@on
                    if (!packet.title.string.contains("Leap")) return@on
                    menuOpened = true
                    menuId = packet.containerId
                    activeLeap?.onMenuOpen?.invoke()
                    cancel()
                }
                is ClientboundContainerClosePacket -> {
                    if (!menuOpened || packet.containerId != menuId) return@on
                    failActiveLeap("leap menu closed before the target was selected")
                }
            }
        }

        on<ChatEvent.Packet> {
            if (!inProgress) return@on
            if (message.noControlCodes == "You cannot use this in a solo dungeon!" ||
                message.noControlCodes == "There are no other players to teleport to!") { // probably will never happen on main server
                modMessage("&cFailed to leap! You're in a solo dungeon!")
                resetActiveLeap()
            }
        }

        on<WorldEvent.Change> {
            pendingLeap = null
            resetActiveLeap()
        }

        on<TickEvent.Server> {
            if (leapCD > 0) leapCD -= 1

            val pending = pendingLeap
            if (pending != null && mc.gui.screen() == null && ContainerUtils.containerId == -1) {
                doLeap(pending)
                pendingLeap = null
            }
        }
    }

    fun leap(target: Any, onMenuOpen: (() -> Unit)? = null, onMenuClose: (() -> Unit)? = null) {
        if (!inDungeons || target == DungeonClass.Unknown) return

        val teammate = when (target) {
            is String -> dungeonTeammatesNoSelf.firstOrNull { !it.isDead && it.name.equals(target, true) }
            is DungeonClass -> dungeonTeammatesNoSelf.firstOrNull { !it.isDead && it.clazz == target }
            else -> null
        } ?: return modMessage("&cFailed to leap! ${formatTarget(target)} &cnot found")

        if (mc.gui.screen() != null || ContainerUtils.containerId != -1) {
            pendingLeap = PendingLeap(teammate, onMenuOpen, onMenuClose)
            modMessage("&eQueued leap to ${formatName(teammate)}")
        } else doLeap(PendingLeap(teammate, onMenuOpen, onMenuClose))
    }

    private fun doLeap(leap: PendingLeap) {
        if (inProgress) return
        if (leapCD > 0) {
            modMessage("&cFailed to leap! On cooldown: ${"%.1f".format(leapCD / 20.0)}s")
            return
        }

        activeLeap = leap
        val r = SwapManager.swapById("INFINITE_SPIRIT_LEAP", "SPIRIT_LEAP").success
        scheduleTask {
            if (activeLeap !== leap) return@scheduleTask
            if (!r) {
                resetActiveLeap()
                return@scheduleTask
            }
            PlayerUtils.interact()
            scheduleTask(20) {
                if (activeLeap === leap) failActiveLeap("target not found in leap menu")
            }
        }
    }

    private fun selectTarget(slot: Int, itemName: String) {
        val leap = activeLeap ?: return
        if (!itemName.contains(leap.target.name, ignoreCase = true)) return
        menuOpened = false
        if (!ContainerUtils.click(slot, afterClick = { completeActiveLeap(leap) })) {
            return failActiveLeap("could not click target slot")
        }
    }

    private fun completeActiveLeap(leap: PendingLeap) {
        if (activeLeap !== leap) return

        lastLeap = System.currentTimeMillis()
        leapCD = 48 * getMageCooldownMultiplier()
        modMessage("&aLeaping to ${formatName(leap.target)}")
        resetActiveLeap()
    }

    private fun formatTarget(target: Any): String {
        return when (target) {
            is DungeonClass -> "&${target.colourCode}${target.name}"
            is String -> formatName(target)
            else -> target.toString()
        }
    }

    private fun formatName(name: String): String {
        val teammate = dungeonTeammatesNoSelf.firstOrNull { it.name.equals(name, true) }
        return if (teammate != null) formatName(teammate) else "&f$name"
    }

    private fun formatName(player: DungeonPlayer): String {
        return "&${player.clazz.colourCode}${player.name}"
    }

    private fun failActiveLeap(reason: String) {
        val leap = activeLeap ?: return
        modMessage("&cFailed to leap to ${formatName(leap.target)}&c: $reason")
        resetActiveLeap()
    }

    private fun resetActiveLeap() {
        val leap = activeLeap
        menuOpened = false
        menuId = -1
        activeLeap = null
        leap?.onMenuClose?.invoke()
    }
}
