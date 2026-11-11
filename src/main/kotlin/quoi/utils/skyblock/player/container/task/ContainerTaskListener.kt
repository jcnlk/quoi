package quoi.utils.skyblock.player.container.task

import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import quoi.QuoiMod.mc
import quoi.annotations.Internal
import quoi.api.events.ContainerEvent
import quoi.api.events.PacketEvent
import quoi.api.events.TickEvent
import quoi.api.events.core.EventListener
import quoi.api.events.core.Priority
import quoi.api.events.core.on
import quoi.utils.skyblock.player.container.CONTAINER_ZERO
import quoi.utils.skyblock.player.container.IContainerSettings

/** Starts a fresh task for matching opening packets while [owner] is active. */
@OptIn(Internal::class)
class ContainerTaskListener internal constructor(
    private val owner: EventListener,
    private val menu: (ClientboundOpenScreenPacket) -> Boolean,
    private val name: String?,
    private val force: Boolean,
    private val settings: IContainerSettings,
    private val reportErrors: Boolean,
    private val builder: ContainerTaskBuilder.() -> Unit,
) : EventListener {
    private var task: ContainerTask? = null
    private val subscriptions = listOf(
        // Run before ContainerUtils handles silent menus, so it sees the normal AwaitContainer action.
        on<PacketEvent.Received, ClientboundOpenScreenPacket>(Priority.LOWEST) {
            if (!owner.running || !owner.shouldHandle(event) || ContainerManager.active || !menu(packet)) return@on
            val next = containerTask(name = name, force = force, settings = settings, reportErrors = reportErrors) {
                builder()
                if (actions.isEmpty()) return@containerTask
                actions.add(0, ContainerAction.AwaitContainer(Regex("^${Regex.escape(packet.title.string)}$"), 20, true))
                val callback = onFinished
                onFinished { result ->
                    task = null
                    callback?.invoke(result)
                }
            }
            if (next.actions.isEmpty()) return@on
            task = next
            next.run()
        },
        on<ContainerEvent.Open> {
            // Bind visible menus too, before startDelay can suspend the AwaitContainer action.
            task?.let { if (it.ownedMenu == null) it.ownedMenu = container.menu }
        },
        on<TickEvent.Start>(Priority.HIGHEST + 2) {
            val current = task ?: return@on
            if (!owner.running || !owner.shouldHandle(this) ||
                (current.ownedMenu != null && mc.player?.containerMenu !== current.ownedMenu)
            ) cancel()
        },
    )

    /** Cancels the current task. Listening resumes while the owner is active. */
    fun cancel() {
        task?.cancel()
    }

    fun unregister() {
        cancel()
        subscriptions.forEach { it.unregister() }
    }
}

/**
 * Listens for opening packets and submits a normal task before the screen is created.
 * [menu] matches packet metadata, such as the title or menu type. Empty tasks are ignored.
 * The task awaits the matching container and its items before executing the builder's actions.
 * Openings while another task owns the manager are left to that task.
 * @param reportErrors prints failures and busy rejections for each task created by this listener
 */
fun EventListener.containerTask(
    menu: (ClientboundOpenScreenPacket) -> Boolean,
    name: String? = null,
    force: Boolean = false,
    settings: IContainerSettings = CONTAINER_ZERO,
    reportErrors: Boolean = true,
    builder: ContainerTaskBuilder.() -> Unit,
): ContainerTaskListener = ContainerTaskListener(this, menu, name, force, settings, reportErrors, builder)
