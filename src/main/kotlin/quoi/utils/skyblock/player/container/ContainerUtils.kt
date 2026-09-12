package quoi.utils.skyblock.player.container

import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.inventory.AbstractContainerMenu
import quoi.api.events.ContainerEvent
import quoi.annotations.Init
import quoi.api.events.PacketEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.EventListener
import quoi.api.events.core.Priority
import quoi.api.events.core.on
import quoi.utils.Shortcuts
import quoi.utils.skyblock.player.container.task.ContainerAction
import quoi.utils.skyblock.player.container.task.ContainerManager

@Init
object ContainerUtils : EventListener, Shortcuts {
    @Volatile
    var containerId = 0
        private set
    @Volatile
    var lastStateId = 0
        private set

    @Volatile
    var containerServerSide = false
        private set

    class OpenContainer(val title: String, val menu: AbstractContainerMenu, val revision: Long) {
        var itemsLoaded = false
            internal set
    }

    var current: OpenContainer? = null
        private set
    var revision = 0L
        private set

    init {
        on<PacketEvent.Received>(Priority.HIGHEST + 1) {
            when (packet) {
                is ClientboundOpenScreenPacket -> {
                    containerId = packet.containerId
                    lastStateId = 0
                    containerServerSide = false
                }
                is ClientboundContainerClosePacket -> {
                    if (packet.containerId != containerId) return@on
                    containerId = 0
                    lastStateId = 0
                    containerServerSide = false
                }
                is ClientboundContainerSetSlotPacket -> {
                    if (packet.containerId == containerId) lastStateId = packet.stateId
                }
                is ClientboundContainerSetContentPacket -> {
                    if (packet.containerId == containerId) lastStateId = packet.stateId
                }
            }
        }
        on<PacketEvent.Received, ClientboundOpenScreenPacket>(Priority.LOWEST - 1, acceptCancelled = true) {
            val owner = ContainerManager.activeTask?.takeIf { task ->
                task.settings.silent && task.actions.any {
                    it is ContainerAction.AwaitContainer && it.containerName.containsMatchIn(packet.title.string)
                }
            }
            if (owner != null) cancel()
            if (cancelled) {
                val connection = mc.connection ?: return@on
                val level = mc.level
                // 26.1 handles queued packets before mc.execute tasks, so the menu must use the packet queue too
                mc.packetProcessor().scheduleIfPossible(connection, object : Packet<ClientGamePacketListener> {
                    override fun type() = packet.type()

                    override fun handle(listener: ClientGamePacketListener) {
                        val player = mc.player ?: return
                        if (mc.connection !== connection || mc.level !== level) return
                        // task was cancelled while the open packet was queued
                        if (owner != null && (owner.cancellationRequested || owner.completed || ContainerManager.activeTask !== owner)) {
                            connection.send(ServerboundContainerClosePacket(packet.containerId))
                            return
                        }
                        containerServerSide = true
                        player.containerMenu = packet.type.create(packet.containerId, player.inventory)
                        if (owner != null) owner.ownedMenu = player.containerMenu
                        opened(packet, player.containerMenu)
                    }
                })
            }
        }
        on<PacketEvent.ReceivedPost>(Priority.LOWEST) {
            when (val p = packet) {
                is ClientboundOpenScreenPacket -> mc.player?.containerMenu?.let { menu ->
                    if (menu.containerId == p.containerId) opened(p, menu)
                }
                is ClientboundContainerSetContentPacket -> loaded(p.containerId)
                is ClientboundContainerSetSlotPacket -> {
                    val container = current ?: return@on
                    // servers that send slots individually finish with the last container slot
                    val lastMenuSlot = container.menu.slots.indexOfLast { it.container !== mc.player?.inventory }
                    if (p.slot == lastMenuSlot) loaded(p.containerId)
                }
                is ClientboundContainerClosePacket -> {
                    if (current?.menu?.containerId == p.containerId) current = null
                }
            }
        }
        on<PacketEvent.Sent, ServerboundContainerClosePacket>(Priority.HIGHEST + 1) {
            if (packet.containerId != containerId) return@on
            containerId = 0
            lastStateId = 0
            containerServerSide = false
        }
        on<WorldEvent.Change> {
            containerId = 0
            lastStateId = 0
            containerServerSide = false
            current = null
        }
    }

    private fun opened(packet: ClientboundOpenScreenPacket, menu: AbstractContainerMenu) {
        val container = OpenContainer(packet.title.string, menu, ++revision)
        current = container
        ContainerEvent.Open(container).post()
    }

    private fun loaded(id: Int) {
        val container = current ?: return
        if (container.menu.containerId != id || mc.player?.containerMenu !== container.menu) return
        container.itemsLoaded = true
        ContainerEvent.Items(container).post()
    }

    inline val MenuType<*>.containerSize: Int
        get() = when (this) {
            MenuType.GENERIC_9x1 -> 9
            MenuType.GENERIC_9x2 -> 18
            MenuType.GENERIC_9x3 -> 27
            MenuType.GENERIC_9x4 -> 36
            MenuType.GENERIC_9x5 -> 45
            MenuType.GENERIC_9x6 -> 54
            MenuType.GENERIC_3x3 -> 9
            MenuType.CRAFTER_3x3 -> 9
            MenuType.ANVIL -> 3
            MenuType.BEACON -> 1
            MenuType.FURNACE, MenuType.SMOKER, MenuType.BLAST_FURNACE -> 3
            MenuType.SHULKER_BOX -> 27
            else -> 54
        }

    fun LocalPlayer.clickSlot(slot: Int, containerId: Int = ContainerUtils.containerId, button: Int = 0, shift: Boolean = false) {
        if (containerId == 0 || containerMenu.containerId != containerId || slot !in containerMenu.slots.indices) return

        val clickType = when {
            button == 2 -> ContainerInput.CLONE
            shift -> ContainerInput.QUICK_MOVE
            else -> ContainerInput.PICKUP
        }

        gameMode.handleContainerInput(containerId, slot, button, clickType, this)
    }
}