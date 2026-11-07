package quoi.utils.skyblock.player.container

import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.protocol.game.*
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.inventory.Slot
import quoi.annotations.Init
import quoi.annotations.Internal
import quoi.api.events.ContainerEvent
import quoi.api.events.PacketEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.EventListener
import quoi.api.events.core.Priority
import quoi.api.events.core.on
import quoi.mixins.accessors.AbstractContainerScreenAccessor
import quoi.utils.Shortcuts
import quoi.utils.skyblock.player.container.task.ContainerAction
import quoi.utils.skyblock.player.container.task.ContainerManager
import quoi.utils.skyblock.player.container.task.ContainerTask

@Init
@OptIn(Internal::class)
object ContainerUtils : EventListener, Shortcuts {
    private data class ContainerCatch(val owner: ContainerTask, val name: Regex, var ticksRemaining: Int)

    private val catches = mutableListOf<ContainerCatch>()

    var containerId = 0
        private set

    var lastStateId = 0
        private set

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
                is ClientboundContainerSetSlotPacket -> {
                    if (packet.containerId == containerId) lastStateId = packet.stateId
                }
                is ClientboundContainerSetContentPacket -> {
                    if (packet.containerId == containerId) lastStateId = packet.stateId
                }
            }
        }
        on<PacketEvent.Received, ClientboundOpenScreenPacket>(Priority.LOWEST - 1, acceptCancelled = true) {
            // close caught reopens before creating a silent menu
            if (takeCatch(packet.title.string)) {
                cancel()
                mc.connection?.send(ServerboundContainerClosePacket(packet.containerId))
                return@on
            }
            val owner = ContainerManager.activeTask?.takeIf { task ->
                task.settings.silent && task.actions.any {
                    it is ContainerAction.AwaitContainer && it.containerName.containsMatchIn(packet.title.string)
                }
            }
            if (owner != null) cancel()
            if (cancelled) {
                val player = mc.player ?: return@on
                val menu = packet.type.create(packet.containerId, player.inventory)
                containerServerSide = true
                player.containerMenu = menu
                if (owner != null) owner.ownedMenu = menu
                opened(packet, menu)
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
                    containerId = 0
                    lastStateId = 0
                    containerServerSide = false
                    current = null
                }
            }
        }
        on<PacketEvent.Sent, ServerboundContainerClosePacket>(Priority.HIGHEST + 1) {
            if (current?.menu?.containerId == packet.containerId) current = null
            if (packet.containerId != containerId) return@on
            containerId = 0
            lastStateId = 0
            containerServerSide = false
        }
        on<TickEvent.Server> { catches.removeAll { --it.ticksRemaining <= 0 } }
        on<WorldEvent.Change> {
            catches.clear()
            containerId = 0
            lastStateId = 0
            containerServerSide = false
            current = null
        }
    }

    /**
     * Registers a catch for [task], or closes a matching container that has already reopened
     */
    @Internal
    internal fun catchContainer(task: ContainerTask, name: Regex, timeout: Int) {
        if (timeout == 0) return
        val open = current
        if (open != null && open.revision > task.containerRevision && name.containsMatchIn(open.title) &&
            mc.player?.containerMenu === open.menu
        ) {
            mc.player?.closeContainer()
            return
        }
        if (timeout > 0) catches.add(ContainerCatch(task, name, timeout))
    }

    /**
     * Removes catches for [task], or all catches if [task] is null
     */
    @Internal
    internal fun clearCatches(task: ContainerTask? = null) {
        if (task == null) catches.clear()
        else catches.removeAll { it.owner === task }
    }

    private fun takeCatch(title: String): Boolean {
        val index = catches.indexOfFirst { it.name.containsMatchIn(title) }
        if (index == -1) return false
        catches.removeAt(index)
        return true
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

    val Screen.hoveredSlot: Slot?
        get() {
            if (this !is AbstractContainerScreen<*>) return null
            //? if >=26.4 {
            /*return (this as AbstractContainerScreenAccessor).`quoi$getSlotAtPos`()?.slot()
            *///?} else {
            val window = mc.window
            return (this as AbstractContainerScreenAccessor).`quoi$getSlotAtPos`(
                mc.mouseHandler.getScaledXPos(window),
                mc.mouseHandler.getScaledYPos(window)
            )
            //?}
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