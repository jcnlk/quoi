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

    fun run(): ContainerTask = ContainerManager.execute(this)

    suspend fun await(): ContainerTaskResult = completion.await()

    fun cancel() = ContainerManager.cancel(this)

    internal fun beginFastBlock(): Boolean {
        if (!fastMode || fastBlockFinished) return false
        fastBlockActive = true
        return true
    }

    internal fun finishFastBlockAfterClick(): Boolean {
        if (!fastMode || !fastBlockActive) return false
        if (fastClicksRemaining > 0) fastClicksRemaining--
        if (fastClicksRemaining > 0) return false

        return finishFastBlock()
    }

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
    ) = click(slot, button, ContainerInput.PICKUP, timeout, failureMessage)

    fun pickupAll(slot: MenuSlot) = click(slot, 0, ContainerInput.PICKUP_ALL)

    fun throwOne(slot: MenuSlot) = click(slot, 0, ContainerInput.THROW)
    fun throwAll(slot: MenuSlot) = click(slot, 1, ContainerInput.THROW)

    fun quickMove(
        target: MenuSlot,
        timeout: Int = 20,
        failureMessage: String = "Timed out",
    ) = click(target, 0, ContainerInput.QUICK_MOVE, timeout, failureMessage)
    fun swap(target: MenuSlot, hotbarSlot: Int) = click(target, hotbarSlot, ContainerInput.SWAP)

    fun moveSlot(from: MenuSlot, to: MenuSlot, button: Int = 0) {
        pickup(from, button)
        pickup(to, button)
    }

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

    fun action(block: () -> Unit) = actions.add(ContainerAction.Other(block))

    fun afterClick(block: () -> Unit) = actions.add(ContainerAction.AfterClick(block))

    fun check(failureMessage: String, predicate: () -> Boolean) =
        actions.add(ContainerAction.Check(failureMessage, predicate))

    fun wait(ticks: Int) = actions.add(ContainerAction.Wait(ticks))

    fun onComplete(callback: () -> Unit) {
        onComplete = callback
    }

    fun onFinished(callback: (ContainerTaskResult) -> Unit) {
        onFinished = callback
    }

    fun <T : ContainerAction> T.unless(block: (ItemStack) -> Boolean): T {
        skipIf = block
        return this
    }

    fun <T : ContainerAction> T.unlessName(text: String): T = unless { it.displayName.string.contains(text) }

    fun <T : ContainerAction> T.unlessLore(text: String): T = unless { it.loreString?.contains(text) == true }
}

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
