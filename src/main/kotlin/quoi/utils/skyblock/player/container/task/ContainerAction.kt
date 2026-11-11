package quoi.utils.skyblock.player.container.task

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import quoi.annotations.Internal
import quoi.api.events.ContainerEvent
import quoi.api.events.TickEvent
import quoi.api.events.core.await
import quoi.api.events.core.wait
import quoi.utils.gameMode
import quoi.utils.player
import quoi.utils.skyblock.player.container.ContainerUtils
import quoi.utils.skyblock.player.container.task.ContainerActionResult.FAILURE
import quoi.utils.skyblock.player.container.task.ContainerActionResult.SUCCESS

@Internal
enum class ContainerActionResult { SUCCESS, FAILURE }

/**
 * An action that interacts with items or slots
 */
abstract class ItemAction : ContainerAction {
    var skipIf: ((ItemStack) -> Boolean)? = null
    var condition: (() -> Boolean)? = null
}

@OptIn(Internal::class)
interface ContainerAction {
    val failureReason: String get() = "Container action failed"

    /**
     * Executes the action
     * @return [SUCCESS] to continue, [FAILURE] to abort the entire task
     */
    suspend fun ContainerManager.execute(): ContainerActionResult

    /**
     * Clicks on a specific slot or searches for an item to click
     */
    class Click(
        val target: MenuSlot,
        private val button: Int,
        private val input: ContainerInput,
        private val timeout: Int = 20,
        override val failureReason: String = "Timed out finding item",
    ) : ItemAction() {
        override suspend fun ContainerManager.execute(): ContainerActionResult {
            val task = activeTask ?: return FAILURE
            if (condition?.invoke() == false) {
                task.skippedLast = true
                return SUCCESS
            }
            val menu = player.containerMenu
            var slot = target.resolve(menu, player.inventory)
            // check immediately, then wait for the item if it hasn't arrived yet
            if (slot == null && target is ItemSlot) {
                await<TickEvent.Start>(timeout = timeout, serverTicks = true) {
                    slot = target.resolve(menu, player.inventory)
                    player.containerMenu !== menu || slot != null
                }
            }
            if (player.containerMenu !== menu) return FAILURE
            val resolved = slot ?: return FAILURE
            val skipped = skipIf?.invoke(resolved.item) == true
            task.skippedLast = skipped
            if (!skipped) {
                gameMode.handleContainerInput(menu.containerId, resolved.index, button, input, player)
                task.ticksSinceLastClick = 0
            }
            return SUCCESS
        }
    }

    /**
     * Executes custom block of code
     */
    class Other(private val block: () -> Unit) : ContainerAction {
        override suspend fun ContainerManager.execute(): ContainerActionResult {
            activeTask?.skippedLast = false
            block()
            return SUCCESS
        }
    }

    class Check(override val failureReason: String, private val predicate: () -> Boolean) : ContainerAction {
        override suspend fun ContainerManager.execute(): ContainerActionResult = if (predicate()) SUCCESS else FAILURE
    }

    /**
     * Closes the task's container without clearing the previous click's skip state
     */
    class Close : ContainerAction {
        override suspend fun ContainerManager.execute(): ContainerActionResult {
            activeTask?.let { closeOwnedMenu(it) }
            return SUCCESS
        }
    }

    /**
     * Registers a container reopen catch without waiting for the container
     */
    class CatchContainer(private val containerName: Regex, private val timeout: Int) : ContainerAction {
        init {
            require(timeout >= 0) { "Container catch timeout must not be negative" }
        }

        override suspend fun ContainerManager.execute(): ContainerActionResult {
            val task = activeTask ?: return FAILURE
            val skipped = task.skippedLast
            task.skippedLast = false
            if (!skipped) ContainerUtils.catchContainer(task, containerName, timeout)
            return SUCCESS
        }
    }

    /**
     * Suspends execution for [ticks]
     */
    class Wait(private val ticks: Int) : ContainerAction {
        override suspend fun ContainerManager.execute(): ContainerActionResult {
            wait(ticks)
            return SUCCESS
        }
    }

    /**
     * Waits for a specific container to open and optionally for the items to populate
     */
    class AwaitContainer(
        val containerName: Regex,
        private val timeout: Int,
        private val waitForItems: Boolean,
        failureMessage: String? = null,
    ) : ContainerAction {
        override var failureReason = failureMessage ?: "Timed out waiting for container matching ${containerName.pattern}"
            private set

        override suspend fun ContainerManager.execute(): ContainerActionResult {
            val task = activeTask ?: return FAILURE
            // a skipped click won't trigger a reopen, so skip this wait too
            val skipped = task.skippedLast
            task.skippedLast = false
            if (skipped) return SUCCESS

            // the container may have opened while another action was waiting
            val open = ContainerUtils.current?.takeIf { it.revision > task.containerRevision }
                ?: await<ContainerEvent.Open>(timeout = timeout, serverTicks = true)?.container
                ?: return FAILURE
            task.containerRevision = open.revision
            if (!containerName.containsMatchIn(open.title)) {
                failureReason = "Wrong container name. Got ${open.title}, needed ${containerName.pattern}"
                return FAILURE
            }
            task.ownedMenu = open.menu
            if (player.containerMenu !== open.menu) return FAILURE
            if (!waitForItems || open.itemsLoaded) return SUCCESS

            failureReason = "Timed out waiting for items in ${open.title}"
            return coroutineScope {
                val items = async(start = CoroutineStart.UNDISPATCHED) {
                    await<ContainerEvent.Items>(timeout = timeout, serverTicks = true) { container === open }
                }
                val changed = async(start = CoroutineStart.UNDISPATCHED) {
                    await<TickEvent.Start> { player.containerMenu !== open.menu }
                }
                try {
                    val loaded = select {
                        items.onAwait { it != null }
                        changed.onAwait { false }
                    }
                    if (player.containerMenu !== open.menu) {
                        failureReason = "Container changed while waiting for items in ${open.title}"
                        FAILURE
                    } else if (loaded) SUCCESS else FAILURE
                } finally {
                    items.cancel()
                    changed.cancel()
                }
            }
        }
    }
}