package quoi.module.impl.misc

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.item.ItemStack
import quoi.api.commands.internal.BaseCommand
import quoi.api.commands.internal.GreedyString
import quoi.api.events.GuiEvent
import quoi.api.events.TickEvent
import quoi.api.input.CatKeys
import quoi.config.Config
import quoi.mixins.accessors.AbstractContainerScreenAccessor
import quoi.module.Module
import quoi.module.settings.impl.ListSetting
import quoi.utils.ChatUtils.modMessage
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.skyblock.ItemUtils.extraAttributes

object AutoSell : Module(
    "Auto Sell",
    desc = "Automatically sell items in trades and cookie menus. (/autosell)"
) {
    val sellList by ListSetting("Sell list", mutableSetOf<String>())
    private val delay by slider("Delay", 6, 2, 10, 1, desc = "The delay between each sell action.", unit = " ticks")
    private val randomization by slider("Randomization", 1, 0, 5, 1, desc = "Random delay variance", unit = " ticks")
    private val clickType by selector("Click Type", "Shift", listOf("Shift", "Middle", "Left"), desc = "The type of click to use when selling items.")
    private val inventoryToggleKey by keybind("Toggle hovered item", CatKeys.KEY_NONE, desc = "Adds or removes the hovered item from the auto sell list while an inventory is open.")
    private val addDefaults by button("Add defaults", desc = "Add default dungeon items to the auto sell list.") {
        sellList.addAll(defaultItems)
        modMessage("&aAdded default items to auto sell list")
        Config.save()
    }

    private val autoSellCommand = BaseCommand("autosell")

    private var last = 0L
    private var next = 0L
    private var inGui = false

    init {
        autoSellCommand.sub("add") { item: GreedyString? ->
            val lowercase = item?.string?.let(::normalizeSellEntry) ?: heldItemName()
                ?: return@sub modMessage("Either hold an item or write an item name to be added to autosell.")

            if (sellList.containsSellEntry(lowercase)) return@sub modMessage("$lowercase is already in the Auto sell list.")

            modMessage("Added \"$lowercase\" to the Auto sell list.")
            sellList.add(lowercase)
            Config.save()
        }.description("Adds an item to the auto sell list.")

        autoSellCommand.sub("remove") { item: GreedyString? ->
            val lowercase = item?.string?.let(::normalizeSellEntry) ?: heldItemName()
                ?: return@sub modMessage("Either hold an item or write an item name to be removed from autosell.")

            if (!sellList.removeSellEntry(lowercase)) return@sub modMessage("$lowercase isn't in the Auto sell list.")

            modMessage("Removed \"$lowercase\" from the Auto sell list.")
            Config.save()
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

        autoSellCommand.register()

        on<GuiEvent.Open.Post> {
            inGui = screen.title.string in menuTitles
        }

        on<GuiEvent.Close> {
            inGui = false
        }

        on<GuiEvent.Key> {
            if (key != inventoryToggleKey.key) return@on
            if (!inventoryToggleKey.isModifierDown()) return@on

            val stack = screen.cursorStack() ?: return@on
            if (stack.isEmpty) return@on
            val itemName = stack.sellListName()

            if (sellList.removeSellEntry(itemName)) {
                modMessage("Removed \"$itemName\" from the Auto sell list.")
            } else {
                sellList.add(itemName)
                modMessage("Added \"$itemName\" to the Auto sell list.")
            }

            Config.save()
        }

        on<TickEvent.Start> {
            if (sellList.isEmpty() || !inGui) return@on

            val menu = (mc.screen as? AbstractContainerScreen<*>)?.menu ?: return@on
            val now = System.currentTimeMillis()
            if (now - last < next) return@on

            for (slot in menu.slots) {
                if (slot.container !is Inventory) continue

                val stack = slot.item.takeIf { !it.isEmpty } ?: continue
                val name = stack.sellListName()

                if (!sellList.any { name.contains(normalizeSellEntry(it)) }) continue
                if (blacklist.any(name::contains)) continue

                mc.gameMode?.handleInventoryMouseClick(
                    menu.containerId,
                    slot.index,
                    clickButton(),
                    clickAction(),
                    player
                )
                last = now
                scheduleNextDelay()
                break
            }
        }
    }

    private fun scheduleNextDelay() {
        next = ((delay + (0..randomization).random()) * 50L)
    }

    private fun clickButton() = when (clickType.index) {
        1 -> 2
        else -> 0
    }

    private fun clickAction() = when (clickType.index) {
        0 -> ClickType.QUICK_MOVE
        1 -> ClickType.CLONE
        else -> ClickType.PICKUP
    }

    private fun normalizeSellEntry(name: String, reforge: String? = null): String =
        (reforge?.let { name.replace(it, "", true) } ?: name)
            .noControlCodes
            .replace(STACK_SIZE_REGEX, "")
            .trim()
            .replace("'", "")
            .lowercase()

    private fun Collection<String>.containsSellEntry(name: String): Boolean =
        any { normalizeSellEntry(it) == name }

    private fun MutableCollection<String>.removeSellEntry(name: String): Boolean =
        removeAll { normalizeSellEntry(it) == name }

    private fun heldItemName(): String? = mc.player?.mainHandItem?.takeIf { !it.isEmpty }?.sellListName()

    private fun ItemStack.sellListName(): String =
        normalizeSellEntry(customName?.string ?: hoverName.string, extraAttributes?.getString("modifier")?.orElse(null))

    private fun net.minecraft.client.gui.screens.Screen.cursorStack() = cursorSlot()?.item

    private fun net.minecraft.client.gui.screens.Screen.cursorSlot(): net.minecraft.world.inventory.Slot? {
        if (this !is InventoryScreen && this !is ContainerScreen) return null

        val window = mc.window ?: return null
        return (this as AbstractContainerScreenAccessor).`quoi$getSlotAtPos`(
            mc.mouseHandler.getScaledXPos(window),
            mc.mouseHandler.getScaledYPos(window)
        )
    }

    private val menuTitles = listOf("Trades", "Booster Cookie", "Farm Merchant", "Ophelia")
    private val STACK_SIZE_REGEX = Regex("^(?:[1-9]|[1-5]\\d|6[0-4])(?:\\s*[xX×])?\\s+|\\s+[xX×]?\\d+$")

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
