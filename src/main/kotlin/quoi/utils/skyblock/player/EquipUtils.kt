package quoi.utils.skyblock.player

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import net.minecraft.world.item.ItemStack
import quoi.QuoiMod.mc
import quoi.QuoiMod.scope
import quoi.annotations.Init
import quoi.api.commands.QuoiCommand
import quoi.api.commands.internal.GreedyString
import quoi.utils.ChatUtils
import quoi.utils.ChatUtils.modMessage
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.items
import quoi.utils.skyblock.item.ItemUtils.skyblockId
import quoi.utils.skyblock.player.container.ContainerUtils
import quoi.utils.skyblock.player.container.task.ContainerTaskResult
import quoi.utils.skyblock.player.container.task.IndexSlot
import quoi.utils.skyblock.player.container.task.containerTask
import java.util.concurrent.atomic.AtomicBoolean

@Init
object EquipUtils {
    private const val MENU_TITLE = "Stats & Equipment"

    private val equipping = AtomicBoolean()

    val isEquipping: Boolean
        get() = equipping.get()

    data class EquipResult(
        val success: Boolean,
        val equippedNames: List<String> = emptyList(),
    )

    /** Identifies an item by display name, SkyBlock ID, or both. */
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

    suspend fun equip(
        vararg pieces: EquipmentPiece,
        blockInput: Boolean = false,
        fastMode: Boolean = false,
    ): Boolean = equipResult(pieces.asList(), blockInput, fastMode).success

    suspend fun equipResult(
        vararg pieces: EquipmentPiece,
        blockInput: Boolean = false,
        fastMode: Boolean = false,
    ): EquipResult = equipResult(pieces.asList(), blockInput, fastMode)

    suspend fun equipByName(
        vararg names: String,
        blockInput: Boolean = false,
        fastMode: Boolean = false,
    ): Boolean = equipByNameResult(*names, blockInput = blockInput, fastMode = fastMode).success

    suspend fun equipByNameResult(
        vararg names: String,
        blockInput: Boolean = false,
        fastMode: Boolean = false,
    ): EquipResult = equipResult(names.map(::EquipmentPiece), blockInput, fastMode)

    suspend fun equipBySkyblockId(
        vararg ids: String,
        blockInput: Boolean = false,
        fastMode: Boolean = false,
    ): Boolean = equipBySkyblockIdResult(*ids, blockInput = blockInput, fastMode = fastMode).success

    suspend fun equipBySkyblockIdResult(
        vararg ids: String,
        blockInput: Boolean = false,
        fastMode: Boolean = false,
    ): EquipResult = equipResult(ids.map { EquipmentPiece(skyblockId = it) }, blockInput, fastMode)

    suspend fun equip(
        pieces: Collection<EquipmentPiece>,
        blockInput: Boolean = false,
        fastMode: Boolean = false,
    ): Boolean = equipResult(pieces, blockInput, fastMode).success

    suspend fun equipResult(
        pieces: Collection<EquipmentPiece>,
        blockInput: Boolean = false,
        fastMode: Boolean = false,
    ): EquipResult {
        if (!equipping.compareAndSet(false, true)) return EquipResult(success = false)

        try {
            if (pieces.isEmpty()) return EquipResult(success = true)

            val targets = onClientThread {
                val player = mc.player ?: return@onClientThread null
                findEquipmentTargets(player.inventory.items, player.equipmentItems(), pieces)
            } ?: return EquipResult(success = false)
            if (targets.isEmpty()) return EquipResult(success = true)

            val equippedNames = mutableListOf<String>()
            val task = containerTask(
                name = "Equipment",
                force = fastMode,
                preventMovement = true,
                blockInput = blockInput,
                fastMode = fastMode,
            ) {
                action { ChatUtils.command("stats") }
                awaitContainer(MENU_TITLE, waitForItems = true)

                targets.forEachIndexed { index, target ->
                    quickMove(IndexSlot(equipmentMenuSlot(target.inventorySlot), null))
                    if (index == targets.lastIndex) action { mc.player?.closeContainer() }
                    awaitContainer(MENU_TITLE)
                    action { equippedNames += target.displayName }
                }

                action { mc.player?.closeContainer() }

                onFinished { result ->
                    if (result != ContainerTaskResult.Busy && ContainerUtils.containerId != 0) {
                        mc.player?.closeContainer()
                    }
                }
            }

            onClientThread { task.run() }
            return when (val result = task.await()) {
                ContainerTaskResult.Success -> EquipResult(true, equippedNames)
                ContainerTaskResult.Busy,
                ContainerTaskResult.Cancelled -> EquipResult(false, equippedNames)
                is ContainerTaskResult.Failure -> {
                    modMessage("&c${result.message}.")
                    EquipResult(false, equippedNames)
                }
            }
        } finally {
            equipping.set(false)
        }
    }

    private suspend fun <T> onClientThread(block: () -> T): T {
        if (mc.isSameThread) return block()

        val result = CompletableDeferred<T>()
        mc.execute {
            runCatching(block).fold(result::complete, result::completeExceptionally)
        }
        return result.await()
    }

    private fun findEquipmentTargets(
        inventory: List<ItemStack>,
        armor: List<ItemStack>,
        pieces: Collection<EquipmentPiece>,
    ): List<EquipmentTarget>? {
        val availableSlots = inventory.indices.toMutableSet()
        val targets = mutableListOf<EquipmentTarget>()

        for (piece in pieces) {
            if (armor.any(piece::matches)) continue

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
    }

    private fun List<String>.forEquipFeedback(): String = when (size) {
        0 -> ""
        1 -> first()
        2 -> joinToString(" and ")
        else -> dropLast(1).joinToString(", ") + ", and " + last()
    }
}

/** Maps the player's inventory ordering to Hypixel's 54-slot equipment menu ordering. */
internal fun equipmentMenuSlot(inventorySlot: Int): Int = when (inventorySlot) {
    in 0..8 -> 54 + 27 + inventorySlot
    in 9..35 -> 54 + inventorySlot - 9
    else -> error("Player inventory slot $inventorySlot is not in the main inventory.")
}
