package quoi.utils.skyblock.player

import net.minecraft.network.protocol.game.*
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import quoi.annotations.Init
import quoi.api.events.*
import quoi.api.events.core.EventDispatcher.totalTicks
import quoi.api.events.core.EventListener
import quoi.api.events.core.Priority
import quoi.api.events.core.on
import quoi.api.input.Keyboard
import quoi.api.skyblock.dungeon.Dungeon.dungeonTeammatesNoSelf
import quoi.api.skyblock.dungeon.Dungeon.getMageCooldownMultiplier
import quoi.api.skyblock.dungeon.Dungeon.inDungeons
import quoi.api.skyblock.dungeon.enums.DungeonClass
import quoi.api.skyblock.dungeon.enums.DungeonPlayer
import quoi.utils.ChatUtils.modMessage
import quoi.utils.DualTime
import quoi.utils.Shortcuts
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.StringUtils.toFixed
import quoi.utils.skyblock.item.ItemUtils.skyblockId
import quoi.utils.skyblock.player.container.CONTAINER_MENU
import quoi.utils.skyblock.player.container.ContainerUtils
import quoi.utils.skyblock.player.container.IContainerSettings
import quoi.utils.skyblock.player.container.task.*
import kotlin.math.ceil
import kotlin.math.roundToInt

@Init
object LeapManager : EventListener, Shortcuts {
    private const val MENU_TIMEOUT_TICKS = 60
    private const val LEAP_MENU_TITLE = "Spirit Leap"

    private val baseCooldownTicks = mapOf("INFINITE_SPIRIT_LEAP" to 40, "SPIRIT_LEAP" to 100)
    private val cooldowns = mutableMapOf<String, DualTime>()
    private val leapTargets = mutableMapOf<Int, String>()

    private data class LeapRequest(
        val target: DungeonPlayer,
        val settings: IContainerSettings,
        val swapBack: Boolean,
    )

    private data class MenuUse(val itemId: String, val expiresAt: Int)
    private data class LeapClick(
        val target: String,
        val itemId: String?,
        val baseTicks: Long,
        val expiresAtMs: Long,
    )

    private var activeLeap: LeapRequest? = null
    private var pendingLeap: LeapRequest? = null
    private var task: ContainerTask? = null
    private var useInputSuppressed = false
    private var menuUse: MenuUse? = null
    private var menuItemId: String? = null
    private var lastLeapClick: LeapClick? = null

    private val inProgress: Boolean
        get() = activeLeap != null

    private val leapMenu get() = ContainerUtils.current?.takeIf { it.title == LEAP_MENU_TITLE }

    init {
        on<PacketEvent.Sent>(Priority.LOWEST) {
            val hand = when (val sent = packet) {
                is ServerboundUseItemPacket -> sent.hand
                is ServerboundUseItemOnPacket -> sent.hand
                else -> return@on
            }
            if (!inDungeons) return@on
            val itemId = player.getItemInHand(hand).skyblockId ?: return@on
            if (itemId in baseCooldownTicks) menuUse = MenuUse(itemId, totalTicks + MENU_TIMEOUT_TICKS)
        }

        on<ContainerEvent.Open> {
            val use = menuUse
            menuUse = null
            menuItemId = null
            leapTargets.clear()
            if (container.title != LEAP_MENU_TITLE) return@on

            val itemId = use?.takeIf { totalTicks < it.expiresAt }?.itemId ?: player.mainHandItem.skyblockId
            menuItemId = itemId?.takeIf(baseCooldownTicks::containsKey)
        }

        on<PacketEvent.ReceivedPost>(Priority.HIGH) {
            val menuId = leapMenu?.menu?.containerId ?: return@on
            when (val update = packet) {
                is ClientboundContainerSetContentPacket -> if (update.containerId == menuId) {
                    for (slot in 11..15) cacheLeapTarget(slot, update.items.getOrNull(slot))
                }
                is ClientboundContainerSetSlotPacket -> if (update.containerId == menuId && update.slot in 11..15) {
                    cacheLeapTarget(update.slot, update.item)
                }
            }
        }

        on<PacketEvent.Sent, ServerboundContainerClickPacket>(Priority.LOWEST) {
            if (leapMenu?.menu?.containerId != packet.containerId || packet.containerInput() != ContainerInput.PICKUP) return@on

            val targetName = leapTargets[packet.slotNum.toInt()] ?: return@on
            val target = dungeonTeammatesNoSelf.firstOrNull { it.name.equals(targetName, ignoreCase = true) } ?: return@on

            val now = DualTime(totalTicks.toLong())
            val itemId = menuItemId
            val baseTicks = itemId?.let { id ->
                val cooldownPercent = (getMageCooldownMultiplier() * 100).roundToInt()
                ceil(baseCooldownTicks.getValue(id) * cooldownPercent / 100.0).toLong()
            } ?: 0L
            if (itemId != null) cooldowns[itemId] = now.plusTicks(baseTicks)
            menuItemId = null
            val previousExpiry = lastLeapClick?.expiresAtMs ?: 0L
            val confirmedItemId = itemId.takeIf { now.real >= previousExpiry }
            lastLeapClick = LeapClick(target.name, confirmedItemId, baseTicks, now.real + 2_000L)

            SkyblockPlayerEvent.Leap(target).post()
        }

        on<ChatEvent.Packet>(Priority.HIGH, acceptCancelled = true) {
            val click = lastLeapClick ?: return@on
            val itemId = click.itemId ?: return@on
            if (!unformatted.startsWith("You have teleported to ${click.target}!", ignoreCase = true)) return@on

            lastLeapClick = null
            val now = DualTime(totalTicks.toLong())
            if (now.real >= click.expiresAtMs) return@on
            cooldowns[itemId] = now.plusTicks((click.baseTicks - 2).coerceAtLeast(0L))
        }

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
            cooldowns.clear()
            lastLeapClick = null
            menuUse = null
            menuItemId = null
            leapTargets.clear()
        }

