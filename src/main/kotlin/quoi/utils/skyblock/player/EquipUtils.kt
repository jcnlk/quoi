package quoi.utils.skyblock.player

import kotlinx.coroutines.launch
import net.minecraft.client.KeyMapping
import net.minecraft.world.item.ItemStack
import quoi.QuoiMod.scope
import quoi.QuoiMod.mc
import quoi.annotations.Init
import quoi.api.commands.QuoiCommand
import quoi.api.commands.internal.GreedyString
import quoi.api.events.KeyEvent
import quoi.api.events.MouseEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.EventListener
import quoi.api.events.core.on
import quoi.utils.ChatUtils.modMessage
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.items
import quoi.utils.skyblock.item.ItemUtils.skyblockId
import quoi.utils.skyblock.player.MovementUtils.stop
import java.util.concurrent.atomic.AtomicBoolean

@Init
object EquipUtils : EventListener {
    private const val MENU_TITLE = "Your Equipment and Stats"
    private const val MENU_SLOT_COUNT = 54

    private val equipping = AtomicBoolean()

    val isEquipping: Boolean
        get() = equipping.get()

    private var blockingGameInput = false
    private var blockInputsCurrent = false

    data class EquipResult(
        val success: Boolean,
        val equippedNames: List<String> = emptyList(),
    )

    /**
     * Identifies an item to equip. When both fields are set, both must match.
     *
     * [name] is matched case-insensitively against the unformatted display name;
     * [skyblockId] is matched exactly.
     */
    data class EquipmentPiece(
        val name: String? = null,
        val skyblockId: String? = null,
    ) {
        init {
            require(name != null || skyblockId != null) { "Provide a name or SkyBlock ID." }
            require(name == null || name.isNotBlank()) { "Name must not be blank." }
            require(skyblockId == null || skyblockId.isNotBlank()) { "SkyBlock ID must not be blank." }
        }

        internal fun matches(stack: ItemStack): Boolean {
            val nameMatches = name == null || stack.displayName.string.noControlCodes.contains(name, ignoreCase = true)
            val idMatches = skyblockId == null || stack.skyblockId == skyblockId
            return !stack.isEmpty && nameMatches && idMatches
        }
    }

    /** Equips all [pieces] in order and closes the equipment menu afterward. */
    suspend fun equip(vararg pieces: EquipmentPiece, blockInput: Boolean = false): Boolean =
        equipResult(pieces.asList(), blockInput).success

    /** Same as [equip], with the display names of the items that were actually equipped. */
    suspend fun equipResult(vararg pieces: EquipmentPiece, blockInput: Boolean = false): EquipResult =
        equipResult(pieces.asList(), blockInput)

    /** Convenience overload for display-name based equipment swaps. */
    suspend fun equipByName(vararg names: String, blockInput: Boolean = false): Boolean =
        equipByNameResult(*names, blockInput = blockInput).success

    /** Same as [equipByName], with the display names of the items that were actually equipped. */
    suspend fun equipByNameResult(vararg names: String, blockInput: Boolean = false): EquipResult =
        equipResult(names.map(::EquipmentPiece), blockInput)

    /** Convenience overload for exact SkyBlock-ID based equipment swaps. */
    suspend fun equipBySkyblockId(vararg ids: String, blockInput: Boolean = false): Boolean =
        equipBySkyblockIdResult(*ids, blockInput = blockInput).success

    /** Same as [equipBySkyblockId], with the display names of the items that were actually equipped. */
    suspend fun equipBySkyblockIdResult(vararg ids: String, blockInput: Boolean = false): EquipResult =
        equipResult(ids.map { EquipmentPiece(skyblockId = it) }, blockInput)

    /**
     * Equips every requested item through `/eq`.
     *
     * The equipment menu is cancelled client-side, matching the existing
     * container utilities. Hypixel rebuilds that menu after each equip, so this
     * waits for and cancels each rebuild before clicking the next item.
     */
    suspend fun equip(pieces: Collection<EquipmentPiece>, blockInput: Boolean = false): Boolean =
        equipResult(pieces, blockInput).success

    /** Same as [equip], with the display names of the items that were actually equipped. */
    suspend fun equipResult(pieces: Collection<EquipmentPiece>, blockInput: Boolean = false): EquipResult {
        if (!equipping.compareAndSet(false, true)) return EquipResult(success = false)

        try {
            return equipNow(pieces, blockInput)
        } finally {
            equipping.set(false)
        }
    }

