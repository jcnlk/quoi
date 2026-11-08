package quoi.api.skyblock.dungeon.terminals

import net.minecraft.network.protocol.game.ClientboundContainerClosePacket
import net.minecraft.network.protocol.game.ServerboundAttackPacket
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket
import net.minecraft.network.protocol.game.ServerboundInteractPacket
import net.minecraft.world.entity.decoration.ArmorStand
import quoi.QuoiMod.mc
import quoi.annotations.Init
import quoi.api.events.*
import quoi.api.events.core.*
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.skyblock.player.container.ContainerUtils

/**
 * modified NoammAddons (CC0-1.0)
 * original: https://github.com/Noamm9/NoammAddons/blob/5dff2334228c6cc78ebace0167052395ee192687/src/main/kotlin/com/github/noamm9/features/impl/floor7/terminals/TerminalListener.kt
 *
 * Tracks terminal metadata and interactions with unopened terminals
 */
@Init
object TerminalUtils : EventListener {
    var current: Terminal? = null
        private set

    val inTerminal: Boolean get() = current != null

    var interactCooldown = 0
        private set

    private var container: ContainerUtils.OpenContainer? = null

    init {
        on<ContainerEvent.Open>(Priority.HIGH) { opened(container) }
        on<ContainerEvent.Items>(Priority.HIGH) { loaded(container, EventDispatcher.totalTicks, System.currentTimeMillis()) }
        on<PacketEvent.ReceivedPost, ClientboundContainerClosePacket>(Priority.HIGH) {
            if (inTerminal) once<TickEvent.End> { reset() }
        }
        on<PacketEvent.Sent>(Priority.LOWEST) { sent(this) }
        on<TickEvent.Server> { if (interactCooldown > 0) interactCooldown-- }
        on<WorldEvent.Change>(Priority.HIGH) { reset() }
    }

    internal fun opened(open: ContainerUtils.OpenContainer) {
        reset()
        val title = open.title.noControlCodes
        val type = TerminalType.fromTitle(title) ?: return
        val terminal = Terminal(type, title, open.menu.containerId)
        container = open
        current = terminal
        TerminalEvent.Open(terminal).post()
    }

    internal fun loaded(open: ContainerUtils.OpenContainer, tick: Int, now: Long) {
        val terminal = current ?: return
        if (container !== open || terminal.fullyOpened) return
        terminal.loaded(tick, now)
        TerminalEvent.Open.Full(terminal).post()
    }

    internal fun sent(event: PacketEvent.Sent) {
        when (val packet = event.packet) {
            is ServerboundContainerClosePacket -> if (inTerminal) reset()
            is ServerboundInteractPacket -> interactEntity(packet.entityId, event)
            is ServerboundAttackPacket -> interactEntity(packet.entityId, event)
        }
    }

    private fun interactEntity(id: Int, event: PacketEvent.Sent) {
        val entity = mc.level?.getEntity(id) as? ArmorStand ?: return
        if (interact(entity.displayName.string.noControlCodes)) event.cancel()
    }

    internal fun interact(name: String): Boolean {
        if (name != "Inactive Terminal") return false
        if (interactCooldown > 0 || inTerminal) return true
        interactCooldown = 15 // TODO: maybe use different cooldown idk (surely term aura delay would work)
        return false
    }

    private fun reset() {
        val terminal = current ?: return
        current = null
        container = null
        terminal.clear()
        TerminalEvent.Close(terminal).post()
    }
}