        on<TickEvent.Start>(Priority.HIGHEST) {
            if (useInputSuppressed) suppressUseInput()
        }

        on<TickEvent.Start>(Priority.LOWEST) {
            val pending = pendingLeap ?: return@on
            if (mc.gui.screen() != null || ContainerUtils.containerId != 0 || ContainerManager.activeTask != null) return@on

            when (doLeap(pending)) {
                LeapStartResult.Started,
                LeapStartResult.Rejected -> if (pendingLeap === pending) pendingLeap = null
                LeapStartResult.Retry -> Unit
            }
        }
    }

    private fun remainingCooldown(itemId: String?): Long {
        val deadline = cooldowns[itemId] ?: return 0L
        val remaining = deadline - DualTime(totalTicks.toLong())
        return maxOf(0L, remaining.ticks, ceil(remaining.real / 50.0).toLong())
    }

    private fun cacheLeapTarget(slot: Int, stack: ItemStack?) {
        if (stack == null || stack.isEmpty) leapTargets.remove(slot)
        else leapTargets[slot] = stack.hoverName.string.noControlCodes.trim().substringAfterLast(' ')
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
        if (activeLeap?.target?.name == teammate.name || pendingLeap?.target?.name == teammate.name) return

        val request = LeapRequest(teammate, settings, swapBack)
        val openLeapMenu = leapMenu?.takeIf { it.menu === player.containerMenu }

        if (!inProgress && pendingLeap == null && openLeapMenu != null && ContainerManager.activeTask == null) {
            doLeap(request, preOpened = true)
        } else if (mc.gui.screen() != null || ContainerUtils.containerId != 0 || ContainerManager.activeTask != null) {
            queueLeap(request)
        } else if (doLeap(request) == LeapStartResult.Retry) {
            queueLeap(request)
        }
    }

    private fun queueLeap(request: LeapRequest) {
        pendingLeap = request
        modMessage("&eQueued leap to ${formatName(request.target)}")
    }

    private fun doLeap(leap: LeapRequest, preOpened: Boolean = false): LeapStartResult {
        if (inProgress) return LeapStartResult.Retry
        val hotbarItems = (0..<8).map { player.inventory.getItem(it).skyblockId }
        val leapId = if (preOpened) null else baseCooldownTicks.keys
            .filter { it in hotbarItems }
            .minByOrNull(::remainingCooldown) ?: run {
            modMessage("&cFailed to leap: no leap found in hotbar")
            return LeapStartResult.Rejected
        }
        val remaining = remainingCooldown(leapId)
        if (remaining > 0) {
            modMessage("&cFailed to leap! On cooldown: ${(remaining / 20.0).toFixed(1)}s")
            return LeapStartResult.Rejected
        }

        val previousSlot = if (!preOpened) {
            val selectedSlot = player.inventory.selectedSlot
            val swap = SwapManager.swapById(checkNotNull(leapId))
            if (swap == SwapResult.TOO_FAST) return LeapStartResult.Retry
            if (!swap.success) {
                if (swap != SwapResult.NOT_FOUND) modMessage("&cFailed swapping to leap: ${swap.name}")
                return LeapStartResult.Rejected
            }
            suppressUseInput()
            selectedSlot.takeIf { leap.swapBack && !swap.already }
        } else null

        activeLeap = leap
        val newTask = containerTask(
            name = "Leap to ${leap.target.name}",
            settings = leap.settings,
            reportErrors = false,
        ) {
            if (!preOpened) {
                check("could not select a Spirit Leap before use. Try again.") {
                    val id = checkNotNull(leapId)
                    remainingCooldown(id) == 0L && SwapManager.swapById(id).success
                }
                action { PlayerUtils.interact() }
                awaitContainer(
                    LEAP_MENU_TITLE,
                    waitForItems = true,
                    timeout = MENU_TIMEOUT_TICKS,
                    failureMessage = "Spirit Leap menu did not open in time. Try again.",
                )
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

        when (result) {
            ContainerTaskResult.Success -> modMessage("&aLeaping to ${formatName(leap.target)}")
            ContainerTaskResult.Busy -> modMessage("&cFailed to leap: another container action is active")
            ContainerTaskResult.Cancelled -> Unit
            is ContainerTaskResult.Failure -> modMessage("&cFailed to leap to ${formatName(leap.target)}&c: ${result.message}")
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
