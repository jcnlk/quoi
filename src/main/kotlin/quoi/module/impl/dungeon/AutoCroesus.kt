package quoi.module.impl.dungeon

import quoi.api.events.core.on
import quoi.api.abobaui.dsl.*
import quoi.api.abobaui.elements.impl.Text.Companion.shadow
import quoi.api.abobaui.elements.impl.Text.Companion.textSupplied
import quoi.api.colour.Colour
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket
import net.minecraft.network.protocol.game.ServerboundInteractPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.Items
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import quoi.api.commands.internal.GreedyString
import quoi.api.events.GuiEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.input.CatKeyboard
import quoi.api.input.Keybinds
import quoi.api.skyblock.location.Island
import quoi.api.skyblock.SkyblockPrices
import quoi.api.skyblock.SkyblockPrices.BazaarPriceType
import quoi.config.configList
import quoi.module.Module
import quoi.module.impl.dungeon.autocroesus.AutoCroesusChestParser
import quoi.module.impl.dungeon.autocroesus.ChestData
import quoi.module.impl.dungeon.autocroesus.ClaimingChestInfo
import quoi.module.impl.dungeon.autocroesus.LoggedRun
import quoi.module.impl.dungeon.autocroesus.LootFilters
import quoi.module.impl.dungeon.autocroesus.LootSummaryItem
import quoi.module.impl.dungeon.autocroesus.autoCroesusChestNames
import quoi.module.impl.dungeon.autocroesus.autoCroesusChestSlots
import quoi.module.impl.dungeon.autocroesus.autoCroesusFloors
import quoi.module.impl.dungeon.autocroesus.cleanLore
import quoi.module.impl.dungeon.autocroesus.coinsFromMillions
import quoi.module.impl.dungeon.autocroesus.containsId
import quoi.module.impl.dungeon.autocroesus.defaultAutoCroesusAlwaysBuy
import quoi.module.impl.dungeon.autocroesus.defaultAutoCroesusWorthless
import quoi.module.impl.dungeon.autocroesus.displayNameFromId
import quoi.module.impl.dungeon.autocroesus.formatCoins
import quoi.module.impl.dungeon.autocroesus.orFalse
import quoi.module.impl.dungeon.autocroesus.parseRoman
import quoi.module.impl.dungeon.autocroesus.previewChestData
import quoi.module.impl.dungeon.autocroesus.profitText
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.impl.ListSetting
import quoi.utils.ChatUtils.modMessage
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.skyblock.item.ItemUtils.lore
import quoi.utils.skyblock.player.container.ContainerUtils.clickSlot
import kotlin.math.roundToInt

/**
 * TODO:
 *  better /quoi worthless and /quoi alwaysbuy handling
 */

