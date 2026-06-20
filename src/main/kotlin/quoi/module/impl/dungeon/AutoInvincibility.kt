package quoi.module.impl.dungeon

import quoi.api.events.core.on

import kotlinx.coroutines.launch
import net.minecraft.world.item.Items
import quoi.QuoiMod.scope
import quoi.api.abobaui.dsl.px
import quoi.api.abobaui.elements.impl.Text.Companion.shadow
import quoi.api.abobaui.elements.impl.Text.Companion.textSupplied
import quoi.api.colour.Colour
import quoi.api.commands.internal.GreedyString
import quoi.api.events.ChatEvent
import quoi.api.events.KeyEvent
import quoi.api.events.MouseEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.skyblock.SkyblockPlayer
import quoi.api.skyblock.SkyblockPlayer.InvincibilityType
import quoi.api.skyblock.SkyblockPlayer.Mask
import quoi.api.skyblock.dungeon.Dungeon
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.utils.ChatUtils.modMessage
import quoi.utils.Scheduler.wait
import quoi.utils.Scheduler.scheduleTask
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.skyblock.item.ItemUtils.skyblockId
import quoi.utils.skyblock.player.ContainerUtils
import quoi.utils.skyblock.player.MovementUtils.stop
import quoi.utils.skyblock.player.PetUtils
import quoi.utils.skyblock.player.PlayerUtils.rightClick
import quoi.utils.skyblock.player.SwapManager
import quoi.utils.ui.hud.impl.TextHud

