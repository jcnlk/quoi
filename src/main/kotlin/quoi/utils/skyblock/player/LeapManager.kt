package quoi.utils.skyblock.player

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import quoi.QuoiMod.mc
import quoi.annotations.Init
import quoi.api.events.ChatEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.EventListener
import quoi.api.events.core.Priority
import quoi.api.events.core.on
import quoi.api.input.Keybinds
import quoi.api.input.CatMouse
import quoi.api.skyblock.dungeon.Dungeon.dungeonTeammatesNoSelf
import quoi.api.skyblock.dungeon.Dungeon.getMageCooldownMultiplier
import quoi.api.skyblock.dungeon.Dungeon.inDungeons
import quoi.api.skyblock.dungeon.DungeonClass
import quoi.api.skyblock.dungeon.DungeonPlayer
import quoi.utils.ChatUtils.modMessage
import quoi.utils.skyblock.player.container.ContainerUtils
import quoi.utils.skyblock.player.container.task.*

@Init
object LeapManager : EventListener {
    private data class LeapRequest(
        val target: DungeonPlayer,
        val blockInput: Boolean,
        val fastMode: Boolean,
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
            if (!inProgress) return@on
            if (unformatted != "You cannot use this in a solo dungeon!" &&
                unformatted != "There are no other players to teleport to!"
            ) return@on

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

        on<TickEvent.Server> {
            if (leapCD > 0) leapCD -= 1

            val pending = pendingLeap
            if (pending != null &&
                mc.gui.screen() == null &&
                ContainerUtils.containerId == 0 &&
                ContainerManager.activeTask == null
            ) {
                pendingLeap = null
                doLeap(pending)
            }
        }
    }

    fun leap(
        name: String,
        blockInput: Boolean = false,
        fastMode: Boolean = false,
        swapBack: Boolean = false,
    ) {
        if (name == "" || !inDungeons) return

        val teammate = dungeonTeammatesNoSelf.firstOrNull { !it.isDead && it.name == name }
        startLeap(teammate, formatName(name), blockInput, fastMode, swapBack)
    }

    fun leap(
        clazz: DungeonClass,
        blockInput: Boolean = false,
        fastMode: Boolean = false,
        swapBack: Boolean = false,
    ) {
        if (clazz == DungeonClass.Unknown || !inDungeons) return

        val teammate = dungeonTeammatesNoSelf.firstOrNull { !it.isDead && it.clazz == clazz }
        startLeap(teammate, "&${clazz.colourCode}${clazz.name}", blockInput, fastMode, swapBack)
    }

    private fun startLeap(
        teammate: DungeonPlayer?,
        formattedTarget: String,
        blockInput: Boolean,
        fastMode: Boolean,
        swapBack: Boolean,
    ) {
        teammate ?: return modMessage("&cFailed to leap! $formattedTarget &cnot found")

        val request = LeapRequest(teammate, blockInput, fastMode, swapBack)
        val openLeapMenu = (mc.gui.screen() as? AbstractContainerScreen<*>)?.takeIf { it.title.string == "Spirit Leap" }

        if (!inProgress && pendingLeap == null && openLeapMenu != null && ContainerManager.activeTask == null) {
            doLeap(request, preOpened = true)
        } else if (mc.gui.screen() != null || ContainerUtils.containerId != 0 || ContainerManager.activeTask != null) {
            pendingLeap = request
            modMessage("&eQueued leap to ${formatName(teammate)}")
        } else {
            doLeap(request)
        }
    }

    private fun doLeap(leap: LeapRequest, preOpened: Boolean = false) {
        if (inProgress) return
        if (leapCD > 0) {
            modMessage("&cFailed to leap! On cooldown: ${"%.1f".format(leapCD / 20.0)}s")
            return
        }

        val previousSlot = if (!preOpened) {
            val selectedSlot = mc.player?.inventory?.selectedSlot ?: return
            val swap = SwapManager.swapById("INFINITE_SPIRIT_LEAP", "SPIRIT_LEAP")
            if (!swap.success) return
            suppressUseInput()
            selectedSlot.takeIf { leap.swapBack && !swap.already }
        } else null

        activeLeap = leap
        val newTask = containerTask(
            name = "Leap to ${leap.target.name}",
            force = leap.fastMode,
            preventMovement = true,
            blockInput = leap.blockInput,
            fastMode = leap.fastMode,
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
        if (preOpened) newTask.beginFastBlock()
        newTask.run()
    }

    private fun finishLeap(leap: LeapRequest, result: ContainerTaskResult, previousSlot: Int?) {
        if (activeLeap !== leap) return

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
                if (ContainerUtils.containerId != 0) mc.player?.closeContainer()
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
            while (consumeClick()) Unit
        }
    }

    private fun restoreUseInput() {
        if (!useInputSuppressed) return
        useInputSuppressed = false

        val useKey = mc.options.keyUse.key
        mc.options.keyUse.isDown = when (useKey.type) {
            InputConstants.Type.MOUSE -> CatMouse.isButtonDown(useKey.value)
            InputConstants.Type.KEYSYM -> Keybinds.isKeyDown(useKey.value)
            InputConstants.Type.SCANCODE -> false
        }
    }
}
