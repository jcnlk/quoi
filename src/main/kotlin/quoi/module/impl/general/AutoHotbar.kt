package quoi.module.impl.general

import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import net.minecraft.client.KeyMapping
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket
import quoi.QuoiMod
import quoi.api.abobaui.dsl.px
import quoi.api.abobaui.elements.impl.Text.Companion.shadow
import quoi.api.abobaui.elements.impl.Text.Companion.textSupplied
import quoi.api.colour.Colour
import quoi.api.commands.internal.GreedyString
import quoi.api.events.*
import quoi.api.events.core.on
import quoi.api.skyblock.dungeon.Dungeon
import quoi.module.Module
import quoi.module.settings.impl.ListSetting
import quoi.utils.ChatUtils.modMessage
import quoi.utils.ChatUtils.prefix
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.Scheduler.wait
import quoi.utils.skyblock.player.EquipmentSwapper
import quoi.utils.skyblock.player.HotbarItem
import quoi.utils.skyblock.player.HotbarPreset
import quoi.utils.skyblock.player.HotbarSwapperUtils
import quoi.utils.skyblock.player.HotbarSwapperUtils.HOTBAR_END
import quoi.utils.skyblock.player.HotbarSwapperUtils.HOTBAR_START
import quoi.utils.skyblock.player.HotbarSwapperUtils.NOT_FOUND
import quoi.utils.skyblock.player.HotbarSwapperUtils.beginSwap
import quoi.utils.skyblock.player.HotbarSwapperUtils.captureHotbar
import quoi.utils.skyblock.player.HotbarSwapperUtils.endSwap
import quoi.utils.skyblock.player.HotbarSwapperUtils.findEmptyInventorySlot
import quoi.utils.skyblock.player.HotbarSwapperUtils.findMatchingInventorySlot
import quoi.utils.skyblock.player.HotbarSwapperUtils.findPreset
import quoi.utils.skyblock.player.HotbarSwapperUtils.isSwapping
import quoi.utils.skyblock.player.HotbarSwapperUtils.swapWithHotbar
import quoi.utils.skyblock.player.MovementUtils.stop
import quoi.utils.skyblock.player.PlayerUtils.suppressItemUse
import quoi.utils.skyblock.player.PlayerUtils.restoreItemUse
import quoi.utils.ui.hud.impl.TextHud
import kotlin.random.Random

