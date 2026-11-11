package quoi.utils.skyblock.player.container.task.presets

import quoi.QuoiMod.mc
import quoi.utils.skyblock.item.ItemUtils.getCleanLore
import quoi.utils.skyblock.player.container.task.ContainerTaskBuilder
import quoi.utils.skyblock.player.container.task.menu

private const val loadoutMenu = "(1/3) Loadouts"
private val loadoutSlots = listOf(14, 15, 16, 23, 24, 25, 32, 33, 34, 41, 42, 43)

/**
 * Equips a loadout slot from 1 to 12 on the first page
 */
fun ContainerTaskBuilder.equipLoadout(slot: Int) {
    val targetSlot = loadoutSlots.getOrNull(slot - 1)
    check("Invalid loadout slot. Use a slot from 1 to ${loadoutSlots.size}") { targetSlot != null }
    if (targetSlot == null) return

    openContainer("loadout", loadoutMenu)
    check("Loadout slot $slot is not equipable") {
        mc.player?.containerMenu?.items?.getOrNull(targetSlot)?.getCleanLore()
            ?.any { it.contains("Left-click to equip!") } == true
    }
    pickup(targetSlot.menu)
    awaitContainer(loadoutMenu, waitForItems = true, failureMessage = "Loadout swap was not confirmed: $slot")
    closeContainer()
}