object AutoInvincibility : Module(
    "Auto Invincibility",
    desc = "Automatically swaps to invincibility items.",
    tag = Tag.LEGACY
) {

    private val useSpiritMask by switch("Spirit Mask", false, desc = "Equips Spirit Mask after proccing.")
    private val useBonzoMask by switch("Bonzo's Mask", false, desc = "Equips Bonzo's Mask after proccing.")
    private val usePhoenixPet by switch("Phoenix Pet", false, desc = "Swaps to Phoenix pet after proccing.")
    private val swapDelay by slider("Swap delay", 0, 0, 40, 1, desc = "Ticks to wait before swapping after an invincibility proc.", unit = "t")
    private val phoenixSwapMethod by selector("Swap method", PhoenixSwapMethod.RodSwap, desc = "Method used to swap to the Phoenix pet. Rod Swap ignores input blocking.").childOf(::usePhoenixPet)
    private val dungeonsOnly by switch("Dungeons only", desc = "Only triggers while being in dungeons.")
    private val bossOnly by switch("Boss only", desc = "Only triggers while being in boss room.")
    private val p3Only by switch("Phase 3 only", desc = "Only triggers during phase 3.")
    private val stopMoving by switch("Prevent moving", true, desc = "Stops movement while equipping masks or swapping through the pet menu. Does not affect Rod Swap.")
    private val blockInputs by switch("Block inputs", true, desc = "Blocks keyboard and mouse input while equipping masks or swapping through the pet menu. Does not affect Rod Swap.")
    private val hud by textHud("Swap hud", Colour.WHITE, font = TextHud.HudFont.Minecraft) {
        visibleIf { this@AutoInvincibility.enabled && (preview || swapHudText != null) }
        column {
            textSupplied(
                supplier = { swapHudText ?: "Equipping Spirit Mask" },
                colour = colour,
                font = font,
                size = 18.px,
            ).shadow = shadow
        }
    }.setting("Shows the invincibility item currently being equipped.")

    private var swapping = false
    private var blockingGameInput = false
    private var swapHudText: String? = null
    private var previousPet: String? = null
    private var phoenixSwapId = 0
    private var phoenixWatchId = 0

    private val rodSwapBlacklist = setOf("SOUL_WHIP", "FLAMING_FLAY")
    private val invincibilityPriority = listOf(
        InvincibilityType.SPIRIT,
        InvincibilityType.PHOENIX,
        InvincibilityType.BONZO
    )

    override fun onDisable() {
        resetAllState()
        super.onDisable()
    }

    init {
        command.sub("equip") { maskName: GreedyString ->
            triggerEquip(maskName.string)
        }.description("Automatically swaps to a specified mask.").requires("&cAuto Invincibility module is disabled!") { enabled }

        on<WorldEvent.Change> {
            resetAllState()
        }

        on<TickEvent.Start> {
            if ((stopMoving || blockInputs) && blockingGameInput) player.stop()
        }

        on<KeyEvent.Press> {
            if (blockInputs && blockingGameInput) cancel()
        }

        on<KeyEvent.Release> {
            if (blockInputs && blockingGameInput) cancel()
        }

        on<MouseEvent.Click> {
            if (blockInputs && blockingGameInput) cancel()
        }

        on<MouseEvent.Scroll> {
            if (blockInputs && blockingGameInput) cancel()
        }

        on<MouseEvent.Move> {
            if (blockInputs && blockingGameInput) cancel()
        }

        on<ChatEvent.Packet> {
            if (dungeonsOnly && !Dungeon.inDungeons) return@on
            if (bossOnly && !Dungeon.inBoss) return@on
            if (p3Only && !Dungeon.inP3) return@on
            val messageRaw = message.noControlCodes

            val bonzoMsg = messageRaw == "Your Bonzo's Mask saved your life!" || messageRaw == "Your ⚚ Bonzo's Mask saved your life!"
            val spiritMsg = messageRaw == "Second Wind Activated! Your Spirit Mask saved your life!"
            val phoenixMsg = messageRaw == "Your Phoenix Pet saved you from certain death!"

            if (phoenixMsg) {
                handlePhoenixProc()
            } else if (bonzoMsg || spiritMsg) {
                triggerNextItem()
            }
        }
    }

    private fun triggerItem(type: InvincibilityType) {
        when (type) {
            InvincibilityType.SPIRIT -> triggerEquip("spirit mask", delayed = true)
            InvincibilityType.BONZO -> triggerEquip("bonzo's mask", delayed = true)
            InvincibilityType.PHOENIX -> triggerPhoenixSwap(delayed = true)
        }
    }

    private fun triggerNextItem() {
        if (Dungeon.isDead || swapping) return

        val nextItem = getNextItem()
        if (nextItem == null) {
            handleNoInvincibilityLeft()
            return
        }

        triggerItem(nextItem)
    }

    private fun handlePhoenixProc() {
        phoenixWatchId++

        val nextItem = getNextItem()
        if (nextItem == null) {
            handleNoInvincibilityLeft()
            return
        }

        triggerItem(nextItem)
        startPhoenixProcWatch("§cPhoenix didn't proc within 5s, swapping back to previous pet.")
    }

    private fun startPhoenixProcWatch(message: String) {
        val watchId = ++phoenixWatchId

        scope.launch {
            waitServerTicks(100)

            if (watchId != phoenixWatchId) return@launch
            if (Dungeon.isDead) return@launch
            if (!isPhoenixPet()) return@launch

            queuePreviousPetSwap(message)
        }
    }

    private fun handleNoInvincibilityLeft() {
        modMessage("§cNo invincibility left!")
        queuePreviousPetSwap("§cNo invincibility left, swapping back to previous pet.")
    }

    private fun getNextItem(): InvincibilityType? = invincibilityPriority.firstOrNull { it.isEnabled() && canUse(it) }

    private fun InvincibilityType.isEnabled(): Boolean = when (this) {
        InvincibilityType.SPIRIT -> useSpiritMask
        InvincibilityType.BONZO -> useBonzoMask
        InvincibilityType.PHOENIX -> usePhoenixPet
    }

    private fun canUse(type: InvincibilityType): Boolean {
        if (type.currentCooldown > 0) return false
        return when (type) {
            InvincibilityType.SPIRIT -> SkyblockPlayer.currentMask != Mask.SPIRIT
            InvincibilityType.BONZO -> SkyblockPlayer.currentMask != Mask.BONZO
            InvincibilityType.PHOENIX -> !isPhoenixPet()
        }
    }

    fun triggerEquip(maskName: String, delayed: Boolean = false) {
        if (Dungeon.isDead || swapping) return

        swapping = true
        scope.launch {
            try {
                waitSwapDelay(delayed)

                if (!waitUntilNotInTerminal()) return@launch

                val currentHelmet = player.inventory.getItem(39)
                val helmetName = currentHelmet.displayName.string
                if (helmetName.contains(maskName, ignoreCase = true)) return@launch

                swapHudText = "Equipping ${maskName.split(" ").joinToString(" ") { it.replaceFirstChar(Char::uppercase) }}"
                modMessage("§eEquipping $maskName.")

                blockingGameInput = true
                equipMask(maskName)
            } finally {
                resetSwapState()
            }
        }
    }

    private fun triggerPhoenixSwap(delayed: Boolean = false) {
        if (Dungeon.isDead || swapping) return

        val swapId = ++phoenixSwapId
        swapping = true
        scope.launch {
            try {
                waitSwapDelay(delayed)

                if (!waitUntilNotInTerminal()) return@launch

                val currentPet = SkyblockPlayer.currentPet.trim()
                if (currentPet.isNotEmpty() && !isPhoenixPet(currentPet)) {
                    previousPet = currentPet
                }

                swapHudText = "Equipping Phoenix"
                when (phoenixSwapMethod.selected) {
                    PhoenixSwapMethod.RodSwap -> triggerRodSwap()
                    PhoenixSwapMethod.PetMenu -> triggerPetMenuSwap()
                }
            } finally {
                resetSwapState()
            }

            if (swapId != phoenixSwapId) return@launch

            startPhoenixProcWatch("§cPhoenix didn't proc within 5s, swapping back to previous pet.")
        }
    }

    private suspend fun waitSwapDelay(delayed: Boolean) {
        if (delayed && swapDelay > 0) waitServerTicks(swapDelay)
    }

    private suspend fun waitServerTicks(ticks: Int) {
        wait(ticks, server = true)
        wait(1)
    }

    private suspend fun triggerRodSwap(delayed: Boolean = false) {
        waitSwapDelay(delayed)

        if (!waitUntilNotInTerminal()) return

        val player = player
        val rodSlot = (0..8).firstOrNull { slot ->
            val stack = player.inventory.getItem(slot)
            stack.item == Items.FISHING_ROD && stack.skyblockId !in rodSwapBlacklist
        }
            ?: return modMessage("§cCould not find a rod in your hotbar.")

        val swapped = SwapManager.swapToSlot(rodSlot)
        if (!swapped.success) return
        if (!swapped.already) wait(1)

        modMessage("§eRod swapping.")
        val wasCasted = player.fishing != null
        player.rightClick()
        if (wasCasted) {
            wait(4)
            player.rightClick()
        }
    }

    private suspend fun triggerPetMenuSwap() {
        if (!waitUntilNotInTerminal()) return

        blockingGameInput = true

        modMessage("§eSwapping to Phoenix.")
        val queued = PetUtils.switchPet("Phoenix", preventMove = stopMoving)
        if (!queued) return modMessage("§cFailed to queue Phoenix pet switch.")

        while (PetUtils.isBusy()) {
            wait(1)
        }
    }

    private fun queuePreviousPetSwap(message: String? = null) {
        val pet = previousPet?.takeIf { it.isNotBlank() } ?: return
        val swapId = phoenixSwapId
        val watchId = phoenixWatchId

        scope.launch {
            while (swapping || PetUtils.isBusy()) {
                wait(1)
            }

            if (swapId != phoenixSwapId || watchId != phoenixWatchId) return@launch
            if (Dungeon.isDead) return@launch
            if (!isPhoenixPet()) return@launch
            if (!waitUntilNotInTerminal()) return@launch

            modMessage(message ?: "§eSwapping back to $pet.")
            when (phoenixSwapMethod.selected) {
                PhoenixSwapMethod.RodSwap -> triggerRodSwap()
                PhoenixSwapMethod.PetMenu -> {
                    val queued = PetUtils.switchPet(pet, preventMove = stopMoving)
                    if (!queued) modMessage("§cFailed to queue previous pet switch.")
                }
            }
        }
    }

    private suspend fun equipMask(name: String) {
        val success = ContainerUtils.getContainerItemsClick(
            command = "eq",
            container = "Your Equipment and Stats",
            name = name,
            inContainer = false,
            shift = true,
            cancelReopen = true
        )

        if (success) {
            scheduleTask(2) { ContainerUtils.closeContainer() }
        } else {
            modMessage("§cFailed to equip $name.")
        }
    }

    private suspend fun waitUntilNotInTerminal(): Boolean {
        while (Dungeon.inTerminal) {
            wait(1)
            if (Dungeon.isDead) return false
        }

        return true
    }

    private fun isPhoenixPet(pet: String = SkyblockPlayer.currentPet): Boolean {
        return pet.contains("phoenix", ignoreCase = true)
    }

    private fun resetSwapState() {
        swapping = false
        blockingGameInput = false
        swapHudText = null
    }

    private fun resetAllState() {
        resetSwapState()
        phoenixWatchId++
        phoenixSwapId++
        previousPet = null
    }

    private enum class PhoenixSwapMethod {
        RodSwap,
        PetMenu
    }
}
