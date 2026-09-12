package quoi.utils.skyblock.player.container.task

import net.minecraft.world.item.ItemStack
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.Slot

/**
 * Represents a target slot for [ContainerTask]s
 */
sealed interface MenuSlot {
    val inContainer: Boolean? // true = container, false = inventory, null = vanilla
}

/**
 * Finds the slot in [menu] for this target
 * Inventory indices use the player inventory menu layout, including hotbar slots 36..44
 */
internal fun MenuSlot.resolve(menu: AbstractContainerMenu, inventory: Inventory): Slot? = when (this) {
    is IndexSlot -> when (inContainer) {
        null -> menu.slots.getOrNull(index)
        true -> menu.slots.getOrNull(index)?.takeIf { menu.containerId != 0 && it.container !== inventory }
        false -> if (menu.containerId == 0) {
            menu.slots.getOrNull(index)?.takeIf { it.container === inventory }
        } else {
            val inventoryIndex = when (index) {
                in 9..35 -> index
                in 36..44 -> index - 36
                else -> -1
            }
            menu.slots.firstOrNull { it.container === inventory && it.containerSlot == inventoryIndex }
        }
    }
    is ItemSlot -> menu.slots.firstOrNull {
        val inInventory = it.container === inventory
        val inRange = when (inContainer) {
            true -> menu.containerId != 0 && !inInventory
            false -> inInventory
            null -> true
        }
        inRange && !it.item.isEmpty && predicate(it.item)
    }
}

/**
 * specific slot by index
 */
class IndexSlot(val index: Int, override val inContainer: Boolean?) : MenuSlot

/**
 * searches for an item that matches [predicate]
 */
class ItemSlot(val predicate: (ItemStack) -> Boolean, override val inContainer: Boolean?) : MenuSlot {
    /**
     * Only searches in open container
     */
    val menu: ItemSlot get() = ItemSlot(predicate, true)

    /**
     * Only searches in inventory
     */
    val inv: ItemSlot get() = ItemSlot(predicate, false)
}

// container only
inline val Int.menu: MenuSlot get() = IndexSlot(this, true)
// inventory only
inline val Int.inv: MenuSlot get() = IndexSlot(this, false)

inline val String.menu: MenuSlot get() = item { it.displayName.string.contains(this, true) }.menu
inline val String.inv: MenuSlot get() = item { it.displayName.string.contains(this, true) }.inv

fun item(predicate: (ItemStack) -> Boolean) = ItemSlot(predicate, null)