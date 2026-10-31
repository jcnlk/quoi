package quoi.utils.skyblock.player.container.task

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import quoi.QuoiMod.logger
import quoi.QuoiMod.mc
import quoi.annotations.Internal
import quoi.utils.ChatUtils
import quoi.utils.skyblock.item.ItemUtils.loreString
import quoi.utils.skyblock.player.container.CONTAINER_ZERO
import quoi.utils.skyblock.player.container.IContainerSettings
import kotlin.coroutines.coroutineContext

@DslMarker
private annotation class TaskDsl

sealed interface ContainerTaskResult {
    data object Success : ContainerTaskResult
    data object Busy : ContainerTaskResult
    data object Cancelled : ContainerTaskResult
    data class Failure(val message: String) : ContainerTaskResult
}

/**
 * Represents a sequence of [ContainerAction]s to be executed in [ContainerManager]
 * Each task can only be run once
 */
@OptIn(Internal::class)
class ContainerTask(
    val name: String?,
    val actions: List<ContainerAction>,
    val force: Boolean,
    val settings: IContainerSettings,
    private val onComplete: (() -> Unit)?,
    private val onFinished: ((ContainerTaskResult) -> Unit)? = null,
) {
    private val completion = CompletableDeferred<ContainerTaskResult>()
    var result: ContainerTaskResult? = null
        private set
    val completed: Boolean get() = result != null
    val totalActions = actions.size
    var completedActions = 0
        internal set
    internal var skippedLast = false
    internal var ticksSinceLastClick = Int.MAX_VALUE
    internal var containerRevision = 0L
    internal var ownedMenu: AbstractContainerMenu? = null
    internal var cleanupAllowed = true
    internal var cancellationRequested = false
    internal var silentInventoryOpen = false

    private val blocksForMenu: Boolean
        get() = !completed && settings.silent && mc.screen == null &&
                (silentInventoryOpen ||
                        mc.player?.let { it.containerMenu !== it.inventoryMenu } == true)

    val stopsMovement: Boolean
        get() = !settings.invWalk && blocksForMenu

    internal val blocksInput: Boolean
        get() = settings.blockInput && blocksForMenu

    /**
     * Submits this task to the [ContainerManager] for execution on the client thread
     */
    fun run(): ContainerTask = ContainerManager.execute(this)

    /**
     * Submits this task on the client thread if needed and waits for its result
     * Cancelling the caller also cancels the task
     */
    suspend fun await(): ContainerTaskResult = try {
        if (!completed) {
            coroutineContext.ensureActive()
            if (mc.isSameThread) run()
            else withContext(mc.asCoroutineDispatcher()) { run() }
        }
        completion.await()
    } catch (e: CancellationException) {
        cancel()
        throw e
    }

    /**
     * Cancels the task and any pending waits
     */
    fun cancel() = ContainerManager.cancel(this)

    /**
     * Completes the task once and resumes callers waiting in [await]
     */
    @Internal
    internal fun finish(result: ContainerTaskResult) {
        if (this.result != null) return
        this.result = result
        try {
            if (result == ContainerTaskResult.Success) onComplete?.invoke()
        } catch (e: Exception) {
            logger.error("Container completion callback failed", e)
        } finally {
            try {
                onFinished?.invoke(result)
            } catch (e: Exception) {
                logger.error("Container result callback failed", e)
            } finally {
                completion.complete(result)
            }
        }
    }
}

@TaskDsl
class ContainerTaskBuilder(val force: Boolean) {
    val actions = mutableListOf<ContainerAction>()
    var onComplete: (() -> Unit)? = null
    var onFinished: ((ContainerTaskResult) -> Unit)? = null

    private fun click(slot: MenuSlot, button: Int, input: ContainerInput, timeout: Int = 20, failureMessage: String = "Timed out finding item"): ItemAction {
        val action = ContainerAction.Click(slot, button, input, timeout, failureMessage)
        actions.add(action)
        return action
    }

    fun pickup(slot: MenuSlot, button: Int = 0, timeout: Int = 20, failureMessage: String = "Timed out finding item") =
        click(slot, button, ContainerInput.PICKUP, timeout, failureMessage) // right/left click

    fun pickupAll(slot: MenuSlot) = click(slot, 0, ContainerInput.PICKUP_ALL) // double click
    fun throwOne(slot: MenuSlot) = click(slot, 0, ContainerInput.THROW) // q
    fun throwAll(slot: MenuSlot) = click(slot, 1, ContainerInput.THROW) // ctrl + q

    fun quickMove(target: MenuSlot) = click(target, 0, ContainerInput.QUICK_MOVE) // shift click
    fun swap(target: MenuSlot, hotbarSlot: Int) = click(target, hotbarSlot, ContainerInput.SWAP) // keys 1 to 9

