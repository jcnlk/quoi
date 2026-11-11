package quoi.utils.skyblock.player.container.task.presets

import net.minecraft.world.item.ItemStack
import quoi.QuoiMod.mc
import quoi.utils.skyblock.item.ItemUtils.cleanName
import quoi.utils.skyblock.player.container.task.ContainerTaskBuilder
import quoi.utils.skyblock.player.container.task.menu

private const val wardrobeMenu = "(1/3) Armor Sets"

/**
 * Equips a wardrobe slot from 1 to 9, skipping an already equipped set unless [toggle] is true
 * @param toggle unequips the set if it is already equipped
 */
fun ContainerTaskBuilder.equipWardrobe(slot: Int, toggle: Boolean = false) {
    check("Invalid wardrobe slot. Use a slot from 1 to 9") { slot in 1..9 }
    if (slot !in 1..9) return

    val targetSlot = slot + 35
    var initialState = WardrobeState.UNKNOWN
    val state = { mc.player?.containerMenu?.items?.getOrNull(targetSlot)?.wardrobeState() ?: WardrobeState.UNKNOWN }

    openContainer("wardrobe", wardrobeMenu)
    action { initialState = state() }
    check("Wardrobe slot $slot is empty") { initialState != WardrobeState.EMPTY }
    check("Wardrobe slot $slot is locked") { initialState != WardrobeState.LOCKED }
    check("Wardrobe slot $slot is not ready") { initialState != WardrobeState.UNKNOWN }
    pickup(targetSlot.menu).unless { !toggle && it.wardrobeState() == WardrobeState.EQUIPPED }
    awaitContainer(wardrobeMenu, waitForItems = true, failureMessage = "Wardrobe swap was not confirmed: $slot")
    check("Wardrobe slot $slot did not change to the requested state") {
        state() == if (toggle && initialState == WardrobeState.EQUIPPED) WardrobeState.READY else WardrobeState.EQUIPPED
    }
    closeContainer()
}

private fun ItemStack.wardrobeState(): WardrobeState = when {
    cleanName.endsWith(": Empty") -> WardrobeState.EMPTY
    cleanName.endsWith(": Equipped") -> WardrobeState.EQUIPPED
    cleanName.endsWith(": Ready") -> WardrobeState.READY
    cleanName.endsWith(": Locked") -> WardrobeState.LOCKED
    else -> WardrobeState.UNKNOWN
}

private enum class WardrobeState { EQUIPPED, READY, EMPTY, LOCKED, UNKNOWN }
