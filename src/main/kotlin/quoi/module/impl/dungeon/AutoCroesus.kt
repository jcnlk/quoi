package quoi.module.impl.dungeon

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.item.Items
import quoi.api.commands.internal.GreedyString
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.on
import quoi.api.input.Keybinds
import quoi.api.input.Keyboard
import quoi.api.skyblock.SkyblockPrices
import quoi.api.skyblock.SkyblockPrices.BazaarPriceType
import quoi.api.skyblock.location.Island
import quoi.config.configList
import quoi.module.Module
import quoi.module.impl.dungeon.autocroesus.*
import quoi.module.settings.Setting.Companion.json
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.utils.ChatUtils.modMessage
import quoi.utils.EntityUtils.getEntities
import quoi.utils.romanToInt
import quoi.utils.skyblock.item.ItemUtils.cleanName
import quoi.utils.skyblock.item.ItemUtils.getCleanLore
import quoi.utils.skyblock.player.container.IContainerSettings
import quoi.utils.skyblock.player.container.task.*
import quoi.utils.skyblock.player.interact.AuraAction
import quoi.utils.skyblock.player.interact.AuraManager
import kotlin.math.roundToLong

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
    private val clickDelay by rangeSlider("Click delay", 8 to 10, 1, 20, unit = "t").json("Click delay ticks")
    private val containerSettings = object : IContainerSettings {
        override val startDelay get() = clickDelay
        override val clickDelay get() = this@AutoCroesus.clickDelay
        override val invWalk = false
        override val blockInput = false
        override val showProgress = false
    }

    private val useChestKeys by switch("Use dungeon chest key", desc = "Claims a second profitable chest using a Dungeon Chest Key.")
    private val chestKeyMinProfit by slider("Chest key min profit", 0.2, 0.0, 10.0, 0.1, desc = "Minimum profit required to use a Dungeon Chest Key.", unit = "M")
        .childOf(::useChestKeys)
    private val bazaarPriceType by selector("Bazaar price", BazaarPriceType.InstaSell, desc = "Bazaar price used for Auto Croesus profit calculations.")
    private val useKismets by switch("Use kismets", desc = "Rerolls configured floors when Bedrock profit is below the threshold.")
    private val kismetMinProfit by slider("Kismet min profit", 2.0, 0.0, 10.0, 0.1, desc = "Bedrock chests below this profit are rerolled.", unit = "M")
        .childOf(::useKismets)

    @Suppress("unused")
    private val startKey by keybind("Start key", Keybinds.KEY_NONE, desc = "Starts Auto Croesus.").onPress(::startFromKeybind)
    private val killSwitch by keybind("Kill switch", Keybinds.KEY_NONE, desc = "Stops the current Auto Croesus claim.")
    private val kismetFloors by multiSelect("Kismet floors", emptySet(), autoCroesusFloors, desc = "Floors where Auto Croesus may use Kismet Feathers.")
        .childOf(::useKismets)
    private val alwaysBuy by stringList(
        "Always buy", mutableListOf(), desc = "Item ids that are always claimed.",
        placeholder = "Add item id...", defaults = defaultAutoCroesusAlwaysBuy
    ).normalises { it.uppercase() }
    private val worthless by stringList(
        "Worthless", mutableListOf(), desc = "Item ids counted as worth nothing.",
        placeholder = "Add item id...", defaults = defaultAutoCroesusWorthless
    ).normalises { it.uppercase() }

    private val runLoot by configList<LoggedRun>("autocroesus_loot.json")

    private val croesusRegex = Regex("""^(?:\(\d/\d\) )?Croesus$""") // https://github.com/Noamm9/NoammAddons/blob/771d2702200bcdabf2bdc7d5256ec327b6d5a2c7/src/main/kotlin/com/github/noamm9/features/impl/dungeon/ChestProfit.kt#L46
    private val runChestRegex = Regex("^(Master )?Catacombs - Flo(or (IV|V?I{0,3}))?$") // https://github.com/Noamm9/NoammAddons/blob/771d2702200bcdabf2bdc7d5256ec327b6d5a2c7/src/main/kotlin/com/github/noamm9/features/impl/dungeon/ChestProfit.kt#L45
    private val rewardChestRegex = Regex("^(?:${autoCroesusChestNames.joinToString("|")})$")
    private val chestParser = AutoCroesusChestParser(worthless) { bazaarPriceType.selected }

    private val log = mutableListOf<String>()
    private val failedRuns = mutableSetOf<Int>()
    private val rerolledRuns = mutableSetOf<Int>()
    private var keyChest: Pair<Int, Int>? = null
    private var canKismet = true
    private var pendingStart: Any? = null
    private var task: ContainerTask? = null
    private var activeCycle: Cycle? = null

    private val claiming: Boolean get() = task != null

    init {
        registerCommands()

        ScreenEvents.AFTER_INIT.register { _, screen, width, height -> // TODO: use a proper event for this or other shit
            if (!active || screen !is ContainerScreen || !screen.title.string.matches(croesusRegex)) return@register

            val chestRight = (width + 176) / 2
            val chestTop = (height - 114 - 18 * screen.menu.rowCount) / 2
            val buttonWidth = 64
            val button = Button.builder(Component.literal("Start Auto")) { startWithPriceUpdate(screen) } // TODO: use use apply instead (someday)
                .bounds(chestRight - buttonWidth - 6, chestTop + 2, buttonWidth, 14)
                .tooltip(Tooltip.create(Component.literal("Start Auto Croesus")))
                .build()

            button.active = canStartFrom(screen)
            Screens.getWidgets(screen).add(button)
            ScreenEvents.afterTick(screen).register { button.active = canStartFrom(screen) }
        }

        on<TickEvent.Start> {
            if ((!claiming && pendingStart == null) || killSwitch.key == Keybinds.KEY_NONE || !Keyboard.isKeyDown(killSwitch.key)) return@on
            reset()
            modMessage("&cAuto Croesus stopped.")
        }

        on<WorldEvent.Change> { reset() }
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
            if (!canStartFrom(null)) return@sub
            log.clear()
            startClaiming()
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

    private fun canStartFrom(screen: ContainerScreen?): Boolean =
        enabled && !claiming && pendingStart == null && (screen == null || active && mc.gui.screen() === screen)

    private fun startWithPriceUpdate(screen: ContainerScreen? = null) {
        if (!canStartFrom(screen)) return
        val request = Any()
        pendingStart = request
        log.clear()
        SkyblockPrices.ensureFresh { success ->
            if (pendingStart !== request) return@ensureFresh
            pendingStart = null
            if (!canStartFrom(screen)) return@ensureFresh
            if (!success) return@ensureFresh modMessage("&cAuto Croesus could not update prices.")
            startClaiming()
            modMessage("&aAuto Croesus activated.")
        }
    }

    private fun startClaiming() {
        reset()
        if (player.containerMenu !== player.inventoryMenu) player.closeContainer()
        claimNext()
    }

    private fun claimNext() {
        val cycle = Cycle()
        activeCycle = cycle
        val next = containerTask(name = name, settings = containerSettings, reportErrors = false) {
            check("Croesus is not within 4 blocks.") {
                clickCroesus().also { if (it) task?.ticksSinceLastClick = 0 } // the first menu click waits the click delay too
            }
            awaitContainer(croesusRegex, waitForItems = true, failureMessage = "Croesus did not open.")
            repeat(2) {
                pickup(53.menu).onlyIf { findRun() == null && player.containerMenu.slots.getOrNull(53)?.item?.item == Items.ARROW }
                awaitContainer(croesusRegex, waitForItems = true)
            }
            check("All chests looted") { cycle.selectRun() }
            pickup(slot { cycle.run!!.slot })
            awaitContainer(runChestRegex, waitForItems = true)
            check("Next run") { cycle.chooseChest() }
            pickup(slot { cycle.chestSlot })
            awaitContainer(rewardChestRegex, waitForItems = true)
            check("Next run") { cycle.chooseAction() }
            pickup(slot { cycle.finalSlot })
            onFinished { result -> onCycleFinished(cycle, result) }
        }
        task = next
        next.run()
    }

    private fun onCycleFinished(cycle: Cycle, result: ContainerTaskResult) {
        if (activeCycle !== cycle) return // stopped or replaced
        activeCycle = null
        task = null
        when (result) {
            ContainerTaskResult.Success -> claimNext()
            ContainerTaskResult.Cancelled -> reset()
            is ContainerTaskResult.Failure if (result.message == "Next run" || result.message == "All chests looted" && cycle.keyMissing) -> claimNext()
            is ContainerTaskResult.Failure if result.message == "All chests looted" -> {
                modMessage("&aAuto Croesus finished. All chests looted.")
                reset()
            }
            else -> {
                val reason = (result as? ContainerTaskResult.Failure)?.message ?: "another container action is in progress"
                log.add("stopped: $reason")
                modMessage("&cAuto Croesus stopped: $reason")
                reset()
            }
        }
    }

    private fun slot(index: () -> Int) = item { it === player.containerMenu.slots.getOrNull(index())?.item }.menu

    private class Run(val slot: Int, val index: Int, val floor: String)

    private class Cycle {
        var run: Run? = null
        var chestSlot = -1
        var finalSlot = -1
        var kismet = false
        var keyMissing = false

        fun selectRun(): Boolean {
            run = findRun()
            if (run == null && keyChest != null) {
                keyChest = null // its run is gone, so continue with the remaining runs
                keyMissing = true
            }
            return run != null
        }

        fun chooseChest(): Boolean {
            val run = run ?: return false
            keyChest?.takeIf { it.first == run.index }?.let { (_, slot) ->
                keyChest = null
                chestSlot = slot
                return true
            }

            val parsed = chestParser.parseChestData(player.containerMenu)
            parsed.error?.let { modMessage("&cAuto Croesus skipped this run: $it") }
            val data = parsed.chests
            failedRuns.add(run.index)
            if (parsed.error != null || data.isEmpty()) return false

            val sorted = data.sortedWith(compareByDescending<ChestData> { it.items.any { item -> alwaysBuy.containsId(item.id) } }.thenByDescending { it.profit })
            val bedrock = data.firstOrNull { it.name == "Bedrock" }

            if (bedrock != null && shouldKismet(run, bedrock)) {
                failedRuns.remove(run.index)
                kismet = true
                chestSlot = bedrock.slot
                return true
            }

            val best = sorted.first() // the Wood chest is free, so every run has a chest worth claiming
            modMessage("Claiming the ${best.name} Chest (${formatCoins(best.profit)} profit).")
            val second = sorted.getOrNull(1)?.takeIf { useChestKeys && it.profit >= chestKeyMinProfit.coinsFromMillions() }
            if (second != null) {
                modMessage("Using a Dungeon Chest Key on the ${second.name} Chest (${formatCoins(second.profit)} profit).")
                keyChest = run.index to second.slot
            }

            log.add("loot ${run.floor}: ${data.joinToString { "${it.name}:${it.profit}" }}")
            logRunLoot(run.floor, data.size, listOfNotNull(best, second))
            chestSlot = best.slot
            return true
        }

        fun chooseAction(): Boolean {
            if (!kismet) {
                finalSlot = 31
                return true
            }
            rerolledRuns.add(run!!.index)
            val kismetItem = player.containerMenu.slots.getOrNull(50)?.item
            val lore = kismetItem.getCleanLore()
            if (kismetItem == null || kismetItem.cleanName != "Reroll Chest" || lore.any { it.contains("Bring a Kismet Feather") }) {
                canKismet = false
                modMessage("&cNo Kismet Feather found. Auto Croesus skips kismet floors for this session.")
                return false
            }
            if (lore.any { it.contains("You already rerolled a chest") }) return false
            finalSlot = 50
            return true
        }
    }

    private fun shouldKismet(run: Run, bedrock: ChestData): Boolean =
        useKismets &&
                canKismet &&
                run.index !in rerolledRuns &&
                kismetFloors.any { it.equals(run.floor, true) } &&
                bedrock.items.none { alwaysBuy.containsId(it.id) } &&
                bedrock.profit < kismetMinProfit.coinsFromMillions()

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

    private fun showLoot(filters: LootFilters) {
        if (filters.score !in 0..317) { // TODO: update max score shit
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

        val loot = mutableMapOf<String, Long>()
        var totalChestCost = 0L
        filtered.forEach { run ->
            totalChestCost += run.chestCost
            run.items.forEach { (id, amount) -> loot[id] = (loot[id] ?: 0L) + amount }
        }

        var totalSellPrice = 0.0
        val itemInfo = loot.map { (id, amount) ->
            val value = SkyblockPrices.buyPrice(id, bazaarPriceType.selected) ?: 0.0
            totalSellPrice += value * amount
            LootSummaryItem(id, amount, value)
        }.sortedByDescending { it.totalValue }

        val totalProfit = totalSellPrice - totalChestCost
        val floorText = filters.floor ?: "All Floors"
        val lines = buildList {
            add("&aLoot from &e${filtered.size} &aruns on &b$floorText&a:")
            itemInfo.take(10).forEach {
                add("&b${it.amount}x &a${displayNameFromId(it.id)} &7(${formatCoins(it.unitValue.roundToLong())} each) = &6${formatCoins(it.totalValue.roundToLong())}")
            }
            if (itemInfo.size > 10) add("&7... and ${itemInfo.size - 10} more item types.")
            add("&cTotal Chest Cost: &6${formatCoins(totalChestCost)}")
            add("&cTotal Sell Price: &6${formatCoins(totalSellPrice.roundToLong())}")
            add("&eTotal Profit: &6${formatCoins(totalProfit.roundToLong())}")
            add("&bProfit/Run: &6${formatCoins((totalProfit / filtered.size).roundToLong())}")
        }
        modMessage(lines.joinToString("\n"))
    }

    private fun clickCroesus(): Boolean {
        val nameTag = getEntities<Entity>(4.0) { it.customName?.string == "Croesus" && it.distanceTo(player) <= 4.0 }.firstOrNull() ?: return false
        val croesus = getEntities<Player>(nameTag.position(), 1.0) { it.uuid.version() == 2 && it.position() == nameTag.position() }.singleOrNull() ?: return false
        AuraManager.interactEntity(croesus, AuraAction.INTERACT_AT)
        return true
    }

    private fun page(menu: AbstractContainerMenu): Int {
        val slots = menu.slots
        val prev = slots.getOrNull(45)?.item ?: return -1
        val next = slots.getOrNull(53)?.item ?: return -1
        val isNext = next.item == Items.ARROW
        val pageItem = if (isNext) next else prev.takeIf { it.item == Items.ARROW } ?: return 1
        val page = pageItem.getCleanLore().firstOrNull { it.startsWith("Page ") }
            ?.removePrefix("Page ")
            ?.toIntOrNull()
            ?: return -1
        return page + if (isNext) -1 else 1
    }

    private fun findRun(): Run? {
        val menu = player.containerMenu
        val page = page(menu).takeIf { it != -1 } ?: return null
        val key = keyChest
        for (slot in autoCroesusChestSlots) {
            val index = slot + (page - 1) * 54
            if (if (key != null) index != key.first else index in failedRuns) continue
            val stack = menu.slots.getOrNull(slot)?.item ?: return null
            if (stack.item != Items.PLAYER_HEAD) continue
            val lore = stack.getCleanLore()
            if (key == null && lore.none { it == "No chests opened yet!" }) continue
            val floorNum = lore.firstOrNull { it.startsWith("Floor ") }?.let { romanToInt(it.removePrefix("Floor ")) } ?: run {
                failedRuns.add(index)
                continue
            }
            val floor = (if (stack.cleanName == "Master Mode The Catacombs") "M" else "F") + floorNum
            if (key == null && !canKismet && kismetFloors.any { it.equals(floor, true) }) {
                failedRuns.add(index)
                continue
            }
            return Run(slot, index, floor)
        }
        return null
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

    private fun reset() {
        pendingStart = null
        val current = task
        task = null
        activeCycle = null
        current?.cancel()
        failedRuns.clear()
        rerolledRuns.clear()
        keyChest = null
        canKismet = true
    }
}
