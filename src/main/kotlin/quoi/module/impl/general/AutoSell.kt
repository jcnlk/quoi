package quoi.module.impl.general

import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import quoi.api.commands.internal.GreedyString
import quoi.api.events.GuiEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.on
import quoi.api.input.Keybinds
import quoi.config.Config
import quoi.mixins.accessors.AbstractContainerScreenAccessor
import quoi.module.Module
import quoi.module.settings.impl.ListSetting
import quoi.utils.ChatUtils.modMessage
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.skyblock.item.ItemUtils.extraAttributes
import quoi.utils.skyblock.item.ItemUtils.lore

object AutoSell : Module(
    "Auto Sell",
    desc = "Automatically sell items in trades and cookie menus. (/quoi autosell)"
) {
    val sellList by ListSetting("Sell list", mutableSetOf<String>())
    private val delay by slider("Delay", 6, 2, 10, 1, desc = "The delay between each sell action.", unit = " ticks")
    private val randomization by slider("Randomization", 1, 0, 5, 1, desc = "Random delay variance", unit = " ticks")
    private val clickType by selector("Click Type", "Shift", listOf("Shift", "Middle", "Left"), desc = "The type of click to use when selling items.")
    private val inventoryToggleKey by keybind("Toggle hovered item", Keybinds.KEY_NONE, desc = "Adds or removes the hovered item from the auto sell list while an inventory is open.")
    @Suppress("unused")
    private val addDefaults by button("Add defaults", desc = "Add default dungeon items to the auto sell list.") {
        sellList.addAll(defaultItems)
        modMessage("&aAdded default items to auto sell list")
        Config.save()
    }

    private var nextClick = 0L

    init {
        val autoSellCommand = command.sub("autosell").description("Auto Sell module settings.")

        autoSellCommand.sub("add") { item: GreedyString? ->
            setSellEntry(item?.string?.let(::normalizeSellEntry) ?: player.mainHandItem.sellListName(), add = true)
        }.description("Adds an item to the auto sell list.")

        autoSellCommand.sub("remove") { item: GreedyString? ->
            setSellEntry(item?.string?.let(::normalizeSellEntry) ?: player.mainHandItem.sellListName(), add = false)
        }.description("Removes an item from the auto sell list.").suggests("item") { sellList.toList() }

        autoSellCommand.sub("clear") {
            modMessage("Auto sell list cleared.")
            sellList.clear()
            Config.save()
        }.description("Clears the auto sell list.")

        autoSellCommand.sub("list") {
            if (sellList.isEmpty()) return@sub modMessage("Auto sell list is empty")
            val chunkedList = sellList.map(::normalizeSellEntry).distinct().chunked(10)
            modMessage("Auto sell list:\n${chunkedList.joinToString("\n")}")
        }.description("Shows the current auto sell list.")

        on<WorldEvent.Change> { nextClick = 0L }

        on<GuiEvent.Key.Press> {
            if (inventoryToggleKey.key == Keybinds.KEY_NONE) return@on
            if (key != inventoryToggleKey.key) return@on
            if (!inventoryToggleKey.isModifierDown()) return@on

            val name = screen.hoveredItemName() ?: return@on
            setSellEntry(name, add = !sellList.containsSellEntry(name))
        }

        on<TickEvent.Start> {
            if (sellList.isEmpty()) return@on
            val screen = mc.gui.screen() as? AbstractContainerScreen<*> ?: return@on
            if (screen.title.string !in menuTitles) return@on
            val menu = screen.menu
            if (!menu.isSellMenu()) return@on

            val now = System.currentTimeMillis()
            if (now < nextClick) return@on

            val slot = menu.slots.firstOrNull { it.container is Inventory && shouldSell(it.item) } ?: return@on
            val action = when (clickType.index) {
                0 -> ContainerInput.QUICK_MOVE
                1 -> ContainerInput.CLONE
                else -> ContainerInput.PICKUP
            }
            gameMode.handleContainerInput(
                menu.containerId,
                slot.index,
                if (clickType.index == 1) 2 else 0,
                action,
                player
            )
            nextClick = now + (delay + (0..randomization).random()) * 50L
        }
    }

    private fun AbstractContainerMenu.isSellMenu(): Boolean {
        val sellItem = slots.getOrNull(49)?.item ?: return false
        if (sellItem.item == Items.HOPPER && sellItem.hoverName.string.noControlCodes == "Sell Item") return true

        val lore = sellItem.lore ?: return false
        return lore.firstOrNull().noControlCodes == "Click items in your inventory to sell" ||
            lore.lastOrNull().noControlCodes == "Click to buyback!"
    }

    private fun shouldSell(stack: ItemStack): Boolean {
        val itemName = stack.sellListName() ?: return false
        return blacklist.none { itemName.matchesSellEntry(it) } && sellList.any { itemName.matchesSellEntry(it) }
    }

    private fun setSellEntry(name: String?, add: Boolean) {
        if (name.isNullOrEmpty()) return modMessage("Either hold an item or write an item name for autosell.")
        val changed = if (add) !sellList.containsSellEntry(name) && sellList.add(name) else sellList.removeSellEntry(name)
        if (!changed) return modMessage("$name ${if (add) "is already" else "isn't"} in the Auto sell list.")

        modMessage("${if (add) "Added" else "Removed"} \"$name\" ${if (add) "to" else "from"} the Auto sell list.")
        Config.save()
    }

    private fun normalizeSellEntry(name: String, reforge: String? = null): String {
        val normalized = name.noControlCodes
            .replace(UPGRADE_REGEX, "")
            .trim()
            .replace(STACK_SIZE_REGEX, "")
            .trim()
            .replace("'", "")
            .lowercase()
            .replace(WHITESPACE_REGEX, " ")
        val prefix = reforge?.replace('_', ' ')?.lowercase()?.trim()?.takeIf { it.isNotEmpty() }
        return if (prefix == null) normalized else normalized.removePrefix("$prefix ")
    }

    private fun String.matchesSellEntry(entry: String): Boolean =
        normalizeSellEntry(entry).let { it.isNotEmpty() && contains(it) }

    private fun Collection<String>.containsSellEntry(name: String): Boolean =
        any { normalizeSellEntry(it) == name }

    private fun MutableCollection<String>.removeSellEntry(name: String): Boolean =
        removeAll { normalizeSellEntry(it) == name }

    private fun ItemStack.sellListName(): String? {
        if (isEmpty) return null
        return normalizeSellEntry(customName?.string ?: hoverName.string, extraAttributes?.getString("modifier")?.orElse(null))
            .takeIf(String::isNotEmpty)
    }

    private fun Screen.hoveredItemName(): String? {
        if (this !is InventoryScreen && this !is ContainerScreen) return null
        val window = mc.window
        return (this as AbstractContainerScreenAccessor).`quoi$getSlotAtPos`(
            mc.mouseHandler.getScaledXPos(window),
            mc.mouseHandler.getScaledYPos(window)
        )?.item?.sellListName()
    }

    private val menuTitles = listOf("Trades", "Booster Cookie", "Farm Merchant", "Ophelia")

    private val STACK_SIZE_REGEX = Regex("^(?:[1-9]|[1-5]\\d|6[0-4])(?:\\s*[xX×])?\\s+|\\s+(?:[xX×]\\s*(?:[1-9]|[1-5]\\d|6[0-4])|(?:[1-9]|[1-5]\\d|6[0-4])\\s*[xX×])$")
    private val WHITESPACE_REGEX = Regex("\\s+")
    private val UPGRADE_REGEX = Regex("[✪➊➋➌➍➎⚚]")

    private val defaultItems = arrayOf(
        "enchanted ice", "superboom tnt", "rotten", "skeleton master", "skeleton grunt", "cutlass",
        "skeleton lord", "skeleton soldier", "zombie soldier", "zombie knight", "zombie commander", "zombie lord",
        "skeletor", "super heavy", "heavy", "sniper helmet", "dreadlord", "earth shard", "zombie commander whip",
        "machine gun", "sniper bow", "soulstealer bow", "silent death", "training weight",
        "beating heart", "premium flesh", "mimic fragment", "enchanted rotten flesh", "sign",
        "enchanted bone", "defuse kit", "optical lens", "tripwire hook", "button", "carpet", "lever", "diamond atom",
        "healing viii splash potion", "healing 8 splash potion", "candycomb"
    )

    private val blacklist = listOf("skeleton master chestplate")
}
