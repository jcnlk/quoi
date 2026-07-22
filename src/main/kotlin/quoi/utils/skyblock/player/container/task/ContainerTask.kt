package quoi.utils.skyblock.player.container.task

import kotlinx.coroutines.CompletableDeferred
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import quoi.utils.skyblock.item.ItemUtils.loreString

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
 */
class ContainerTask(
    val name: String?,
    val actions: List<ContainerAction>,
    val force: Boolean,
    val onComplete: (() -> Unit)?,
    val preventMovement: Boolean = true,
    val blockInput: Boolean = true,
    val fastMode: Boolean = false,
    val showProgress: Boolean = true,
    private val onFinished: ((ContainerTaskResult) -> Unit)? = null,
) {
    private val completion = CompletableDeferred<ContainerTaskResult>()

    var pending = true
    var completed = false

    val totalActions = actions.size
    var completedActions = 0
    var skippedLast = false
    var ticksSinceLastClick = 0
    var actionsThisTick = 0

    var awaiting: ContainerAction? = null

    var queue = ArrayDeque(actions)

    var result: ContainerTaskResult? = null
        private set

    internal var fastBlockActive = false
        private set
    private var fastBlockFinished = false
    private var fastClicksRemaining = actions.count {
        it is ContainerAction.Click || it is ContainerAction.DynamicClick
    }

    /**
     * Submits this task to the [ContainerManager] for execution
     */
    fun run(): ContainerTask = ContainerManager.execute(this)

    /** Waits for the terminal result of a task submitted with [run]. */
    suspend fun await(): ContainerTaskResult = completion.await()

    /** Cancels this task when it is currently managed by [ContainerManager]. */
    fun cancel() = ContainerManager.cancel(this)

    /** Starts the one-shot input block used by [fastMode]. */
    internal fun beginFastBlock(): Boolean {
        if (!fastMode || fastBlockFinished) return false
        fastBlockActive = true
        return true
    }

    /** Ends the fast block after the last planned click. */
    internal fun finishFastBlockAfterClick(): Boolean {
        if (!fastMode || !fastBlockActive) return false
        if (fastClicksRemaining > 0) fastClicksRemaining--
        if (fastClicksRemaining > 0) return false

        return finishFastBlock()
    }

    /** Ends the fast block permanently so a later container reopen cannot re-arm it. */
    internal fun finishFastBlock(): Boolean {
        if (!fastMode || !fastBlockActive) return false
        fastBlockActive = false
        fastBlockFinished = true
        return true
    }

    internal fun finish(result: ContainerTaskResult) {
        if (this.result != null) return

        this.result = result
        onFinished?.invoke(result)
        completion.complete(result)
    }
}

@TaskDsl
class ContainerTaskBuilder(val force: Boolean) {
    val actions = mutableListOf<ContainerAction>()
    var onComplete: (() -> Unit)? = null
    var onFinished: ((ContainerTaskResult) -> Unit)? = null

    private fun click(
        slot: MenuSlot,
        button: Int,
        input: ContainerInput,
        timeout: Int = 20,
        failureMessage: String = "Timed out",
    ): ContainerAction {
        val action = when (slot) {
            is IndexSlot -> ContainerAction.Click(slot.index, button, input, slot.inContainer)
            is ItemSlot -> ContainerAction.DynamicClick(
                slot.predicate,
                button,
                input,
                slot.inContainer,
                timeout,
                failureMessage,
            )
        }
        actions.add(action)
        return action
    }

    fun pickup(
        slot: MenuSlot,
        button: Int = 0,
        timeout: Int = 20,
        failureMessage: String = "Timed out",
    ) = click(slot, button, ContainerInput.PICKUP, timeout, failureMessage) // right/left click

    fun pickupAll(slot: MenuSlot) = click(slot, 0, ContainerInput.PICKUP_ALL) // double click

    fun throwOne(slot: MenuSlot) = click(slot, 0, ContainerInput.THROW) // q
    fun throwAll(slot: MenuSlot) = click(slot, 1, ContainerInput.THROW) // ctrl + q

    fun quickMove(target: MenuSlot) = click(target, 0, ContainerInput.QUICK_MOVE) // shift click
    fun swap(target: MenuSlot, hotbarSlot: Int) = click(target, hotbarSlot, ContainerInput.SWAP) // keys 1 to 9

    fun moveSlot(from: MenuSlot, to: MenuSlot, button: Int = 0) { // move from one slot to another
        pickup(from, button)
        pickup(to, button)
    }

    /**
     * Awaits for container to open before proceeding
     * @param name container name to wait for
     * @param waitForItems if `true`, waits for items to fill the container
     * @param timeout time to wait for the container to open (client ticks)
     */
    fun awaitContainer(
        name: Regex,
        waitForItems: Boolean = false,
        timeout: Int = 20
    ) = actions.add(ContainerAction.AwaitContainer(name, timeout, waitForItems))

    fun awaitContainer(
        name: String,
        waitForItems: Boolean = false,
        timeout: Int = 20
    ) = awaitContainer(Regex(Regex.escape(name), RegexOption.IGNORE_CASE), waitForItems, timeout)

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

    fun action(block: () -> Unit) = actions.add(ContainerAction.Other(block)) // custom action

    /** Runs after a click and keeps its skip state available to the next await action. */
    fun afterClick(block: () -> Unit) = actions.add(ContainerAction.AfterClick(block))

    fun check(failureMessage: String, predicate: () -> Boolean) =
        actions.add(ContainerAction.Check(failureMessage, predicate))

    fun wait(ticks: Int) = actions.add(ContainerAction.Wait(ticks)) // wait N ticks

    fun onComplete(callback: () -> Unit) { // cb on task finish
        onComplete = callback
    }

    /** Invoked for success, failure, cancellation, and a busy manager. */
    fun onFinished(callback: (ContainerTaskResult) -> Unit) {
        onFinished = callback
    }

    /**
     * skips the action if the [block] is `true` for the item in the target slot.
     */
    fun <T : ContainerAction> T.unless(block: (ItemStack) -> Boolean): T {
        skipIf = block
        return this
    }

    /**
     * skips the action if the item's name contains [text]
     */
    fun <T : ContainerAction> T.unlessName(text: String): T = unless { it.displayName.string.contains(text) }

    /**
     * skips the action if the item's lore contains [text]
     */
    fun <T : ContainerAction> T.unlessLore(text: String): T = unless { it.loreString?.contains(text) == true }
}

/**
 * @param force if btrue`, bypasses 1 action per tick limit
 * @param fastMode limits movement and input blocking to the first matching container opening through the final planned click
 */
@TaskDsl
fun containerTask(
    name: String? = null,
    force: Boolean = false,
    preventMovement: Boolean = true,
    blockInput: Boolean = true,
    fastMode: Boolean = false,
    showProgress: Boolean = true,
    builder: ContainerTaskBuilder.() -> Unit
): ContainerTask = ContainerTaskBuilder(force).apply(builder).run {
    ContainerTask(
        name,
        actions,
        force,
        onComplete,
        preventMovement,
        blockInput,
        fastMode,
        showProgress,
        onFinished,
    )
}