package quoi.utils.skyblock.player.container.task

import net.minecraft.client.KeyMapping
import quoi.QuoiMod.mc
import quoi.annotations.Init
import quoi.api.events.KeyEvent
import quoi.api.events.MouseEvent
import quoi.api.events.RenderEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.EventListener
import quoi.api.events.core.Priority
import quoi.api.events.core.on
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.StringUtils.width
import quoi.utils.player
import quoi.utils.render.DrawContextUtils.drawText
import quoi.utils.scaledHeight
import quoi.utils.scaledWidth
import quoi.utils.skyblock.player.MovementUtils.hasMovementInput
import quoi.utils.skyblock.player.MovementUtils.stop

/**
 * Manages execution of [ContainerTask]s
 */
@Init
object ContainerManager : EventListener { // todo toggleable invwalk for container clicks, clean up
    var activeTask: ContainerTask? = null
        private set

    private var startDelay = 0 // 2 to not get limboed
    private var endDelay = 0 // 3 to not get limboed
    private var preventMovementDuringEndDelay = false
    private var blockInputDuringEndDelay = false
    private var movementKeysSuppressed = false

    val active: Boolean
        get() = activeTask != null || endDelay > 0

    init {
        on<TickEvent.Start>(priority = Priority.HIGHEST) {
            val preventMovement = activeTask?.shouldPreventMovement
                ?: (endDelay > 0 && preventMovementDuringEndDelay)
            if (preventMovement) {
                suppressMovementKeys()
            }

            if (startDelay > 0) {
                startDelay--
                return@on
            }

            if (endDelay > 0) {
                endDelay--
                if (endDelay == 0) {
                    preventMovementDuringEndDelay = false
                    blockInputDuringEndDelay = false
                    restoreMovementKeys()
                }
                return@on
            }

            activeTask?.let { doTask(it) }
        }

        on<TickEvent.End>(priority = Priority.LOWEST) {
            activeTask?.actionsThisTick = 0
        }

        on<WorldEvent.Change> {
            val task = activeTask
            activeTask = null
            startDelay = 0
            endDelay = 0
            preventMovementDuringEndDelay = false
            blockInputDuringEndDelay = false
            restoreMovementKeys()
            task?.awaiting?.cancel()
            task?.finish(ContainerTaskResult.Cancelled)
        }

        on<KeyEvent.Press> { if (shouldBlockInput) cancel() }
        on<KeyEvent.Release> { if (shouldBlockInput) cancel() }
        on<MouseEvent.Click> { if (shouldBlockInput) cancel() }
        on<MouseEvent.Scroll> { if (shouldBlockInput) cancel() }
        on<MouseEvent.Move> { if (shouldBlockInput) cancel() }

        on<RenderEvent.Overlay> {
            val task = activeTask ?: return@on
            if (!task.showProgress || task.totalActions <= 0) return@on

            val progress = task.completedActions.toFloat() / task.totalActions
            val filled = (progress * 10).toInt().coerceIn(0, 10)
            val empty = 10 - filled

            val bar = "[&a${"█".repeat(filled)}&7${"░".repeat(empty)}&r]"

            val x = scaledWidth / 2f - bar.noControlCodes.width() / 2f
            var y = scaledHeight / 2f + 10f

            if (!task.name.isNullOrBlank()) {
                val x = scaledWidth / 2f - task.name.noControlCodes.width() / 2f
                ctx.drawText(task.name, x, y)
                y += 11f
            }

            ctx.drawText(bar, x, y)
        }
    }

    fun execute(task: ContainerTask): ContainerTask {
        if (activeTask != null && activeTask !== task) {
            task.finish(ContainerTaskResult.Busy)
            return task
        }

        val first = task.actions.firstOrNull()
        val f = first is ContainerAction.Click || first is ContainerAction.DynamicClick
        if (task.shouldPreventMovement && player.hasMovementInput && f) { // only apply if holding movement keys and first action is click
            suppressMovementKeys()
            startDelay = 2
        }
        endDelay = 0
        preventMovementDuringEndDelay = false
        blockInputDuringEndDelay = false
        if (!task.shouldPreventMovement) restoreMovementKeys()

        task.queue = ArrayDeque(task.actions)
        activeTask = task

        return task
    }

    fun cancel(task: ContainerTask) {
        if (activeTask !== task) return

        task.awaiting?.cancel()
        activeTask = null
        startDelay = 0
        endDelay = 0
        preventMovementDuringEndDelay = false
        blockInputDuringEndDelay = false
        restoreMovementKeys()
        task.finish(ContainerTaskResult.Cancelled)
    }

    /** Starts Fast Mode's one-shot block after the first matching container opens. */
    internal fun beginFastBlock() {
        activeTask?.beginFastBlock()
    }

    /** Releases Fast Mode immediately after its final target click is handled. */
    internal fun finishFastBlockAfterClick() {
        val task = activeTask ?: return
        if (task.finishFastBlockAfterClick()) restoreMovementKeys()
    }

    private fun doTask(task: ContainerTask) {
        if (task.pending) {
            activeTask = task
            task.queue = ArrayDeque(task.actions)
            task.pending = false
        }

        doActions()
    }

    private fun doActions() {
        val active = activeTask ?: return

        active.awaiting?.let {
            if (it.execute()) {
                active.completedActions++
                if (it.abort) {
                    activeTask = null
                    beginEndDelay(active)
                    active.finish(ContainerTaskResult.Failure(it.failureReason ?: "Container action failed"))
                    return
                }
                active.awaiting = null
            }
            else return
        }

        val iterator = active.queue.iterator()

        while (iterator.hasNext()) {
            if (active.actionsThisTick >= 1 && !active.force) break

            val action = iterator.next()
            if (action.execute()) {
                iterator.remove()
                active.actionsThisTick++
                active.completedActions++

                if (action.abort) {
                    activeTask = null
                    beginEndDelay(active)
                    active.finish(ContainerTaskResult.Failure(action.failureReason ?: "Container action failed"))
                    return
                }
            } else {
                active.awaiting = action
                active.actionsThisTick++
                break
            }
        }

        if (active.queue.isEmpty() && active.awaiting == null) {
            active.completed = true
            active.onComplete?.invoke()
            activeTask = null
            beginEndDelay(active)
            active.finish(ContainerTaskResult.Success)
        }
    }

    private fun suppressMovementKeys() {
        player.stop()
        movementKeysSuppressed = true
    }

    private fun beginEndDelay(task: ContainerTask) {
        if (task.finishFastBlock()) restoreMovementKeys()

        endDelay = maxOf(0, 3 - task.ticksSinceLastClick)
        preventMovementDuringEndDelay = !task.fastMode && task.preventMovement && endDelay > 0
        blockInputDuringEndDelay = !task.fastMode && task.blockInput && endDelay > 0
        if (endDelay == 0) restoreMovementKeys()
    }

    private val shouldBlockInput: Boolean
        get() = activeTask?.shouldBlockInput ?: (endDelay > 0 && blockInputDuringEndDelay)

    private val ContainerTask.shouldPreventMovement: Boolean
        get() = preventMovement && (!fastMode || fastBlockActive)

    private val ContainerTask.shouldBlockInput: Boolean
        get() = blockInput && (!fastMode || fastBlockActive)

    /** Re-reads physically held keys after the final movement-suppression tick. */
    private fun restoreMovementKeys() {
        if (!movementKeysSuppressed) return
        movementKeysSuppressed = false

        if (mc.isSameThread) KeyMapping.setAll()
        else mc.execute(KeyMapping::setAll)
    }
}