package quoi.module.impl.general

import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import quoi.api.commands.internal.GreedyString
import quoi.api.events.GuiEvent
import quoi.api.events.core.on
import quoi.api.input.Keybinds
import quoi.config.Config
import quoi.module.Module
import quoi.utils.ChatUtils.modMessage
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.skyblock.item.ItemUtils.cleanName
import quoi.utils.skyblock.item.ItemUtils.getCleanLore
import quoi.utils.skyblock.item.ItemUtils.normalizeItemName
import quoi.utils.skyblock.item.ItemUtils.normalizedName
import quoi.utils.skyblock.player.container.ContainerSettings
import quoi.utils.skyblock.player.container.ContainerUtils.hoveredSlot
import quoi.utils.skyblock.player.container.task.containerTask
import quoi.utils.skyblock.player.container.task.inv

object AutoSell : Module(
    "Auto Sell",
    desc = "Automatically sell items in trades, cookie and merchant menus. (/quoi autosell)"
) {
    private val defaultItems = setOf(
        "enchanted ice", "superboom tnt", "rotten", "skeleton master", "skeleton grunt", "cutlass",
        "skeleton lord", "skeleton soldier", "zombie soldier", "zombie knight", "zombie commander", "zombie lord",
        "skeletor", "super heavy", "heavy", "sniper helmet", "dreadlord", "earth shard", "zombie commander whip",
        "machine gun", "sniper bow", "soulstealer bow", "silent death", "training weight", "beating heart",
        "premium flesh", "mimic fragment", "enchanted rotten flesh", "sign", "enchanted bone", "defuse kit",
        "optical lens", "tripwire hook", "button", "carpet", "lever", "diamond atom", "healing viii splash potion",
        "healing 8 splash potion", "candycomb", "earthen blade", "conjuring", "bouncy",
    )

    val sellList by stringList(
        "Sell list", mutableSetOf<String>(), desc = "Items containing one of these names are sold.",
        placeholder = "Add item name...", defaults = defaultItems
    ).normalises { it.normalizeItemName() }

    private val containerSettings = ContainerSettings(this)
    private val inventoryToggleKey by keybind("Toggle hovered item", Keybinds.KEY_NONE, desc = "Adds or removes the hovered item from the auto sell list while an inventory is open.")

    private val normalizedEntries get() = sellList.map { it.normalizeItemName() }.filter(String::isNotEmpty).distinct()

    private val sellTask = containerTask(
        name = name,
        settings = containerSettings,
        menu = {
            sellList.isNotEmpty() && it.type == MenuType.GENERIC_9x6 && it.title.string.noControlCodes in sellMenus
        },
    ) {
        val entries = normalizedEntries
        val candidates = ((9..35) + (0..8)).filter { shouldSell(player.inventory.getItem(it), entries) }
        if (candidates.isNotEmpty()) {
            check("Not a sell menu") { player.containerMenu.isSellMenu() }
            for (inventoryIndex in candidates) {
                val inventorySlot = if (inventoryIndex in 0..8) inventoryIndex + 36 else inventoryIndex
                quickMove(inventorySlot.inv).unless { !shouldSell(it, normalizedEntries) }
            }
        }
    }

    init {
        val autoSellCommand = command.sub("autosell").description("Auto Sell module settings.")

        autoSellCommand.sub("add") { item: GreedyString? ->
            setSellEntry(item?.string ?: player.mainHandItem.normalizedName, add = true)
        }.description("Adds an item to the auto sell list.")

        autoSellCommand.sub("remove") { item: GreedyString? ->
            setSellEntry(item?.string ?: player.mainHandItem.normalizedName, add = false)
        }.description("Removes an item from the auto sell list.").suggests("item") { sellList.toList() }

        autoSellCommand.sub("clear") {
            modMessage("Auto sell list cleared.")
            sellList.clear()
            Config.save()
        }.description("Clears the auto sell list.")

        autoSellCommand.sub("list") {
            if (sellList.isEmpty()) return@sub modMessage("Auto sell list is empty")
            val chunkedList = normalizedEntries.chunked(10)
            modMessage("Auto sell list:\n${chunkedList.joinToString("\n")}")
        }.description("Shows the current auto sell list.")

        on<GuiEvent.Key.Press> {
            if (inventoryToggleKey.key == Keybinds.KEY_NONE) return@on
            if (key != inventoryToggleKey.key) return@on
            if (!inventoryToggleKey.isModifierDown()) return@on
            if (screen !is InventoryScreen && screen !is ContainerScreen) return@on

            val name = screen.hoveredSlot?.item.normalizedName ?: return@on
            setSellEntry(name, add = name !in normalizedEntries)
        }
    }

    override fun onDisable() {
        sellTask.cancel()
        super.onDisable()
    }

    private fun AbstractContainerMenu.isSellMenu(): Boolean {
        val sellItem = slots.getOrNull(49)?.item ?: return false
        if (sellItem.item == Items.HOPPER && sellItem.cleanName == "Sell Item") return true

        val lore = sellItem.getCleanLore()
        return lore.firstOrNull() == "Click items in your inventory to sell" || lore.lastOrNull() == "Click to buyback!"
    }

    private fun shouldSell(stack: ItemStack, entries: List<String>): Boolean {
        val itemName = stack.normalizedName ?: return false
        return "skeleton master chestplate" !in itemName && entries.any(itemName::contains) // TODO: allow not 50/50 to be sold
    }

    private fun setSellEntry(value: String?, add: Boolean) {
        val name = value?.normalizeItemName()?.takeIf(String::isNotEmpty)
            ?: return modMessage("Either hold an item or write an item name for autosell.")
        val changed = if (add) name !in normalizedEntries && sellList.add(name)
        else sellList.removeAll { it.normalizeItemName() == name }
        if (!changed) return modMessage("$name ${if (add) "is already" else "isn't"} in the Auto sell list.")

        modMessage("${if (add) "Added" else "Removed"} \"$name\" ${if (add) "to" else "from"} the Auto sell list.")
        Config.save()
    }

    // we love hypixel wiki: https://hypixelskyblock.minecraft.wiki/api.php?action=query&list=embeddedin&eititle=Template%3AShop_UI&eilimit=500&format=json
    private val sellMenus = setOf(
        "Abiphone Flip", "Abiphone Flip+", "Abiphone X", "Abiphone XI", "Abiphone XII", "Abiphone XIII", "Abiphone XIV",
        "Adventurer", "Agatha's Shop", "Agnes", "Alan", "Albert", "Alchemist", "Alpha Ophelia", "Amaury", "Amelia",
        "An", "Ant", "Armorsmith", "Baker", "Barbarian Shop", "Bartender", "Bea", "Bingo Shop", "Booster Cookie",
        "Booster Upgrades", "Brynmor's Shop", "Bubu", "Bulvar", "Celeste Set", "Chocolate Shop", "Combat Merchant",
        "Drakuu", "Duncan", "Einary's Emporium", "Ezekiel", "Fear Mongerer", "Fish Merchant", "Fishing Merchant",
        "Forge Arrows", "Gold Forger", "Green Thumb", "Gregory", "Grog", "Hilda", "Husbandry Merchant", "Iron Armor",
        "Iron Forger", "Joyful Viking", "Junker Joel", "Kiara", "Legacy Trades", "Librarian", "Liz's Stock Redemption",
        "Lukas the Aquarist", "Lumber Merchant", "Lumina", "Lys", "Mad Redstone Engineer", "Mage Shop", "Malmar",
        "Marthos", "Master Tactician", "Mercenary Armor", "Mine Merchant", "Mining Merchant", "Mino", "Moby", "Nemo",
        "Odawa", "Old Shaman Nyko", "Ophelia", "Oringo - Traveling Zookeeper", "Pat", "Pesthunter's Wares",
        "Plumber Joe", "Rift Bartender", "Rocks & Bricks", "Rosetta's Armor", "Sanger's Shop", "Sherry", "Shifty",
        "SkyMart Barn Skins", "SkyMart Farming Essentials", "SkyMart Farming Tools", "SkyMart Greenhouse Skins",
        "SkyMart Pests", "Smithmonger", "Sorcerer Okron", "Squire Armor", "Starlight Set", "Talbot", "Terry",
        "Tomioka", "Tony's Shop", "Trades", "Trinity", "Ulyn", "Unhinged Kloon", "Vargul", "Variety", "Weaponsmith",
        "Winona", "Woodworking", "Wool Weaver (Cool)", "Wool Weaver (Vibrant)", "Zog",
    )
}