    private suspend fun equipNow(pieces: Collection<EquipmentPiece>, blockInput: Boolean): EquipResult {
        if (pieces.isEmpty()) return EquipResult(success = true)

        val player = mc.player ?: return EquipResult(success = false)
        val targets = findEquipmentTargets(player.inventory.items, player.equipmentItems(), pieces) ?: return EquipResult(success = false)
        if (targets.isEmpty()) return EquipResult(success = true)

        try {
            if (ContainerUtils.getContainerItems(
                command = "eq",
                containerName = MENU_TITLE,
                slots = MENU_SLOT_COUNT,
                onMenuOpen = { startInputBlock(blockInput) },
            ).isEmpty()) return EquipResult(success = false)

            val equippedNames = mutableListOf<String>()
            for ((index, target) in targets.withIndex()) {
                val menuSlot = target.inventorySlot.toEquipmentMenuSlot()
                if (!ContainerUtils.clickAndAwaitContainerReopen(
                        menuSlot,
                        MENU_TITLE,
                        shift = true,
                        closeBeforeReopen = index == targets.lastIndex,
                        onClickSent = if (index == targets.lastIndex) ::stopInputBlock else null,
                    )
                ) {
                    return EquipResult(success = false, equippedNames)
                }
                equippedNames += target.displayName
            }

            return EquipResult(success = true, equippedNames)
        } finally {
            ContainerUtils.closeContainer()
            stopInputBlock()
        }
    }

    private fun findEquipmentTargets(
        inventory: List<ItemStack>,
        armor: List<ItemStack>,
        pieces: Collection<EquipmentPiece>,
    ): List<EquipmentTarget>? {
        val availableSlots = inventory.indices.toMutableSet()
        val targets = mutableListOf<EquipmentTarget>()

        for (piece in pieces) {
            if (armor.any(piece::matches)) {
                continue
            }

            val slot = availableSlots.firstOrNull { piece.matches(inventory[it]) } ?: return null
            availableSlots -= slot
            targets += EquipmentTarget(slot, inventory[slot].displayName.string.noControlCodes)
        }

        return targets
    }

    private data class EquipmentTarget(
        val inventorySlot: Int,
        val displayName: String,
    )

    private fun net.minecraft.client.player.LocalPlayer.equipmentItems(): List<ItemStack> =
        (36..39).map(inventory::getItem)

    private fun Int.toEquipmentMenuSlot(): Int = when (this) {
        in 0..8 -> MENU_SLOT_COUNT + 27 + this
        in 9..35 -> MENU_SLOT_COUNT + this - 9
        else -> error("Player inventory slot $this is not in the main inventory.")
    }

    private fun startInputBlock(blockInputs: Boolean) {
        blockingGameInput = true
        blockInputsCurrent = blockInputs
    }

    private fun stopInputBlock() {
        if (!blockingGameInput) return

        blockingGameInput = false
        blockInputsCurrent = false
        KeyMapping.setAll()
    }

    init {
        QuoiCommand.command.sub("equip") { itemNames: GreedyString ->
            val names = itemNames.string.split(',').map(String::trim)
            if (names.any(String::isEmpty)) {
                return@sub modMessage("&cProvide one or more item names separated by commas.")
            }
            if (isEquipping) {
                return@sub modMessage("&cAn equipment swap is already in progress.")
            }

            scope.launch {
                val result = equipByNameResult(*names.toTypedArray())
                if (result.equippedNames.isNotEmpty()) {
                    modMessage("&aEquipped &f${result.equippedNames.forEquipFeedback()}")
                }

                when {
                    !result.success -> modMessage("&cFailed to equip all requested items.")
                    result.equippedNames.isEmpty() -> modMessage("&eAll requested items are already equipped.")
                }
            }
        }.description("Equips comma-separated items from your inventory through /eq.")

        on<TickEvent.Start> {
            if (blockingGameInput) mc.player?.stop()
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
            stopInputBlock()
        }
    }

    private fun List<String>.forEquipFeedback(): String {
        return joinToString(", ") { it.trim().removeSurrounding("[", "]") }
    }
}
