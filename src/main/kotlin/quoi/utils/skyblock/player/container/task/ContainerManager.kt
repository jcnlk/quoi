package quoi.utils.skyblock.player.container.task

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import net.minecraft.client.KeyMapping
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import quoi.QuoiMod.logger
import quoi.QuoiMod.mc
import quoi.annotations.Init
import quoi.api.events.*
import quoi.api.events.core.EventListener
import quoi.api.events.core.Priority
import quoi.api.events.core.asyncScope
import quoi.api.events.core.on
import quoi.api.events.core.wait
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.StringUtils.width
import quoi.utils.render.DrawContextUtils.drawText
import quoi.utils.scaledHeight
import quoi.utils.scaledWidth
import quoi.utils.skyblock.player.MovementUtils.stop
import quoi.utils.skyblock.player.container.ContainerUtils
import kotlin.coroutines.coroutineContext

/**
 * Manages execution of [ContainerTask]s
 * Only one task can run at a time, including its end delay and cleanup
 */
@Init
object ContainerManager : EventListener {
    @Volatile
    var activeTask: ContainerTask? = null
        private set
    private var job: Job? = null
    private var movementKeysSuppressed = false
    private var openingInventory = false
    private var changingWorld = false

    val active: Boolean get() = activeTask != null

    init {
        on<TickEvent.Start>(Priority.HIGHEST + 1) {
            val task = activeTask ?: return@on
            if (task.stopsMovement) {
                mc.player?.stop()
                movementKeysSuppressed = true
            }
            if (task.ticksSinceLastClick < Int.MAX_VALUE) task.ticksSinceLastClick++
        }
        on<TickEvent.Start>(Priority.HIGHEST) {
            val task = activeTask ?: return@on
            if (job != null) return@on
            job = asyncScope.launch(start = CoroutineStart.LAZY) { runTask(task) }
            job?.invokeOnCompletion {
                // if cancelled before starting, runTask's finally block won't run
                if (!task.completed) finish(task, ContainerTaskResult.Cancelled)
            }
            job?.start()
        }
        on<WorldEvent.Change>(Priority.HIGHEST) {
            changingWorld = true
            try {
                activeTask?.let {
                    it.cleanupAllowed = false
                    cancel(it)
                }
            } finally {
                changingWorld = false
            }
        }
        on<KeyEvent.Press> { if (activeTask?.blocksInput == true) cancel() }
        on<KeyEvent.Release> { if (activeTask?.blocksInput == true) cancel() }
        on<MouseEvent.Click> { if (activeTask?.blocksInput == true) cancel() }
        on<MouseEvent.Scroll> { if (activeTask?.blocksInput == true) cancel() }
        on<MouseEvent.Move> { if (activeTask?.blocksInput == true) cancel() }
        on<RenderEvent.Overlay> {
            val task = activeTask ?: return@on
            if (!task.settings.showProgress || task.totalActions == 0) return@on
            val filled = (task.completedActions * 10 / task.totalActions).coerceIn(0, 10)
            val bar = "[&a${"█".repeat(filled)}&7${"░".repeat(10 - filled)}&r]"
            var y = scaledHeight / 2f + 10f
            task.name?.takeIf(String::isNotBlank)?.let {
                ctx.drawText(it, scaledWidth / 2f - it.noControlCodes.width() / 2f, y)
                y += 11f
            }
            ctx.drawText(bar, scaledWidth / 2f - bar.noControlCodes.width() / 2f, y)
        }
    }

    fun execute(task: ContainerTask): ContainerTask {
        check(mc.isSameThread) { "Container tasks must be submitted on the client thread" }
        if (task.completed || activeTask === task) return task
        if (activeTask != null) {
            task.finish(ContainerTaskResult.Busy)
            return task
        }
        if (changingWorld || mc.player == null) {
            task.finish(ContainerTaskResult.Cancelled)
            return task
        }
        task.containerRevision = ContainerUtils.revision
        activeTask = task
        return task
    }

