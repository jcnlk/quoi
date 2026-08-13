package quoi.utils.skyblock.player

import net.minecraft.world.inventory.ClickType
import net.minecraft.world.item.ItemStack
import quoi.QuoiMod.mc
import quoi.utils.skyblock.item.ItemUtils.skyblockId
import quoi.utils.skyblock.item.ItemUtils.skyblockUuid

object HotbarSwapperUtils {
    const val HOTBAR_START = 0
    const val HOTBAR_END = 8
    private const val INVENTORY_START = 9
    private const val INVENTORY_END = 35

    var activePresetName: String? = null
        private set

    val isSwapping: Boolean get() = activePresetName != null

    fun beginSwap(presetName: String) {
        activePresetName = presetName
    }

    fun endSwap(presetName: String? = activePresetName) {
        if (presetName == null || activePresetName == presetName) activePresetName = null
    }

    fun captureHotbar(name: String): HotbarPreset {
        val inventory = mc.player?.inventory
        val slots = (HOTBAR_START..HOTBAR_END).map { slot ->
            HotbarItem.from(inventory?.getItem(slot))
        }
        return HotbarPreset(name = name, slots = slots.toMutableList())
    }

    fun findPreset(presets: List<HotbarPreset>, name: String, fuzzy: Boolean = false): HotbarPreset? {
        val query = name.lowercase()
        return presets.firstOrNull { preset ->
            val presetName = preset.name.lowercase()
            if (fuzzy) query in presetName else query == presetName
        }
    }

    fun findMatchingInventorySlot(
        hotbarItem: HotbarItem,
        targetHotbarSlot: Int,
        ignoredSlots: Set<Int>,
        includeHotbar: Boolean
    ): Int {
        if (hotbarItem.isEmpty) return NOT_FOUND

        val inventory = mc.player?.inventory ?: return NOT_FOUND
        val current = inventory.getItem(targetHotbarSlot)
        if (!current.isEmpty && hotbarItem.matches(current)) return NOT_FOUND

        val slots = inventory.nonEquipmentItems.take(36)
        val range = if (includeHotbar) HOTBAR_START..INVENTORY_END else INVENTORY_START..INVENTORY_END

        for (slot in range) {
            if (slot == targetHotbarSlot || slot in ignoredSlots) continue
            val stack = slots.getOrNull(slot) ?: continue
            if (stack.isEmpty) continue
            if (hotbarItem.matches(stack)) return slot
        }

        return NOT_FOUND
    }

    fun findEmptyInventorySlot(ignoredSlots: Set<Int>): Int {
        val slots = mc.player?.inventory?.nonEquipmentItems?.take(36) ?: return NOT_FOUND
        for (slot in INVENTORY_START..INVENTORY_END) {
            val stack = slots.getOrNull(slot) ?: continue
            if (slot !in ignoredSlots && stack.isEmpty) return slot
        }
        return NOT_FOUND
    }

    fun swapWithHotbar(inventorySlot: Int, hotbarSlot: Int): Boolean {
        val player = mc.player ?: return false
        if (hotbarSlot !in HOTBAR_START..HOTBAR_END || inventorySlot !in HOTBAR_START..INVENTORY_END) return false

        mc.gameMode?.handleInventoryMouseClick(
            player.inventoryMenu.containerId,
            inventorySlot.toMenuSlot(),
            hotbarSlot,
            ClickType.SWAP,
            player
        ) ?: return false

        return true
    }

    private fun Int.toMenuSlot(): Int = if (this in HOTBAR_START..HOTBAR_END) this + 36 else this

    const val NOT_FOUND = -1
}

data class HotbarPreset(
    var name: String = "",
    var slots: MutableList<HotbarItem> = mutableListOf(),
    var message: String? = null,
    var requiredFloor: String? = null,
    var requiredClass: String? = null
)

data class HotbarItem(
    var uuid: String? = null,
    var id: String? = null,
    var name: String = "None"
) {
    val isEmpty: Boolean get() = uuid == null && id == null

    fun matches(stack: ItemStack?): Boolean {
        if (stack == null || stack.isEmpty) return false
        val stackUuid = stack.skyblockUuid
        if (uuid != null && stackUuid != null && uuid == stackUuid && name.equals(stack.displayName.string, true)) return true

        val stackId = stack.skyblockId
        return id != null && stackId != null && id.equals(stackId, true)
    }

    companion object {
        fun from(stack: ItemStack?): HotbarItem {
            if (stack == null || stack.isEmpty) return HotbarItem()
            return HotbarItem(
                uuid = stack.skyblockUuid,
                id = stack.skyblockId,
                name = stack.displayName.string
            )
        }
    }
}
