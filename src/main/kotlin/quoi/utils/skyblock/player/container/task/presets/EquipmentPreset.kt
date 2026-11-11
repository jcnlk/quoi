package quoi.utils.skyblock.player.container.task.presets

import net.minecraft.world.item.ItemStack
import quoi.QuoiMod.mc
import quoi.utils.skyblock.item.ItemUtils.cleanName
import quoi.utils.skyblock.item.ItemUtils.normalizeItemName
import quoi.utils.skyblock.player.container.task.ContainerTaskBuilder
import quoi.utils.skyblock.player.container.task.item
import quoi.utils.skyblock.player.container.task.resolve

private const val equipmentMenu = "Stats & Equipment"

/**
 * Equips an inventory item through /stats unless it is already equipped
 */
fun ContainerTaskBuilder.equipItem(name: String) {
    val normalizedName = name.normalizeItemName()
    val matches = { stack: ItemStack ->
        !stack.isEmpty && stack.cleanName.normalizeItemName().contains(normalizedName)
    }
    val equipped = {
        mc.player?.let { player ->
            (36..39).any { matches(player.inventory.getItem(it)) } ||
                item(matches).menu.resolve(player.containerMenu, player.inventory) != null
        } == true
    }

    check("Equipment name must not be empty") { normalizedName.isNotEmpty() }
    openContainer("stats", equipmentMenu)
    quickMove(item(matches).inv).onlyIf { !equipped() }
    awaitContainer(equipmentMenu, waitForItems = true, failureMessage = "Equipment swap was not confirmed: $name")
    check("Item was not equipped: $name", equipped)
    closeContainer()
}
