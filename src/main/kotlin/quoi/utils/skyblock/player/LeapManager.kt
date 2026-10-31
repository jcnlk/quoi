package quoi.utils.skyblock.player

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import quoi.QuoiMod.logger
import quoi.QuoiMod.mc
import quoi.annotations.Init
import quoi.annotations.Internal
import quoi.api.events.ChatEvent
import quoi.api.events.PacketEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.EventDispatcher
import quoi.api.events.core.EventListener
import quoi.api.events.core.Priority
import quoi.api.events.core.on
import quoi.api.input.Keyboard
import quoi.api.skyblock.dungeon.Dungeon.dungeonTeammatesNoSelf
import quoi.api.skyblock.dungeon.Dungeon.getMageCooldownMultiplier
import quoi.api.skyblock.dungeon.Dungeon.inDungeons
import quoi.api.skyblock.dungeon.enums.DungeonClass
import quoi.api.skyblock.dungeon.enums.DungeonPlayer
import quoi.module.impl.render.clickgui.ClickGui
import quoi.utils.ChatUtils.modMessage
import quoi.utils.skyblock.item.ItemUtils.skyblockId
import quoi.utils.skyblock.player.container.ContainerUtils
import quoi.utils.skyblock.player.container.CONTAINER_MENU
import quoi.utils.skyblock.player.container.IContainerSettings
import quoi.utils.skyblock.player.container.task.*

// TODO: redo?
@Init
@OptIn(Internal::class)
object LeapManager : EventListener {
    private data class LeapRequest(
        val target: DungeonPlayer,
        val settings: IContainerSettings,
        val swapBack: Boolean,
    )

    private var activeLeap: LeapRequest? = null
    private var pendingLeap: LeapRequest? = null
    private var task: ContainerTask? = null
    private var useInputSuppressed = false

    var lastLeap = 0L
        private set

    var leapCD = 0.0
        private set

    private val inProgress: Boolean
        get() = activeLeap != null

    init {
        on<ChatEvent.Packet> {
            if (unformatted != "You cannot use this in a solo dungeon!" &&
                unformatted != "There are no other players to teleport to!"
            ) return@on

            if (!inProgress) return@on
            modMessage("&cFailed to leap! You're in a solo dungeon!")
            task?.cancel() ?: resetActiveLeap()
        }

        on<WorldEvent.Change> {
            pendingLeap = null
            task?.cancel()
            resetActiveLeap()
        }

        on<TickEvent.Start>(Priority.HIGHEST) {
            if (useInputSuppressed) suppressUseInput()
        }

        on<TickEvent.Start>(Priority.LOWEST) {
            val pending = pendingLeap ?: return@on
            if (mc.screen != null || ContainerUtils.containerId != 0 || ContainerManager.activeTask != null) return@on

            when (val result = doLeap(pending)) {
                LeapStartResult.Started,
                LeapStartResult.Rejected -> if (pendingLeap === pending) {
                    if (ClickGui.debug) logger.info("Leap dequeued: target={}, result={}", pending.target.name, result)
                    pendingLeap = null
                }
                LeapStartResult.Retry -> Unit
            }
        }

        on<TickEvent.Server> {
            if (leapCD > 0) leapCD -= 1
        }

        on<PacketEvent.Sent, ServerboundUseItemPacket>(Priority.LOWEST - 2, acceptCancelled = true) {
            val leap = activeLeap ?: return@on
            if (!ClickGui.debug) return@on
            logger.info(
                "Leap use-item event: target={}, hand={}, sequence={}, cancelled={}, item={}",
                leap.target.name, packet.hand, packet.sequence, cancelled, mc.player?.mainHandItem?.skyblockId
            )
        }
        on<PacketEvent.Received, ClientboundOpenScreenPacket>(Priority.HIGHEST + 2, acceptCancelled = true) {
            val leap = activeLeap ?: return@on
            if (!ClickGui.debug) return@on
            logger.info(
                "Leap received menu: target={}, id={}, title={}",
                leap.target.name, packet.containerId, packet.title.string
            )
        }
    }

    fun leap(
        name: String,
        settings: IContainerSettings = CONTAINER_MENU,
        swapBack: Boolean = false,
    ) {
        check(mc.isSameThread) { "Leap requests must be submitted on the client thread" }
        if (name == "" || !inDungeons) return

        val teammate = dungeonTeammatesNoSelf.firstOrNull { !it.isDead && it.name == name }
        startLeap(teammate, formatName(name), settings, swapBack)
    }

    fun leap(
        clazz: DungeonClass,
        settings: IContainerSettings = CONTAINER_MENU,
        swapBack: Boolean = false,
    ) {
        check(mc.isSameThread) { "Leap requests must be submitted on the client thread" }
        if (clazz == DungeonClass.Unknown || !inDungeons) return

        val teammate = dungeonTeammatesNoSelf.firstOrNull { !it.isDead && it.clazz == clazz }
        startLeap(teammate, "&${clazz.colourCode}${clazz.name}", settings, swapBack)
    }

