package quoi.utils.skyblock.player.container.task

import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import quoi.api.events.ContainerEvent
import quoi.api.events.TickEvent
import quoi.api.events.core.await
import quoi.api.events.core.wait
import quoi.utils.gameMode
import quoi.utils.player
import quoi.utils.skyblock.player.container.ContainerUtils

/**
 * An action that interacts with items or slots
 */
abstract class ItemAction : ContainerAction {
    var skipIf: ((ItemStack) -> Boolean)? = null
}

interface ContainerAction {
    val failureReason: String get() = "Container action failed"

    /**
     * Executes the action
     * @return `true` if the action succeeded, `false` to abort the entire task
     */
    suspend fun ContainerManager.execute(): Boolean

    /**
     * Clicks on a specific slot or searches for an item to click
     */
    class Click(
        val target: MenuSlot,
        val button: Int,
        val input: ContainerInput,
        val timeout: Int = 20,
        override val failureReason: String = "Timed out finding item",
    ) : ItemAction() {
        override suspend fun ContainerManager.execute(): Boolean {
            val task = activeTask ?: return false
            val menu = player.containerMenu
            var slot = target.resolve(menu, player.inventory)
            // check immediately, then wait for the item if it hasn't arrived yet
            if (slot == null && target is ItemSlot) {
                await<TickEvent.Start>(timeout = timeout) {
                    slot = target.resolve(menu, player.inventory)
                    player.containerMenu !== menu || slot != null
                }
            }
            if (player.containerMenu !== menu) return false
            val resolved = slot ?: return false
            val skipped = skipIf?.invoke(resolved.item) == true
            task.skippedLast = skipped
            if (!skipped) {
                gameMode.handleContainerInput(menu.containerId, resolved.index, button, input, player)
                task.ticksSinceLastClick = 0
            }
            task.finishClick()
            restoreMovementIfAllowed()
            return true
        }
    }

    /**
     * Executes custom block of code
     */
    class Other(val block: () -> Unit) : ContainerAction {
        override suspend fun ContainerManager.execute(): Boolean {
            activeTask?.skippedLast = false
            block()
            return true
        }
    }

    class Check(override val failureReason: String, val predicate: () -> Boolean) : ContainerAction {
        override suspend fun ContainerManager.execute(): Boolean = predicate()
    }

    /**
     * Closes the task's container without clearing the previous click's skip state
     */
    class Close : ContainerAction {
        override suspend fun ContainerManager.execute(): Boolean {
            activeTask?.let { closeOwnedMenu(it) }
            return true
        }
    }

    /**
     * Suspends execution for [ticks]
     */
    class Wait(val ticks: Int) : ContainerAction {
        override suspend fun ContainerManager.execute(): Boolean {
            wait(ticks)
            return true
        }
    }

    /**
     * Waits for a specific container to open and optionally for the items to populate
     */
    class AwaitContainer(
        val containerName: Regex,
        val timeout: Int,
        val waitForItems: Boolean,
    ) : ContainerAction {
        override var failureReason = "Timed out waiting for container matching ${containerName.pattern}"
            private set

        override suspend fun ContainerManager.execute(): Boolean {
            val task = activeTask ?: return false
            // a skipped click won't trigger a reopen, so skip this wait too
            val skipped = task.skippedLast
            task.skippedLast = false
            if (skipped) return true

            // the container may have opened while another action was waiting
            val open = ContainerUtils.current?.takeIf { it.revision > task.containerRevision }
                ?: await<ContainerEvent.Open>(timeout = timeout)?.container
                ?: return false
            task.containerRevision = open.revision
            if (!containerName.containsMatchIn(open.title)) {
                failureReason = "Wrong container name. Got ${open.title}, needed ${containerName.pattern}"
                return false
            }
            task.ownedMenu = open.menu
            task.beginFastBlock()
            if (player.containerMenu !== open.menu) return false
            if (!waitForItems || open.itemsLoaded) return true

            failureReason = "Timed out waiting for items in ${open.title}"
            return await<ContainerEvent.Items>(timeout = timeout) {
                container === open
            } != null && player.containerMenu === open.menu
        }
    }
}