object AutoHotbar : Module(
    "Auto Hotbar",
    desc = "Saves and equips hotbar presets."
) {
    private val blockInput by switch("Block input", false, desc = "Blocks keyboard and mouse input during hotbar correction passes.")
    private val helmetTimeout by slider("Helmet timeout", 100, 20, 400, 20, desc = "Maximum client ticks to wait for each helmet equipment step when the server is slow.")
    private val swapPassDelay by slider("Swap pass delay", 5, 0, 20, 1, desc = "Base delay in ticks before each hotbar correction pass.")
    private val passDelayRandomness by slider("Pass delay randomness", 3, 0, 20, 1, desc = "Adds 0 to this many random ticks to each correction pass delay.")
    private val clickDelay by slider("Click delay", 2, 0, 10, 1, desc = "Base delay in ticks after each hotbar swap click.")
    private val clickDelayRandomness by slider("Click delay randomness", 2, 0, 10, 1, desc = "Adds 0 to this many random ticks after each hotbar swap click.")
    private val postSwapDelay by slider("Post swap delay", 5, 0, 20, 1, desc = "Delay in ticks after the final hotbar swap before movement and input are restored.")
    private val presetsSetting = register(ListSetting<HotbarPreset, MutableList<HotbarPreset>>("Presets", mutableListOf()))
    private val presets get() = presetsSetting.value
    private val validFloors = listOf("f1", "f2", "f3", "f4", "f5", "f6", "f7", "m1", "m2", "m3", "m4", "m5", "m6", "m7")
    private val validClasses = listOf("healer", "mage", "berserk", "archer", "tank")
    private var swapJob: Job? = null
    private var blockingGameInput = false
    private val pendingChatMessages = ArrayDeque<Pair<String, Long>>()
    private val triggeredPresets = mutableSetOf<String>()
    private var triggerLevel = mc.level

    @Suppress("unused")
    private val hud by textHud("Auto Hotbar HUD", Colour.WHITE, font = TextHud.HudFont.Minecraft) {
        visibleIf { this@AutoHotbar.enabled && (preview || isSwapping) }
        column {
            textSupplied(
                supplier = {
                    val name = HotbarSwapperUtils.activePresetName ?: "Preset"
                    "Swapping §7[§c$name§7]"
                },
                colour = colour,
                font = font,
                size = 18.px,
            ).shadow = shadow
        }
    }.setting()

    init {
        command.sub("hotbar").description("Manages hotbar presets.").also { hotbar ->
            hotbar.sub("save") { name: String -> save(name) }
                .description("Saves your current hotbar as a preset.")

            hotbar.sub("load") { presetName: String -> load(presetName) }
                .description("Loads a hotbar preset.")
                .suggests { presets.map { it.name } }

            hotbar.sub("list") { list() }
                .description("Lists saved hotbar presets.")

            hotbar.sub("delete") { presetName: String -> delete(presetName) }
                .description("Deletes a hotbar preset.")
                .suggests { presets.map { it.name } }

            hotbar.sub("setmsg") { presetName: String, message: GreedyString? ->
                setMessage(presetName, message?.string)
            }.description("Sets or clears the chat trigger for a preset.")
                .suggests("presetName") { presets.map { it.name } }

            hotbar.sub("setmatch") { presetName: String, mode: String ->
                setMatch(presetName, mode)
            }.description("Sets partial or full message matching for a preset.")
                .suggests("presetName") { presets.map { it.name } }
                .suggests("mode", listOf("partial", "full"))

            hotbar.sub("setcowhat") { presetName: String, enabled: Boolean ->
                setCowHat(presetName, enabled)
            }.description("Enables or disables equipping Cow Hat after loading a preset.")
                .suggests("presetName") { presets.map { it.name } }

            hotbar.sub("sethelmet") { presetName: String, helmet: String ->
                setHelmet(presetName, helmet)
            }.description("Sets the helmet to equip after loading a preset: cow, necron, spirit, or none.")
                .suggests("presetName") { presets.map { it.name } }
                .suggests("helmet", listOf("cow", "necron", "spirit", "none"))

            hotbar.sub("setonce") { presetName: String, enabled: Boolean ->
                setOnce(presetName, enabled)
            }.description("Limits a preset's automatic trigger to once per dungeon run.")
                .suggests("presetName") { presets.map { it.name } }

            hotbar.sub("setfloor") { presetName: String, floor: String? ->
                setFloor(presetName, floor)
            }.description("Sets or clears the dungeon floor requirement for a preset.")
                .suggests("presetName") { presets.map { it.name } }
                .suggests("floor", validFloors)

            hotbar.sub("setclass") { presetName: String, className: String? ->
                setClass(presetName, className)
            }.description("Sets or clears the dungeon class requirement for a preset.")
                .suggests("presetName") { presets.map { it.name } }
                .suggests("className", validClasses)
        }

        on<PacketEvent.Received, ClientboundSystemChatPacket>(acceptCancelled = true) {
            if (packet.overlay) return@on
            val message = packet.content.string
            val level = mc.level
            mc.execute {
                if (mc.level === level) handleChatMessage(message, fromPacket = true)
            }
        }

        on<ChatEvent.Receive>(acceptCancelled = true) {
            handleChatMessage(message, fromPacket = false)
        }

        on<TickEvent.Start> {
            if (blockingGameInput) {
                player.stop()
                if (blockInput) suppressItemUse(AutoHotbar)
                else restoreItemUse(AutoHotbar)
            }
        }

        on<WorldEvent.Change> {
            pendingChatMessages.clear()
            stopSwapping()
        }

        on<KeyEvent.Press> {
            if (blockInput && blockingGameInput) cancel()
        }

        on<KeyEvent.Release> {
            if (blockInput && blockingGameInput) cancel()
        }

        on<MouseEvent.Click> {
            if (blockInput && blockingGameInput) cancel()
        }

        on<MouseEvent.Scroll> {
            if (blockInput && blockingGameInput) cancel()
        }

        on<MouseEvent.Move> {
            if (blockInput && blockingGameInput) cancel()
        }
    }

    override fun onDisable() {
        pendingChatMessages.clear()
        stopSwapping()
        super.onDisable()
    }

    private fun handleChatMessage(message: String, fromPacket: Boolean) {
        if (!enabled || mc.player == null) return
        if (triggerLevel !== mc.level) {
            triggerLevel = mc.level
            triggeredPresets.clear()
            pendingChatMessages.clear()
        }
        val unformatted = message.noControlCodes
        if (unformatted.startsWith("${prefix().string.noControlCodes} ")) return

        val now = System.nanoTime()
        while (pendingChatMessages.firstOrNull()?.let { now - it.second > 10_000_000_000L } == true) {
            pendingChatMessages.removeFirst()
        }
        if (!fromPacket) {
            val index = pendingChatMessages.indexOfFirst { it.first == unformatted }
            if (index >= 0) {
                pendingChatMessages.removeAt(index)
                return
            }
        }

        val preset = presets.firstOrNull {
            !(it.oncePerRun && Dungeon.inDungeons && it.name in triggeredPresets) &&
                it.matchesMessage(unformatted) && requirementsMet(it, reportMismatch = false)
        } ?: return
        if (fromPacket) {
            if (pendingChatMessages.size >= 64) pendingChatMessages.removeFirst()
            pendingChatMessages.addLast(unformatted to now)
        }
        load(preset, automatic = true)
    }

    private fun save(name: String) {
        if (findPreset(presets, name) != null) {
            modMessage("&cA preset named &e$name &calready exists.")
            return
        }

        presets.add(captureHotbar(name))
        modMessage("&aSaved hotbar preset &e$name&a.")
    }

    private fun load(presetName: String) {
        if (!enabled || isSwapping || swapJob?.isActive == true) return
        val preset = findPreset(presets, presetName, fuzzy = true)
            ?: return modMessage("&cPreset &e$presetName &cdoesn't exist.")

        load(preset)
    }

    private fun load(preset: HotbarPreset, automatic: Boolean = false) {
        if (!enabled || isSwapping || swapJob?.isActive == true) return
        if (!requirementsMet(preset)) return
        if (automatic && Dungeon.inDungeons) {
            if (preset.oncePerRun && preset.name in triggeredPresets) return
            triggeredPresets.add(preset.name)
        }

        swapJob = QuoiMod.scope.launch(mc.asCoroutineDispatcher()) {
            try {
                waitUntilNotInTerminal()

                beginSwap(preset.name)
                modMessage("&aEquipping preset &e${preset.name}&a.")

                runSwapPasses(preset)
                modMessage("&aPreset &e${preset.name} &aequipped.")
                val helmetId = preset.selectedHelmetId
                val helmetName = preset.selectedHelmetName
                if (helmetId != null && helmetName != null) {
                    waitUntilNotInTerminal()
                    if (Dungeon.isDead) return@launch
                    if (EquipmentSwapper.equip(helmetName, blockInput = blockInput, itemId = helmetId, timeout = helmetTimeout)) {
                        modMessage("&aQueued $helmetName equip.")
                    } else {
                        modMessage("&cCould not queue $helmetName equip. Another equipment swap may be in progress.")
                    }
                }
            } finally {
                endSwap(preset.name)
                if (swapJob == this.coroutineContext[Job]) swapJob = null
            }
        }
    }

    private fun stopSwapping() {
        swapJob?.cancel()
        swapJob = null
        stopInputBlock()
        endSwap()
    }

    private suspend fun waitUntilNotInTerminal() {
        while (Dungeon.inTerminal) {
            wait(1)
        }
    }

    private suspend fun waitRandomDelay(baseDelay: Int, randomness: Int) {
        val randomTicks = if (randomness <= 0) 0 else Random.nextInt(randomness + 1)
        wait(baseDelay + randomTicks)
    }

    private suspend fun runSwapPasses(preset: HotbarPreset) {
        val passes = listOf(true, false, true)
        waitRandomDelay(swapPassDelay, passDelayRandomness)

        blockingGameInput = true
        player.stop()
        try {
            if (blockInput) suppressItemUse(AutoHotbar)
            var swapped = false
            passes.forEachIndexed { index, includeHotbar ->
                if (index > 0) waitRandomDelay(swapPassDelay, passDelayRandomness)
                swapped = runPass(preset, includeHotbar) || swapped
            }
            if (swapped) wait(postSwapDelay)
        } finally {
            stopInputBlock()
        }
    }

    private fun stopInputBlock() {
        if (!blockingGameInput) return

        blockingGameInput = false
        val restore = Runnable {
            KeyMapping.setAll()
            restoreItemUse(AutoHotbar)
        }
        if (mc.isSameThread) restore.run()
        else mc.execute(restore)
    }

    private suspend fun runPass(preset: HotbarPreset, includeHotbar: Boolean): Boolean {
        val interacted = mutableSetOf<Int>()
        var swapped = false
        for (slot in HOTBAR_START..HOTBAR_END) {
            val item = preset.slots.getOrNull(slot) ?: HotbarItem()
            if (setSlot(slot, item, interacted, includeHotbar)) {
                swapped = true
                waitRandomDelay(clickDelay, clickDelayRandomness)
            }
        }
        return swapped
    }

    private fun setSlot(slot: Int, item: HotbarItem, interacted: MutableSet<Int>, includeHotbar: Boolean): Boolean {
        if (slot !in HOTBAR_START..HOTBAR_END) return false

        if (item.isEmpty) {
            return clearSlot(slot, interacted)
        }

        val itemSlot = findMatchingInventorySlot(item, slot, interacted, includeHotbar)
        if (itemSlot == NOT_FOUND || slot in interacted) return false
        if (includeHotbar && itemSlot !in HOTBAR_START..HOTBAR_END) return false

        interacted.add(itemSlot)
        return swapWithHotbar(itemSlot, slot)
    }

    private fun clearSlot(slot: Int, interacted: MutableSet<Int>): Boolean {
        val stack = player.inventory.getItem(slot)
        if (stack.isEmpty) return false

        val emptySlot = findEmptyInventorySlot(interacted)
        if (emptySlot == NOT_FOUND) return false

        interacted.add(emptySlot)
        return swapWithHotbar(emptySlot, slot)
    }

    private fun requirementsMet(preset: HotbarPreset, reportMismatch: Boolean = true): Boolean {
        preset.requiredFloor?.takeIf { it.isNotBlank() }?.let { required ->
            val current = Dungeon.floor?.name?.lowercase()
            if (current != required.lowercase()) {
                if (reportMismatch) {
                    modMessage("&cPreset &e${preset.name} &crequires floor &4$required &cbut you're on &4${current ?: "unknown"}&c.")
                }
                return false
            }
        }

        preset.requiredClass?.takeIf { it.isNotBlank() }?.let { required ->
            val current = Dungeon.currentDungeonPlayer.clazz.name.lowercase()
            if (current != required.lowercase()) {
                if (reportMismatch) {
                    modMessage("&cPreset &e${preset.name} &crequires class &4$required &cbut you're playing &4$current&c.")
                }
                return false
            }
        }

        return true
    }

    private fun list() {
        if (presets.isEmpty()) {
            modMessage("&cNo hotbar presets saved.")
            return
        }

        modMessage(presets.joinToString("\n") { preset ->
            val trigger = preset.message ?: "None"
            val floor = preset.requiredFloor ?: "None"
            val clazz = preset.requiredClass ?: "None"
            val match = if (preset.partialMessageMatch) "partial" else "full"
            val helmet = preset.selectedHelmetName ?: "None"
            val once = if (preset.oncePerRun) "on" else "off"
            "&e${preset.name} &7- trigger: &f$trigger&7, match: &f$match&7, helmet: &f$helmet&7, once per run: &f$once&7, floor: &f$floor&7, class: &f$clazz"
        }, id = "hotbar_presets".hashCode())
    }

    private fun delete(presetName: String) {
        val preset = findPreset(presets, presetName, fuzzy = true)
            ?: return modMessage("&cCouldn't find a preset matching &e$presetName&c.")

        presets.remove(preset)
        modMessage("&aRemoved preset &e${preset.name}&a.")
    }

    private fun setMessage(presetName: String, message: String?) {
        val preset = findPreset(presets, presetName, fuzzy = true)
            ?: return modMessage("&cPreset not found.")

        preset.message = message?.takeIf { it.isNotBlank() }
        if (preset.message == null) modMessage("&aRemoved trigger message for &e${preset.name}&a.")
        else {
            val match = if (preset.partialMessageMatch) "partial" else "full"
            modMessage("&aPreset &e${preset.name} &awill trigger on ($match match): &c${preset.message}")
        }
    }

    private fun setMatch(presetName: String, mode: String) {
        val preset = findPreset(presets, presetName, fuzzy = true)
            ?: return modMessage("&cPreset not found.")

        val normalized = mode.lowercase()
        preset.partialMessageMatch = when (normalized) {
            "partial" -> true
            "full" -> false
            else -> return modMessage("&cInvalid match mode. Use partial or full.")
        }
        modMessage("&aPreset &e${preset.name} &anow uses &e$normalized &amessage matching.")
    }

    private fun setCowHat(presetName: String, enabled: Boolean) {
        val preset = findPreset(presets, presetName, fuzzy = true)
            ?: return modMessage("&cPreset not found.")

        preset.equipCowHat = enabled
        if (enabled || preset.helmetItemId == "COW_HEAD") preset.helmetItemId = null
        modMessage("&aCow Hat equip for &e${preset.name}&a: &e${if (enabled) "on" else "off"}&a.")
    }

    private fun setOnce(presetName: String, enabled: Boolean) {
        val preset = findPreset(presets, presetName, fuzzy = true)
            ?: return modMessage("&cPreset not found.")

        preset.oncePerRun = enabled
        modMessage("&aOnce per dungeon run for &e${preset.name}&a: &e${if (enabled) "on" else "off"}&a.")
    }

    private fun setHelmet(presetName: String, helmet: String) {
        val preset = findPreset(presets, presetName, fuzzy = true)
            ?: return modMessage("&cPreset not found.")

        val itemId = when (helmet.lowercase()) {
            "cow", "cow_head" -> "COW_HEAD"
            "necron", "diamond_necron_head" -> "DIAMOND_NECRON_HEAD"
            "spirit", "starred_spirit_mask" -> "STARRED_SPIRIT_MASK"
            "none" -> null
            else -> return modMessage("&cInvalid helmet. Use cow, necron, spirit, or none.")
        }
        preset.helmetItemId = itemId
        preset.equipCowHat = false
        modMessage("&aHelmet for &e${preset.name}&a: &e${preset.selectedHelmetName ?: "None"}&a.")
    }

    private fun setFloor(presetName: String, floor: String?) {
        val preset = findPreset(presets, presetName, fuzzy = true)
            ?: return modMessage("&cPreset not found.")

        val normalized = floor?.lowercase()?.takeIf { it.isNotBlank() }
        if (normalized == null) {
            preset.requiredFloor = null
            modMessage("&aRemoved floor requirement for &e${preset.name}&a.")
            return
        }

        if (normalized !in validFloors) {
            modMessage("&cInvalid floor. Valid floors: ${validFloors.joinToString(", ")}")
            return
        }

        preset.requiredFloor = normalized
        modMessage("&aPreset &e${preset.name} &anow requires floor &c$normalized&a.")
    }

    private fun setClass(presetName: String, className: String?) {
        val preset = findPreset(presets, presetName, fuzzy = true)
            ?: return modMessage("&cPreset not found.")

        val normalized = className?.lowercase()?.takeIf { it.isNotBlank() }
        if (normalized == null) {
            preset.requiredClass = null
            modMessage("&aRemoved class requirement for &e${preset.name}&a.")
            return
        }

        if (normalized !in validClasses) {
            modMessage("&cInvalid class. Valid classes: ${validClasses.joinToString(", ")}")
            return
        }

        preset.requiredClass = normalized
        modMessage("&aPreset &e${preset.name} &anow requires class &c$normalized&a.")
    }
}
