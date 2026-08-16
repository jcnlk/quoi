package quoi.module.impl.general

import kotlinx.coroutines.*
import quoi.QuoiMod.scope
import quoi.api.events.MouseEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.on
import quoi.api.input.CatKeys
import quoi.config.Config
import quoi.module.Module
import quoi.module.settings.Setting.Companion.json
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.impl.ListSetting
import quoi.utils.ChatUtils.modMessage
import quoi.utils.StringUtils.formattedString
import quoi.utils.skyblock.item.ItemUtils.skyblockId
import quoi.utils.skyblock.item.ItemUtils.skyblockUuid
import quoi.utils.skyblock.player.PlayerUtils.isLookingAtBreakable
import quoi.utils.skyblock.player.PlayerUtils.leftClick
import quoi.utils.skyblock.player.PlayerUtils.rightClick
import kotlin.math.max
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

// https://github.com/Noamm9/CatgirlAddons/blob/main/src/main/kotlin/catgirlroutes/module/impl/misc/AutoClicker.kt
object AutoClicker: Module(
    "Auto Clicker",
    desc = "A simple auto clicker for both left and right click. Activates when the corresponding key is being held down."
) {
    private val breakBlocks by switch("Allow breaking blocks", desc = "Allows the player to break blocks.").json("Break blocks")
    private val stopOnSwap by switch("Stop on item swap", desc = "Stops clicking on item swap.")
    private val favouriteItems by switch("Whitelist only", desc = "Only click when holding a whitelisted item.").json("Favourite items only")
    private val favLeft by ListSetting("FAVOURITE_ITEMS_LEFT", mutableListOf<String>())
    private val favRight by ListSetting("FAVOURITE_ITEMS_RIGHT", mutableListOf<String>())

    private val blockDungeonBreaker by switch("Block dungeon breaker", true, desc = "Prevents auto clicker from working with Dungeon Breaker.")

    private val leftClick by switch("Enable Left Click", desc = "Toggles the auto clicker for left click.").json("Left Click")
    private val leftCps by rangeSlider("Left CPS", 10 to 12, 1, 20, desc = "The range of clicks per second to perform for left click.").childOf(::leftClick)
    private val leftClickKeybind by keybind("Left click keybind", CatKeys.MOUSE_LEFT, desc = "The keybind to hold for left click auto clicking.")

    private val rightClick by switch("Enable Right Click", desc = "Toggles the auto clicker for right click.").json("Right Click")
    private val rightCps by rangeSlider("Right CPS", 10 to 12, 1, 20, desc = "The range of clicks per second to perform for right click.").childOf(::rightClick)
    private val rightClickKeybind by keybind("Right click keybind", CatKeys.MOUSE_RIGHT, desc = "The keybind to hold for right click auto clicking.")

    private var leftJob: Job? = null
    private var rightJob: Job? = null
    private var lastHeldSlot = -1
    private var isMining = false

    private fun heldItemKey(): String? {
        val stack = player.mainHandItem.takeUnless { it.isEmpty } ?: return null
        return stack.skyblockUuid ?: stack.skyblockId ?: stack.displayName.formattedString
    }

    private fun shouldClick(isLeft: Boolean): Boolean {
        if (mc.screen != null) return false
        if (blockDungeonBreaker && player.mainHandItem.skyblockId == "DUNGEONBREAKER") return false

        val favList = if (isLeft) favLeft else favRight
        return !favouriteItems || heldItemKey() in favList
    }

    private fun shouldAutoClick(isLeft: Boolean): Boolean {
        val enabled = if (isLeft) leftClick else rightClick
        val keybind = if (isLeft) leftClickKeybind else rightClickKeybind
        return this.enabled && enabled && keybind.isDown() && shouldClick(isLeft)
    }

    private val lookingAtBreakable get() = breakBlocks && player.isLookingAtBreakable

    override fun onDisable() {
        reset()
        super.onDisable()
    }

    init {
        val autoclicker = command.sub("autoclicker").description("Auto Clicker module settings.")

        autoclicker.sub("add") { button: String ->
            stupid(button) { list ->
                val held = heldItemKey() ?: return@stupid modMessage("&cYou are not holding an item!")
                if (list.contains(held)) return@stupid modMessage("&cThis item is already in the $button list!")

                list.add(held)
                modMessage("&aAdded ${player.mainHandItem.displayName.formattedString} &ato $button list!")
            }
        }.suggests("button", "left", "right")
        autoclicker.sub("remove") { button: String ->
            stupid(button) { list ->
                val held = heldItemKey() ?: return@stupid modMessage("&cYou are not holding an item!")
                if (!list.remove(held)) return@stupid modMessage("&cThis item is not in the $button list!")

                modMessage("&aRemoved ${player.mainHandItem.displayName.formattedString} &afrom $button list!")
            }
        }.suggests("button", "left", "right")
        autoclicker.sub("clear") { button: String ->
            stupid(button) { list ->
                list.clear()
                modMessage("&aCleared the $button list!")
            }
        }.suggests("button", "left", "right")

        on<MouseEvent.Click> {
            if (!state || button !in 0..1) return@on

            val isLeft = button == 0
            val enabled = if (isLeft) leftClick else rightClick
            val keybind = if (isLeft) leftClickKeybind else rightClickKeybind
            val mouseKey = CatKeys.MOUSE_LEFT + button

            if (enabled && keybind.key == mouseKey && keybind.isModifierDown() && shouldClick(isLeft)) {
                cancel()
            }
        }

        on<TickEvent.End> {
            val currentSlot = player.inventory.selectedSlot
            if (lastHeldSlot == -1) lastHeldSlot = currentSlot
            if (stopOnSwap && currentSlot != lastHeldSlot) reset()

            lastHeldSlot = currentSlot

            if (mc.screen != null) {
                reset()
                return@on
            }

            if (shouldAutoClick(true)) startClicking(true)
            else stopClicking(true)

            if (shouldAutoClick(false)) startClicking(false)
            else stopClicking(false)

            updateMiningState()
        }

        on<WorldEvent.Change> {
            reset()
        }
    }

    private fun startClicking(isLeft: Boolean) {
        if (isLeft) {
            if (leftJob?.isActive == true) return
            leftJob = scope.launch { click(true) }
        } else {
            if (rightJob?.isActive == true) return
            rightJob = scope.launch { click(false) }
        }
    }

    private fun stopClicking(isLeft: Boolean) {
        if (isLeft) {
            leftJob?.cancel()
            leftJob = null
        } else {
            rightJob?.cancel()
            rightJob = null
        }
    }

    private fun updateMiningState() {
        val shouldMine = leftJob?.isActive == true && lookingAtBreakable
        if (shouldMine == isMining) return

        mc.options.keyAttack.isDown = shouldMine
        isMining = shouldMine
    }

    private fun randomDelay(cpsRange: Pair<Int, Int>): Long {
        val minDelay = 1000L / cpsRange.second
        val maxDelay = 1000L / cpsRange.first
        return if (minDelay >= maxDelay) minDelay else Random.nextLong(minDelay, maxDelay)
    }

    private suspend fun click(isLeft: Boolean) {
        val job = currentCoroutineContext()[Job] ?: return

        while (job.isActive) {
            val nextDelay = CompletableDeferred<Long>()
            mc.execute {
                if (!job.isActive || !shouldAutoClick(isLeft)) {
                    nextDelay.complete(-1)
                    return@execute
                }

                if (!isLeft) player.rightClick()
                else if (!lookingAtBreakable) player.leftClick()

                nextDelay.complete(randomDelay(if (isLeft) leftCps else rightCps))
            }

            val delayMillis = nextDelay.await()
            if (delayMillis < 0) return
            delay(delayMillis.milliseconds)
        }
    }

    private fun stupid(button: String, block: (MutableList<String>) -> Unit) {
        val list = when (button.lowercase()) {
            "left" -> favLeft
            "right" -> favRight
            else -> return modMessage("&cInvalid button! Use \"left\" or \"right\".")
        }
        block(list)
        Config.save()
    }

    private fun reset() {
        stopClicking(true)
        stopClicking(false)
        mc.options.keyAttack.isDown = false
        isMining = false
        lastHeldSlot = -1
    }
}