    /**
     * Picks up [from], then clicks [to]. An occupied destination may leave an item on the cursor
     */
    fun moveSlot(from: MenuSlot, to: MenuSlot, button: Int = 0) {
        pickup(from, button)
        pickup(to, button)
    }

    /**
     * Awaits for container to open before proceeding
     * @param name container name to wait for
     * @param waitForItems if `true`, waits for items to fill the container
     * @param timeout time to wait for the container to open (server ticks)
     * @param failureMessage optional message if the container does not open in time
     */
    fun awaitContainer(
        name: Regex,
        waitForItems: Boolean = false,
        timeout: Int = 20,
        failureMessage: String? = null,
    ) = actions.add(ContainerAction.AwaitContainer(name, timeout, waitForItems, failureMessage))

    fun awaitContainer(
        name: String,
        waitForItems: Boolean = false,
        timeout: Int = 20,
        failureMessage: String? = null,
    ) = awaitContainer(Regex(Regex.escape(name), RegexOption.IGNORE_CASE), waitForItems, timeout, failureMessage)

    /**
     * Applies [awaitContainer] before each action.
     * Good for actions that will trigger container reopen (pagination, wardrobe swap, etc).
     */
    fun awaitingContainer(
        name: String,
        waitForItems: Boolean = false,
        timeout: Int = 20,
        block: ContainerTaskBuilder.() -> Unit
    ) = awaitingContainer(Regex(Regex.escape(name), RegexOption.IGNORE_CASE), waitForItems, timeout, block)

    fun awaitingContainer(
        name: Regex,
        waitForItems: Boolean = false,
        timeout: Int = 20,
        block: ContainerTaskBuilder.() -> Unit
    ) {
        val nested = ContainerTaskBuilder(force).apply { block() }
        nested.actions.forEach {
            actions.add(ContainerAction.AwaitContainer(name, timeout, waitForItems))
            actions.add(it)
        }
    }

    /**
     * Opens a container with [command] and waits for it using [awaitContainer]
     */
    fun openContainer(command: String, name: String, waitForItems: Boolean = true, timeout: Int = 20) {
        openContainer(command, Regex(Regex.escape(name), RegexOption.IGNORE_CASE), waitForItems, timeout)
    }

    fun openContainer(command: String, name: Regex, waitForItems: Boolean = true, timeout: Int = 20) {
        action { ChatUtils.command(command) }
        awaitContainer(name, waitForItems, timeout)
    }

    /**
     * Suppresses the next matching container reopen without waiting for it
     * A missing reopen does not fail the task
     *
     * Call after the triggering click, before the task closes its menu. Skipped clicks are ignored
     * Catches remain after success and are cleared on failure, cancellation, a new task or world change
     *
     * @param name container name to catch
     * @param timeout time to keep the catch active (server ticks). 0 disables the catch, must not be negative
     */
    fun catchContainer(name: Regex, timeout: Int = 20) =
        actions.add(ContainerAction.CatchContainer(name, timeout))

    fun catchContainer(name: String, timeout: Int = 20) =
        catchContainer(Regex(Regex.escape(name), RegexOption.IGNORE_CASE), timeout)

    fun closeContainer() = actions.add(ContainerAction.Close()) // only closes the task's container

    /**
     * Aborts the task with [failureMessage] if [predicate] is false
     */
    fun check(failureMessage: String, predicate: () -> Boolean) =
        actions.add(ContainerAction.Check(failureMessage, predicate))

    /**
     * Called on success, failure, cancellation or if another task is already running
     */
    fun onFinished(callback: (ContainerTaskResult) -> Unit) {
        onFinished = callback
    }

    fun action(block: () -> Unit) = actions.add(ContainerAction.Other(block)) // custom action

    fun wait(ticks: Int) = actions.add(ContainerAction.Wait(ticks)) // wait N ticks

    fun onComplete(callback: () -> Unit) { // cb on successful task finish
        onComplete = callback
    }

    /**
     * skips the action if the [block] is `true` for the item in the target slot.
     */
    fun <T : ItemAction> T.unless(block: (ItemStack) -> Boolean): T {
        skipIf = block
        return this
    }

    fun <T : ItemAction> T.unlessName(text: String): T = unless { it.displayName.string.contains(text) }

    fun <T : ItemAction> T.unlessLore(text: String): T = unless { it.loreString?.contains(text) == true }
}

/**
 * @param name optional task name. if not null it will be rendered in the middle of the screen
 * @param force if `true`, bypasses [IContainerSettings.clickDelay] delay
 * @param settings settings shared with the caller, or [CONTAINER_ZERO] for tasks without a settings group
 */
fun containerTask(
    name: String? = null,
    force: Boolean = false,
    settings: IContainerSettings = CONTAINER_ZERO,
    builder: ContainerTaskBuilder.() -> Unit
): ContainerTask = ContainerTaskBuilder(force).apply(builder).run {
    ContainerTask(name, actions.toList(), force, settings, onComplete, onFinished)
}