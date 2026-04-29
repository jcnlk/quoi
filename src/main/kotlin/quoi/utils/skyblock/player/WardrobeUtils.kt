package quoi.utils.skyblock.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket
import quoi.QuoiMod.mc
import quoi.QuoiMod.scope
import quoi.annotations.Init
import quoi.api.commands.QuoiCommand
import quoi.api.events.PacketEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.EventBus.on
import quoi.api.events.core.Priority
import quoi.api.skyblock.Location.inSkyblock
import quoi.utils.ChatUtils.modMessage
import quoi.utils.Scheduler.scheduleTask
import quoi.utils.Scheduler.wait
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.skyblock.player.ContainerUtils.click
import quoi.utils.skyblock.player.ContainerUtils.containerId
import quoi.utils.skyblock.player.ContainerUtils.closeContainer
import quoi.utils.skyblock.player.ContainerUtils.getContainerItems
import quoi.utils.skyblock.player.MovementUtils.stop
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Init
object WardrobeUtils {
    private val queue = ArrayDeque<WardrobeRequest>()
    private var inProgress = false
    private var preventMoveCurrent = true
    private var currentSlot: Int? = null

    val equippingSlot: Int?
        get() = currentSlot

    init {
        QuoiCommand.command.sub("wardrobe") { slot: Int ->
            if (!equip(slot)) modMessage("&cFailed to queue wardrobe equip.")
        }.description("Equips a wardrobe slot from 1 to 9.")

        on<TickEvent.Start> {
            val player = mc.player ?: return@on
            if (inProgress && preventMoveCurrent) player.stop()
        }

        on<WorldEvent.Change> {
            resetState()
        }
    }

    @JvmOverloads
    fun equip(slot: Int, preventMove: Boolean = true, disableUnequip: Boolean = false): Boolean {
        if (slot !in 1..9) {
            modMessage("&cInvalid wardrobe slot. Use &e/quoi wardrobe <1-9>&c.")
            return false
        }

        if (!inSkyblock) {
            modMessage("&cYou are not in SkyBlock.")
            return false
        }

        if (queue.any { it.slot == slot }) {
            return false
        }

        queue += WardrobeRequest(slot, preventMove, disableUnequip)
        processQueue()
        return true
    }

    fun isBusy(): Boolean = inProgress || queue.isNotEmpty()

    private fun processQueue() {
        if (inProgress || queue.isEmpty()) return
        inProgress = true

        scope.launch(Dispatchers.IO) {
            while (queue.isNotEmpty()) {
                val request = queue.removeFirst()
                currentSlot = request.slot
                preventMoveCurrent = request.preventMove

                val result = equipNow(request.slot, request.disableUnequip)
                modMessage(result.chatMessage)
                wait(2)
            }

            resetState()
        }
    }

    private suspend fun equipNow(slot: Int, disableUnequip: Boolean): EquipResult {
        val targetSlot = slot + 35
        val items = getContainerItems("wardrobe", "Wardrobe (1/3)") // TODO: match with regex ig
        if (items.isEmpty()) {
            closeContainer()
            return EquipResult.failure("Timed out waiting for wardrobe.")
        }

        var targetItem = items.getOrNull(targetSlot)
        if (targetItem == null) {
            closeContainer()
            return EquipResult.failure("Wardrobe slot $slot is empty.")
        }

        val initialState = targetItem.wardrobeState()
        if (initialState == WardrobeState.EMPTY || initialState == WardrobeState.UNKNOWN) {
            awaitWardrobeRefresh(targetSlot)?.let { refreshed ->
                targetItem = refreshed
            }
        }

        val resolvedItem = targetItem
        when (resolvedItem?.wardrobeState() ?: WardrobeState.EMPTY) {
            WardrobeState.EMPTY -> {
                closeContainer()
                return EquipResult.failure("Wardrobe slot $slot is empty.")
            }
            WardrobeState.EQUIPPED -> {
                if (disableUnequip) {
                    closeContainer()
                    return EquipResult.failure("Armor already equipped.")
                }
            }
            WardrobeState.READY -> Unit
            WardrobeState.LOCKED -> {
                closeContainer()
                return EquipResult.failure("Wardrobe slot $slot is locked.")
            }
            WardrobeState.UNKNOWN -> {
                closeContainer()
                return EquipResult.failure("Wardrobe slot $slot is not ready.")
            }
        }

        if (!click(targetSlot)) {
            closeContainer()
            return EquipResult.failure("Failed to click wardrobe slot $slot.")
        }

        wait(2)
        closeContainer()
        return EquipResult.success(slot)
    }

    private suspend fun awaitWardrobeRefresh(slot: Int, timeout: Int = 6): net.minecraft.world.item.ItemStack? = suspendCoroutine { cont ->
        val currentContainerId = containerId
        if (currentContainerId == -1) {
            cont.resume(null)
            return@suspendCoroutine
        }

        var resumed = false
        var listener: quoi.api.events.core.EventBus.EventListener? = null
        listener = on<PacketEvent.Received>(Priority.LOWEST) {
            val packet = packet as? ClientboundContainerSetSlotPacket ?: return@on
            if (packet.containerId != currentContainerId) return@on
            if (packet.slot != slot) return@on

            val item = packet.item.takeUnless { it.isEmpty }
            val state = item?.wardrobeState() ?: WardrobeState.UNKNOWN
            if (state == WardrobeState.EMPTY || state == WardrobeState.UNKNOWN) return@on

            listener?.remove()
            if (resumed) return@on
            resumed = true
            cont.resume(item)
        }

        scheduleTask(timeout) {
            if (resumed) return@scheduleTask
            resumed = true
            listener?.remove()
            cont.resume(null)
        }
    }

    private fun resetState() {
        queue.clear()
        inProgress = false
        preventMoveCurrent = true
        currentSlot = null
    }

    private fun net.minecraft.world.item.ItemStack.wardrobeState(): WardrobeState {
        val name = displayName.string.noControlCodes

        return when {
            name.contains(": Empty", ignoreCase = true) -> WardrobeState.EMPTY
            name.contains(": Equipped", ignoreCase = true) -> WardrobeState.EQUIPPED
            name.contains(": Ready", ignoreCase = true) -> WardrobeState.READY
            name.contains(": Locked", ignoreCase = true) -> WardrobeState.LOCKED
            else -> WardrobeState.UNKNOWN
        }
    }

    private data class WardrobeRequest(
        val slot: Int,
        val preventMove: Boolean,
        val disableUnequip: Boolean,
    )

    private enum class WardrobeState {
        EQUIPPED,
        READY,
        EMPTY,
        LOCKED,
        UNKNOWN,
    }

    private data class EquipResult(
        val chatMessage: String,
    ) {
        companion object {
            fun success(slot: Int) = EquipResult("&aEquipped wardrobe slot &f$slot")
            fun failure(reason: String) = EquipResult("&c$reason")
        }
    }
}