// UnclaimedBloom6
// original: https://github.com/UnclaimedBloom6/RandomStuff/tree/main/AutoCroesus
object AutoCroesus : Module(
    "Auto Croesus",
    desc = "Automatically claims profitable Croesus dungeon chests.",
    area = Island.DungeonHub
) {
    private val clickDelay by slider("Click delay", 500L, 50L, 1_000L, 50L, desc = "Delay between automatic clicks.", unit = "ms")
    @Suppress("unused")
    private val profitOverlay by textHud("Auto Croesus profit", Colour.WHITE) {
        visibleIf { preview || inRunChest() }
        val data = if (preview) previewChestData else currentChestData
        column {
            data.forEach { chest ->
                textSupplied(
                    supplier = { "${chest.name} Chest §6(${formatCoins(chest.cost)}) ${profitText(chest.profit)}" },
                    colour = colour,
                    font = font,
                    size = 18.px,
                ).shadow = shadow

                chest.items.forEach { item ->
                    textSupplied(
                        supplier = { "  ${item.displayName} §a+${formatCoins(item.value * item.amount)}" },
                        colour = colour,
                        font = font,
                        size = 18.px,
                    ).shadow = shadow
                }
            }
        }
    }.setting()

    private val useChestKeys by switch("Use dungeon chest key", true, desc = "Claims a second profitable chest using a Dungeon Chest Key.")
    private val chestKeyMinProfit by slider("Chest key min profit", 0.2, 0.0, 10.0, 0.1, desc = "Minimum profit required to use a Dungeon Chest Key.", unit = "M")
        .childOf(::useChestKeys)
    private val bazaarPriceType by selector("Bazaar price", BazaarPriceType.InstaSell, desc = "Bazaar price used for Auto Croesus profit calculations.")
    private val useKismets by switch("Use kismets", false, desc = "Rerolls configured floors when Bedrock profit is below the threshold.")
    private val kismetMinProfit by slider("Kismet min profit", 2.0, 0.0, 10.0, 0.1, desc = "Bedrock chests below this profit are rerolled.", unit = "M")
        .childOf(::useKismets)
    @Suppress("unused")
    private val addDefaultAlwaysBuy by button("Add default always buy", desc = "Adds the original Auto Croesus always-buy defaults.") {
        addDefaults("Always buy", alwaysBuy, defaultAutoCroesusAlwaysBuy)
    }
    @Suppress("unused")
    private val addDefaultWorthless by button("Add default worthless", desc = "Adds the original Auto Croesus worthless defaults.") {
        addDefaults("Worthless", worthless, defaultAutoCroesusWorthless)
    }
    @Suppress("unused")
    private val startKey by keybind("Start key", Keybinds.KEY_NONE, desc = "Starts Auto Croesus.").onPress(::startFromKeybind)
    private val killSwitch by keybind("Kill switch", Keybinds.KEY_NONE, desc = "Stops the current Auto Croesus claim.")
    private val kismetFloors by multiSelect("Kismet floors", emptySet(), autoCroesusFloors, desc = "Floors where Auto Croesus may use Kismet Feathers.")
        .childOf(::useKismets)
    private val alwaysBuy by ListSetting("Always buy", mutableListOf<String>())
    private val worthless by ListSetting("Worthless", mutableListOf<String>())
    private val runLoot by configList<LoggedRun>("autocroesus_loot.json")

    private val croesusRegex = Regex("^(?:\\([1-3]/3\\) )?Croesus$")
    private val runChestRegex = Regex("^(?:Master )?Catacombs - Floor [IVX]+$")
    private val chestParser = AutoCroesusChestParser(worthless) { bazaarPriceType.selected }

    private val failedIndexes = mutableSetOf<Int>()
    private val loggedIndexes = mutableSetOf<Int>()
    private val log = mutableListOf<String>()
    private var claiming = false
    private var waitingForCroesus = false
    private var waitingForRunToOpen = false
    private var waitingForChestToOpen = false
    private var waitingOnPage = -1
    private var lastPageOn = -1
    private var tryingToKismet = false
    private var canKismet = true
    private var lastClick = -1L
    private var slotToClick = -1
    private var currentChest: ClaimingChestInfo? = null
    private var currentChestData: List<ChestData> = emptyList()

    init {
        registerCommands()

        on<TickEvent.Start> {
            if (claiming && killSwitch.key != Keybinds.KEY_NONE && CatKeyboard.isKeyDown(killSwitch.key)) {
                reset()
                modMessage("&cAuto Croesus stopped.")
                return@on
            }

            onClickTick()
            onClaimingTick()
            onCroesusTick()
            onRunChestTick()
            onChestTick()
        }

        on<GuiEvent.Slot.Draw> {
            if (slot.containerSlot == slotToClick) {
                ctx.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, -1)
                return@on
            }

            if (!inCroesus()) return@on
            val stack = slot.item
            if (stack.item != Items.PLAYER_HEAD) return@on
            val lore = stack.cleanLore()
            if (lore.any { it == "No chests opened yet!" }) {
                ctx.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0xAA00FF00.toInt())
            }
        }

        on<WorldEvent.Change> {
            reset()
        }
    }

    override fun onDisable() {
        reset()
        super.onDisable()
    }

    private fun registerCommands() {
        val ac = command.sub("autocroesus")
            .description("Auto Croesus controls.")
            .requires("&cAuto Croesus module is disabled!") { enabled }

        ac.sub("go") {
            startWithPriceUpdate()
        }.description("Updates prices if needed and starts claiming.")

        ac.sub("forcego") {
            log.clear()
            claiming = true
            modMessage("&aAuto Croesus activated without updating prices.")
        }.description("Starts claiming without updating prices.")

        ac.sub("api") {
            modMessage("&eUpdating Auto Croesus prices...")
            SkyblockPrices.update { success ->
                modMessage(if (success) "&aAuto Croesus prices updated." else "&cAuto Croesus price update failed.")
            }
        }.description("Refreshes Bazaar, item, and lowest-bin prices.")

        ac.sub("reset") {
            reset()
            modMessage("&aAuto Croesus reset.")
        }.description("Stops and clears current state.")

        ac.sub("copylog") {
            mc.keyboardHandler.clipboard = log.joinToString("\n")
            modMessage("&aAuto Croesus log copied.")
        }.description("Copies the debug log.")

        ac.sub("loot") {
            showLoot(LootFilters())
        }.description("Shows all logged Auto Croesus loot.")

        ac.sub("loot") { floor: String, score: Int?, limit: Int? ->
            showLoot(LootFilters(score = score ?: 0, floor = floor.uppercase(), limit = limit))
        }.description("Shows logged Auto Croesus loot. Optional: floor, minimum score, limit.")
            .suggests("floor", autoCroesusFloors)

        ac.sub("alwaysbuy") { arg: GreedyString? ->
            toggleListEntry("Always buy", alwaysBuy, arg?.string, defaultAutoCroesusAlwaysBuy)
        }.description("Lists or toggles an always-buy SkyBlock item id.")

        ac.sub("worthless") { arg: GreedyString? ->
            toggleListEntry("Worthless", worthless, arg?.string, defaultAutoCroesusWorthless)
        }.description("Lists or toggles a worthless SkyBlock item id.")
    }

    private fun startFromKeybind() {
        if (!enabled || mc.gui.screen() != null) return
        startWithPriceUpdate()
    }

    private fun startWithPriceUpdate() {
        log.clear()
        SkyblockPrices.ensureFresh { success ->
            if (!enabled) return@ensureFresh
            if (!success) return@ensureFresh modMessage("&cAuto Croesus could not update prices.")
            claiming = true
            modMessage("&aAuto Croesus activated.")
        }
    }

    private fun toggleListEntry(name: String, list: MutableList<String>, arg: String?, defaults: Set<String>) {
        val value = arg?.trim()?.uppercase()
        when {
            value.isNullOrBlank() -> modMessage("$name: ${list.joinToString(", ").ifBlank { "empty" }}")
            value == "RESET" -> {
                list.clear()
                list.addAll(defaults)
                modMessage("&a$name reset to defaults.")
            }
            list.any { it.equals(value, true) } -> {
                list.removeAll { it.equals(value, true) }
                modMessage("Removed $value from $name.")
            }
            else -> {
                list.add(value)
                modMessage("Added $value to $name.")
            }
        }
    }

    private fun addDefaults(name: String, list: MutableList<String>, defaults: Set<String>) {
        val before = list.size
        defaults.forEach { id ->
            if (list.none { it.equals(id, true) }) list.add(id)
        }
        modMessage("&aAdded ${list.size - before} default $name items.")
    }

    private fun showLoot(filters: LootFilters) {
        if (filters.score !in 0..317) {
            modMessage("&cScore must be between 0 and 317.")
            return
        }
        if (filters.limit != null && filters.limit <= 0) {
            modMessage("&cLimit must be a positive number.")
            return
        }
        if (filters.floor != null && !autoCroesusFloors.any { it.equals(filters.floor, true) }) {
            modMessage("&cFloor must be F1-F7 or M1-M7.")
            return
        }

        val filtered = runLoot.asReversed()
            .asSequence()
            .filter { filters.floor == null || it.floor.equals(filters.floor, true) }
            .filter { it.score >= filters.score }
            .let { seq -> filters.limit?.let(seq::take) ?: seq }
            .toList()

        if (filtered.isEmpty()) {
            modMessage("&cNo Auto Croesus loot matched those filters.")
            return
        }

        val loot = mutableMapOf<String, Int>()
        var totalChestCost = 0
        filtered.forEach { run ->
            totalChestCost += run.chestCost
            run.items.forEach { (id, amount) -> loot[id] = (loot[id] ?: 0) + amount }
        }

        var totalSellPrice = 0.0
        val itemInfo = loot.map { (id, amount) ->
            val value = SkyblockPrices.buyPrice(id, bazaarPriceType.selected) ?: 0.0
            totalSellPrice += value * amount
            LootSummaryItem(id, amount, value)
        }.sortedByDescending { it.totalValue }

        val totalProfit = totalSellPrice - totalChestCost
        val floorText = filters.floor ?: "All Floors"
        modMessage("&aLoot from &e${filtered.size} &aruns on &b$floorText&a:")
        itemInfo.take(10).forEach {
            modMessage("&b${it.amount}x &a${displayNameFromId(it.id)} &7(${formatCoins(it.unitValue.roundToInt())} each) = &6${formatCoins(it.totalValue.roundToInt())}")
        }
        if (itemInfo.size > 10) modMessage("&7... and ${itemInfo.size - 10} more item types.")
        modMessage("&cTotal Chest Cost: &6${formatCoins(totalChestCost)}")
        modMessage("&cTotal Sell Price: &6${formatCoins(totalSellPrice.roundToInt())}")
        modMessage("&eTotal Profit: &6${formatCoins(totalProfit.roundToInt())}")
        modMessage("&bProfit/Run: &6${formatCoins((totalProfit / filtered.size).roundToInt())}")
    }

    private fun onClickTick() {
        if (slotToClick == -1 || System.currentTimeMillis() - lastClick < clickDelay) return
        val container = container() ?: return
        if (container.menu.slots.size <= slotToClick) return

        log.add("clicking $slotToClick in ${container.title.string}")
        player.clickSlot(slotToClick, container.menu.containerId)
        lastClick = System.currentTimeMillis()
        slotToClick = -1
    }

    private fun onClaimingTick() {
        if (!claiming || waitingForCroesus || container() != null) return
        if (waitingForRunToOpen || waitingForChestToOpen) {
            modMessage("&cAuto Croesus went out of sync and reset.")
            log.add("out of sync")
            reset()
            return
        }
        startClaiming()
    }

    private fun onCroesusTick() {
        if (!inCroesus() || !claiming || waitingForRunToOpen) return
        waitingForCroesus = false

        val container = container() ?: return
        if (!invLoaded(container)) return
        val page = page()
        if (page == -1 || waitingOnPage != -1 && waitingOnPage != page) return

        val chest = currentChest
        if (chest != null && chest.runSlot != -1) {
            if (page != chest.page) {
                if (lastPageOn == page) return
                lastPageOn = page
                slotToClick = 53
                return
            }

            lastPageOn = -1
            slotToClick = chest.runSlot
            waitingForRunToOpen = true
            return
        }

        val (slot, floor) = unopenedChest(page)
        if (slot != null && floor != null) {
            currentChest = ClaimingChestInfo(floor, page, slot)
            waitingForRunToOpen = true
            slotToClick = slot
            return
        }

        if (container.menu.slots.getOrNull(53)?.item?.item == Items.ARROW) {
            if (lastPageOn == page) return
            lastPageOn = page
            waitingOnPage = page + 1
            slotToClick = 53
            return
        }

        modMessage("&aAuto Croesus finished. All chests looted.")
        reset()
        connection.send(ServerboundContainerClosePacket(container.menu.containerId))
        mc.gui.setScreen(null)
    }

    private fun onRunChestTick() {
        if (!claiming || !inRunChest()) return
        val container = container() ?: return
        if (!invLoaded(container) || waitingForChestToOpen) return

        waitingForRunToOpen = false
        lastPageOn = -1
        waitingOnPage = -1

        currentChest?.chestSlot?.takeIf { it != -1 }?.let {
            waitingForChestToOpen = true
            slotToClick = it
            currentChest?.chestSlot = -1
            return
        }

        val parsed = chestParser.parseChestData(container)
        if (parsed.error != null) {
            modMessage("&cAuto Croesus skipped this run: ${parsed.error}")
            currentChest?.let { failedIndexes.add(it.runSlot + (it.page - 1) * 54) }
            currentChest = null
            slotToClick = 30
            return
        }

        val data = parsed.chests
        if (data.isEmpty()) {
            currentChest?.let { failedIndexes.add(it.runSlot + (it.page - 1) * 54) }
            currentChest = null
            slotToClick = 30
            return
        }

        currentChestData = data.sortedByDescending { it.profit }
        val sorted = data.sortedWith(compareByDescending<ChestData> { it.items.any { item -> alwaysBuy.containsId(item.id) } }.thenByDescending { it.profit })
        val bedrock = data.firstOrNull { it.name == "Bedrock" }
        val chest = currentChest

        if (chest != null && bedrock != null && shouldKismet(chest, bedrock)) {
            tryingToKismet = true
            waitingForChestToOpen = true
            slotToClick = bedrock.slot
            return
        }

        val best = sorted.firstOrNull { it.profit > 0 || it.items.any { item -> alwaysBuy.containsId(item.id) } }
        if (best == null) {
            currentChest?.let { failedIndexes.add(it.runSlot + (it.page - 1) * 54) }
            currentChest = null
            slotToClick = 30
            return
        }

        modMessage("Claiming the ${best.name} Chest (${formatCoins(best.profit)} profit).")
        val second = sorted.getOrNull(1)
        if (second != null && useChestKeys && second.profit >= chestKeyMinProfit.coinsFromMillions()) {
            modMessage("Using a Dungeon Chest Key on the ${second.name} Chest (${formatCoins(second.profit)} profit).")
            currentChest?.chestSlot = second.slot
        }

        chest?.let {
            val runIndex = it.runSlot + (it.page - 1) * 54
            if (loggedIndexes.add(runIndex)) {
                log.add("loot ${it.floor}: ${data.joinToString { chestData -> "${chestData.name}:${chestData.profit}" }}")
                logRunLoot(it.floor, data.size, listOfNotNull(best, second?.takeIf { chest -> useChestKeys && chest.profit >= chestKeyMinProfit.coinsFromMillions() }))
            }
            failedIndexes.add(runIndex)
        }

        waitingForChestToOpen = true
        slotToClick = best.slot
    }

    private fun onChestTick() {
        if (!waitingForChestToOpen) return
        val container = container() ?: return
        if (!invLoaded(container) || container.menu.slots.size < 32) return
        val chestTitle = container.title.string.removeSuffix(" Chest")
        if (chestTitle !in autoCroesusChestNames) return
        waitingForChestToOpen = false

        if (tryingToKismet && chestTitle == "Bedrock") {
            val kismet = container.menu.slots.getOrNull(50)?.item
            tryingToKismet = false

            val lore = kismet.lore?.map { it.noControlCodes } ?: emptyList()
            if (kismet == null || kismet.hoverName.string.noControlCodes != "Reroll Chest" || lore.any { it.contains("Bring a Kismet Feather") }) {
                canKismet = false
                modMessage("&cNo Kismet Feather found. Auto Croesus disabled kismet claims for this session.")
                currentChest = null
                connection.send(ServerboundContainerClosePacket(container.menu.containerId))
                mc.gui.setScreen(null)
                return
            }

            if (lore.any { it.contains("You already rerolled a chest") }) {
                currentChest?.skipKismet = true
                waitingForRunToOpen = true
                slotToClick = 49
                return
            }

            currentChest?.skipKismet = true
            slotToClick = 50
            return
        }

        slotToClick = 31
        if (currentChest?.chestSlot == -1) currentChest = null
    }

    private fun shouldKismet(chest: ClaimingChestInfo, bedrock: ChestData): Boolean =
        useKismets &&
            canKismet &&
            !chest.skipKismet &&
            kismetFloors.any { it.equals(chest.floor, true) } &&
            bedrock.items.none { alwaysBuy.containsId(it.id) } &&
            bedrock.profit < kismetMinProfit.coinsFromMillions()

    private fun startClaiming() {
        claiming = true
        if (!clickCroesus()) {
            modMessage("&cAuto Croesus could not start. Look directly at Croesus and stay in interaction range.")
            reset()
            return
        }
        waitingForCroesus = true
    }

    private fun clickCroesus(): Boolean {
        val hitResult = mc.hitResult as? EntityHitResult ?: return false.also { log.add("hitResult was not an entity") }
        if (hitResult.type != HitResult.Type.ENTITY) return false
        val entity = hitResult.entity
        if (entity.uuid.version() != 2) return false
        val displayEntity = level.getEntities(entity, entity.boundingBox.expandTowards(1.0, 1.0, 1.0))
            .firstOrNull { it.customName?.string == "Croesus" && it.position() == entity.position() }
            ?: return false
        if (displayEntity.customName?.string != "Croesus") return false

        connection.send(
            ServerboundInteractPacket(entity.id, InteractionHand.MAIN_HAND, Vec3(0.0, 0.0, 0.0), player.isShiftKeyDown)
        )
        return true
    }

    private fun page(): Int {
        val slots = container()?.menu?.slots ?: return -1
        val prev = slots.getOrNull(45)?.item ?: return -1
        val next = slots.getOrNull(53)?.item ?: return -1
        val isNext = next.item == Items.ARROW
        val pageItem = if (isNext) next else prev.takeIf { it.item == Items.ARROW } ?: return 1
        val page = pageItem.lore?.firstOrNull { it.noControlCodes.startsWith("Page ") }
            ?.noControlCodes
            ?.removePrefix("Page ")
            ?.toIntOrNull()
            ?: return -1
        return page + if (isNext) -1 else 1
    }

    private fun unopenedChest(page: Int): Pair<Int?, String?> {
        val slots = container()?.menu?.slots ?: return null to null
        for (slot in autoCroesusChestSlots) {
            val index = slot + (page - 1) * 54
            if (index in failedIndexes) continue
            val stack = slots.getOrNull(slot)?.item ?: return null to null
            if (stack.item != Items.PLAYER_HEAD) continue
            val lore = stack.lore?.map { it.noControlCodes } ?: continue
            if (lore.none { it == "No chests opened yet!" }) continue
            val floorLine = lore.firstOrNull { it.startsWith("Floor ") } ?: run {
                failedIndexes.add(index)
                continue
            }
            val floorNum = floorLine.removePrefix("Floor ").toIntOrNull() ?: parseRoman(floorLine.removePrefix("Floor ")) ?: run {
                failedIndexes.add(index)
                continue
            }
            val prefix = if (stack.hoverName.string.noControlCodes == "Master Mode The Catacombs") "M" else "F"
            val floor = "$prefix$floorNum"
            if (!canKismet && kismetFloors.any { it.equals(floor, true) }) {
                failedIndexes.add(index)
                continue
            }
            return slot to floor
        }
        return null to null
    }

    private fun logRunLoot(floor: String, chestCount: Int, claimedChests: List<ChestData>) {
        if (claimedChests.isEmpty()) return
        val items = mutableMapOf<String, Int>()
        claimedChests.forEach { chest ->
            chest.items.forEach { item ->
                items[item.id] = (items[item.id] ?: 0) + item.amount
            }
        }
        runLoot.add(
            LoggedRun(
                floor = floor,
                score = scoreFromChestCount(chestCount),
                chestCost = claimedChests.sumOf { it.cost },
                items = items,
            )
        )
    }

    private fun scoreFromChestCount(chestCount: Int): Int = when (chestCount) {
        3 -> 229
        4 -> 230
        5 -> 270
        6 -> 300
        else -> 0
    }

    private fun inCroesus(): Boolean = mc.gui.screen()?.title?.string?.matches(croesusRegex) == true
    private fun inRunChest(): Boolean = mc.gui.screen()?.title?.string?.matches(runChestRegex) == true
    private fun container() = mc.gui.screen() as? AbstractContainerScreen<*>

    private fun invLoaded(container: AbstractContainerScreen<*>): Boolean {
        val slots = container.menu.slots
        return slots.size > 45 && !slots.getOrNull(slots.size - 45)?.item?.isEmpty.orFalse()
    }

    private fun reset() {
        claiming = false
        waitingForCroesus = false
        waitingForRunToOpen = false
        waitingForChestToOpen = false
        waitingOnPage = -1
        lastPageOn = -1
        tryingToKismet = false
        canKismet = true
        lastClick = -1L
        slotToClick = -1
        currentChest = null
        currentChestData = emptyList()
        failedIndexes.clear()
        loggedIndexes.clear()
    }
}