    fun cancel(task: ContainerTask) {
        if (!mc.isSameThread) {
            mc.execute { cancel(task) }
            return
        }
        task.cancellationRequested = true
        if (activeTask !== task) {
            if (!task.completed) task.finish(ContainerTaskResult.Cancelled)
            return
        }
        val runningJob = job
        if (runningJob != null) runningJob.cancel()
        else finish(task, ContainerTaskResult.Cancelled)
    }

    private suspend fun runTask(task: ContainerTask) {
        var result: ContainerTaskResult = ContainerTaskResult.Success
        var inventoryScreen: InventoryScreen? = null
        val player = mc.player
        val previousScreen = mc.screen
        fun closeInventory() {
            if (inventoryScreen == null || mc.player !== player) return
            if (mc.screen === inventoryScreen ||
                (task.settings.silent && mc.screen === previousScreen && player?.containerMenu === player?.inventoryMenu)
            ) player?.closeContainer()
            inventoryScreen = null
        }
        try {
            checkNotNull(player) { "Player is unavailable" }
            wait(task.settings.startDelay.sample())
            for (action in task.actions) {
                coroutineContext.ensureActive()
                if (mc.player !== player) throw CancellationException("Player changed")
                // open inventory for inventory clicks if no container or inventory screen is open
                if (action is ContainerAction.Click && action.target.inContainer != true &&
                    player.containerMenu.containerId == 0 && inventoryScreen == null && mc.screen !is InventoryScreen
                ) {
                    inventoryScreen = InventoryScreen(player)
                    openingInventory = true
                    try {
                        mc.setScreen(inventoryScreen)
                    } finally {
                        openingInventory = false
                    }
                }
                // close inventory before waiting for an external container
                if (action is ContainerAction.AwaitContainer && inventoryScreen != null) {
                    closeInventory()
                }
                // subtract ticks already spent in other actions from the click delay
                if (!task.force && action is ItemAction) {
                    wait(maxOf(0, task.settings.clickDelay.sample() - task.ticksSinceLastClick))
                }
                coroutineContext.ensureActive()
                // don't click a replacement menu until an await action has accepted it
                if (action is ItemAction && task.ownedMenu != null &&
                    (player.containerMenu !== task.ownedMenu || ContainerUtils.current?.revision != task.containerRevision)
                ) {
                    result = ContainerTaskResult.Failure("Container changed before click")
                    break
                }
                if (!with(action) { execute() }) {
                    result = ContainerTaskResult.Failure(action.failureReason)
                    break
                }
                task.completedActions++
            }
            wait(task.settings.endDelay.sample())
        } catch (e: CancellationException) {
            result = ContainerTaskResult.Cancelled
        } catch (e: Exception) {
            logger.error("Container task ${task.name} failed", e)
            result = ContainerTaskResult.Failure(e.message ?: "Container action failed")
        } finally {
            try {
                if (task.cleanupAllowed && mc.player === player) {
                    closeOwnedMenu(task)
                    closeInventory()
                }
            } finally {
                finish(task, result)
            }
        }
    }

    internal fun closeOwnedMenu(task: ContainerTask) {
        val player = mc.player ?: return
        if (task.ownedMenu != null && player.containerMenu === task.ownedMenu) player.closeContainer()
    }

    private fun finish(task: ContainerTask, result: ContainerTaskResult) {
        if (activeTask === task) {
            activeTask = null
            job = null
            restoreMovementIfAllowed()
        }
        task.finish(result)
    }

    /**
     * Restores held movement keys after the task stops blocking them
     */
    internal fun restoreMovementIfAllowed() {
        if (!movementKeysSuppressed || activeTask?.stopsMovement == true) return
        movementKeysSuppressed = false
        KeyMapping.setAll()
    }

    @JvmStatic
    fun onSetScreen(screen: Screen?): Boolean =
        openingInventory && activeTask?.settings?.silent == true && screen is InventoryScreen

    private fun Pair<Int, Int>.sample(): Int = (first..second).random()
}