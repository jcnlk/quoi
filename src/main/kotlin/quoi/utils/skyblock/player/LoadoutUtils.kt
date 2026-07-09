package quoi.utils.skyblock.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.minecraft.client.KeyMapping
import net.minecraft.world.item.ItemStack
import quoi.QuoiMod.mc
import quoi.QuoiMod.scope
import quoi.annotations.Init
import quoi.api.commands.QuoiCommand
import quoi.api.events.KeyEvent
import quoi.api.events.MouseEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.EventListener
import quoi.api.events.core.on
import quoi.api.skyblock.location.Location.inSkyblock
import quoi.utils.ChatUtils.modMessage
import quoi.utils.Scheduler.wait
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.skyblock.item.ItemUtils.loreString
import quoi.utils.skyblock.player.ContainerUtils.clickAndAwaitContainerReopen
import quoi.utils.skyblock.player.ContainerUtils.closeContainer
import quoi.utils.skyblock.player.ContainerUtils.getContainerItems
import quoi.utils.skyblock.player.MovementUtils.stop
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean

@Init
object LoadoutUtils : EventListener {
    private val loadoutSlots = listOf(14, 15, 16, 23, 24, 25, 32, 33, 34, 41, 42, 43)

    private val queue = ArrayDeque<LoadoutRequest>()
    private var inProgress = false
    private var currentSlot: Int? = null
    private var blockingGameInput = false
    private var blockInputsCurrent = false
    private var preventMoveCurrent = true

    val equippingSlot: Int?
        get() = currentSlot

    fun isBusy(): Boolean = inProgress || queue.isNotEmpty()

    @JvmOverloads
    fun equip(
        slot: Int,
        preventMove: Boolean = true,
        blockInputs: Boolean = false,
        onMenuOpen: (() -> Unit)? = null,
        onMenuClose: (() -> Unit)? = null,
    ): Boolean {
        if (slot !in 1..loadoutSlots.size) {
            modMessage("&cInvalid loadout slot. Use &e/quoi loadout <1-${loadoutSlots.size}>&c.")
            return false
        }

        if (!inSkyblock) {
            modMessage("&cYou are not in SkyBlock.")
            return false
        }

        if (queue.any { it.slot == slot }) return false

        queue += LoadoutRequest(slot, preventMove, blockInputs, onMenuOpen, onMenuClose)
        processQueue()
        return true
    }

    private fun processQueue() {
        if (inProgress || queue.isEmpty()) return
        inProgress = true

        scope.launch(Dispatchers.IO) {
            while (queue.isNotEmpty()) {
                val request = queue.removeFirst()
                currentSlot = request.slot
                preventMoveCurrent = request.preventMove
                blockInputsCurrent = request.blockInputs

                val menuClosed = AtomicBoolean()
                val onMenuClosed = {
                    if (menuClosed.compareAndSet(false, true)) {
                        stopInputBlock()
                        request.onMenuClose?.invoke()
                    }
                }

                val result = try {
                    equipNow(request.slot, request.onMenuOpen, onMenuClosed)
                } finally {
                    onMenuClosed()
                }

                modMessage(result.chatMessage)
                wait(2)
            }

            resetState()
        }
    }

    private suspend fun equipNow(
        slot: Int,
        onMenuOpen: (() -> Unit)?,
        onMenuClosed: () -> Unit,
    ): EquipResult {
        val items = openLoadouts(onMenuOpen)
        if (items.isEmpty()) {
            closeContainer()
            return EquipResult.failure("Timed out waiting for loadouts.")
        }

        val targetSlot = loadoutSlots[slot - 1]
        val target = items.getOrNull(targetSlot)
        if (target?.isLoadoutButton() != true) {
            closeContainer()
            return EquipResult.failure("Loadout slot $slot is not equipable.")
        }

        if (!clickAndAwaitContainerReopen(
                targetSlot,
                "(1/3) Loadouts",
                closeBeforeReopen = true,
                onClickSent = onMenuClosed,
            )
        ) {
            closeContainer()
            return EquipResult.failure("Failed to click loadout slot $slot.")
        }

        closeContainer()
        return EquipResult.success(slot)
    }

    private suspend fun openLoadouts(onMenuOpen: (() -> Unit)?): List<ItemStack?> {
        val items = getContainerItems(
            command = "/loadout",
            containerName = Regex("""^\(\d+/\d+\) Loadouts$""", RegexOption.IGNORE_CASE),
            onMenuOpen = {
                startInputBlock()
                onMenuOpen?.invoke()
            },
        )
        if (items.isNotEmpty()) return items

        return emptyList()
    }

    private fun ItemStack.isLoadoutButton(): Boolean {
        val lore = loreString?.noControlCodes ?: return false
        return lore.contains("Left-click to equip!", ignoreCase = true)
    }

    private fun startInputBlock() {
        blockingGameInput = true
    }

    private fun stopInputBlock() {
        if (!blockingGameInput) return

        blockingGameInput = false
        blockInputsCurrent = false
        preventMoveCurrent = true
        KeyMapping.setAll()
    }

    private fun resetState() {
        queue.clear()
        inProgress = false
        currentSlot = null
        stopInputBlock()
    }

    init {
        QuoiCommand.command.sub("loadout") { slot: Int ->
            if (!equip(slot)) modMessage("&cFailed to queue loadout equip.")
        }.description("Equips a loadout slot from 1 to ${loadoutSlots.size}.")

        on<TickEvent.Start> {
            if (blockingGameInput && preventMoveCurrent) mc.player?.stop()
        }

        on<KeyEvent.Press> {
            if (blockingGameInput && blockInputsCurrent) cancel()
        }

        on<KeyEvent.Release> {
            if (blockingGameInput && blockInputsCurrent) cancel()
        }

        on<MouseEvent.Click> {
            if (blockingGameInput && blockInputsCurrent) cancel()
        }

        on<MouseEvent.Scroll> {
            if (blockingGameInput && blockInputsCurrent) cancel()
        }

        on<MouseEvent.Move> {
            if (blockingGameInput && blockInputsCurrent) cancel()
        }

        on<WorldEvent.Change> {
            resetState()
        }
    }

    private data class LoadoutRequest(
        val slot: Int,
        val preventMove: Boolean,
        val blockInputs: Boolean,
        val onMenuOpen: (() -> Unit)?,
        val onMenuClose: (() -> Unit)?,
    )

    private data class EquipResult(
        val chatMessage: String,
    ) {
        companion object {
            fun success(slot: Int) = EquipResult("&aEquipped loadout &f$slot")
            fun failure(reason: String) = EquipResult("&c$reason")
        }
    }
}