    private fun startLeap(
        teammate: DungeonPlayer?,
        formattedTarget: String,
        settings: IContainerSettings,
        swapBack: Boolean,
    ) {
        teammate ?: return modMessage("&cFailed to leap! $formattedTarget &cnot found")

        val request = LeapRequest(teammate, settings, swapBack)
        val openLeapMenu = (mc.screen as? AbstractContainerScreen<*>)?.takeIf { it.title.string == "Spirit Leap" }

        if (!inProgress && pendingLeap == null && openLeapMenu != null && ContainerManager.activeTask == null) {
            doLeap(request, preOpened = true)
        } else if (mc.screen != null || ContainerUtils.containerId != 0 || ContainerManager.activeTask != null) {
            queueLeap(request)
        } else if (doLeap(request) == LeapStartResult.Retry) {
            queueLeap(request)
        }
    }

    private fun queueLeap(request: LeapRequest) {
        pendingLeap = request
        if (ClickGui.debug) {
            val screen = mc.screen
            val runningTask = ContainerManager.activeTask
            val reason = when {
                screen != null -> "screen open"
                ContainerUtils.containerId != 0 -> "container open"
                runningTask != null -> "container task active"
                inProgress -> "leap active"
                else -> "hotbar swap used this tick"
            }
            logger.info(
                "Leap queued: target={}, reason={}, screen={}, trackedContainer={}, playerContainer={}, title={}, silent={}, task={}, action={}/{}, activeLeap={}, leapTaskResult={}",
                request.target.name, reason, screen?.javaClass?.name, ContainerUtils.containerId,
                mc.player?.containerMenu?.containerId, ContainerUtils.current?.title, ContainerUtils.containerServerSide,
                runningTask?.name, runningTask?.completedActions, runningTask?.totalActions,
                activeLeap?.target?.name, task?.result
            )
        }
        modMessage("&eQueued leap to ${formatName(request.target)}")
    }

    private fun doLeap(leap: LeapRequest, preOpened: Boolean = false): LeapStartResult {
        if (inProgress) return LeapStartResult.Retry
        if (leapCD > 0) {
            modMessage("&cFailed to leap! On cooldown: ${"%.1f".format(leapCD / 20.0)}s")
            return LeapStartResult.Rejected
        }

        val previousSlot = if (!preOpened) {
            val selectedSlot = mc.player?.inventory?.selectedSlot ?: run {
                modMessage("&cFailed to leap: player unavailable")
                return LeapStartResult.Rejected
            }
            val swap = SwapManager.swapById("INFINITE_SPIRIT_LEAP", "SPIRIT_LEAP")
            if (swap == SwapResult.TOO_FAST) return LeapStartResult.Retry
            if (!swap.success) {
                if (swap != SwapResult.NOT_FOUND) modMessage("&cFailed swapping to leap: ${swap.name}")
                return LeapStartResult.Rejected
            }
            suppressUseInput()
            selectedSlot.takeIf { leap.swapBack && !swap.already }
        } else null

        activeLeap = leap
        if (ClickGui.debug) {
            logger.info(
                "Leap started: target={}, preOpened={}, slot={}, item={}, serverTick={}",
                leap.target.name, preOpened, mc.player?.inventory?.selectedSlot,
                mc.player?.mainHandItem?.skyblockId, EventDispatcher.totalTicks
            )
        }
        val newTask = containerTask(
            name = "Leap to ${leap.target.name}",
            settings = leap.settings,
        ) {
            if (!preOpened) {
                action { PlayerUtils.interact() }
                awaitContainer("Spirit Leap", waitForItems = true)
            }
            pickup(
                item { it.displayName.string.contains(leap.target.name) }.menu,
                failureMessage = "target not found in leap menu",
            )

            onFinished { result -> finishLeap(leap, result, previousSlot) }
        }

        task = newTask
        newTask.run()
        return LeapStartResult.Started
    }

    private fun finishLeap(leap: LeapRequest, result: ContainerTaskResult, previousSlot: Int?) {
        if (activeLeap !== leap) return

        if (ClickGui.debug) logger.info("Leap finished: target={}, result={}, serverTick={}", leap.target.name, result, EventDispatcher.totalTicks)
        when (result) {
            ContainerTaskResult.Success -> {
                lastLeap = System.currentTimeMillis()
                leapCD = 48 * getMageCooldownMultiplier()
                modMessage("&aLeaping to ${formatName(leap.target)}")
            }
            ContainerTaskResult.Busy -> modMessage("&cFailed to leap: another container action is active")
            ContainerTaskResult.Cancelled -> Unit
            is ContainerTaskResult.Failure -> {
                modMessage("&cFailed to leap to ${formatName(leap.target)}&c: ${result.message}")
            }
        }

        previousSlot?.let(SwapManager::swapToSlot)

        resetActiveLeap()
    }

    private fun formatName(name: String): String {
        val teammate = dungeonTeammatesNoSelf.firstOrNull { it.name.equals(name, true) }
        return if (teammate != null) formatName(teammate) else "&f$name"
    }

    private fun formatName(player: DungeonPlayer): String = "&${player.clazz.colourCode}${player.name}"

    private fun resetActiveLeap() {
        activeLeap = null
        task = null
        restoreUseInput()
    }

    private fun suppressUseInput() {
        useInputSuppressed = true
        mc.options.keyUse.apply {
            isDown = false
            while (consumeClick()) {
                // Drain queued use clicks before opening the leap menu.
            }
        }
    }

    private fun restoreUseInput() {
        if (!useInputSuppressed) return
        useInputSuppressed = false

        val useKey = mc.options.keyUse.key
        mc.options.keyUse.isDown = Keyboard.isKeyDown(useKey)
    }

    private enum class LeapStartResult {
        Started, Retry, Rejected
    }
}
