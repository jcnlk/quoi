package quoi.module.impl.dungeon

import kotlinx.coroutines.launch
import net.minecraft.world.item.Items
import quoi.QuoiMod.scope
import quoi.api.abobaui.dsl.px
import quoi.api.abobaui.elements.impl.Text.Companion.shadow
import quoi.api.abobaui.elements.impl.Text.Companion.textSupplied
import quoi.api.colour.Colour
import quoi.api.commands.internal.GreedyString
import quoi.api.events.ChatEvent
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
import quoi.utils.skyblock.player.ContainerUtils
import quoi.utils.skyblock.player.MovementUtils.stop
import quoi.utils.skyblock.player.PetUtils
import quoi.utils.skyblock.player.PlayerUtils.rightClick
import quoi.utils.skyblock.player.SwapManager
import quoi.utils.ui.hud.impl.TextHud

object AutoInvincibility : Module(
    "Auto Invincibility",
    desc = "Automatically swaps to invincibility items."
) {

    private val useSpiritMask by switch("Spirit Mask", false)
    private val useBonzoMask by switch("Bonzo's Mask", false)
    private val usePhoenixPet by switch("Phoenix Pet", false)
    private val phoenixSwapMethod by selector("Swap method", PhoenixSwapMethod.RodSwap).childOf(::usePhoenixPet)
    private val dungeonsOnly by switch("Dungeons only")
    private val bossOnly by switch("Boss only")
    private val p3Only by switch("Phase 3 only")
    private val stopMoving by switch("Prevent moving", true)
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
    }.setting()

    private var swapping = false
    private var swapHudText: String? = null

    override fun onDisable() {
        resetSwapState()
        super.onDisable()
    }

    init {
        command.sub("equip") { maskName: GreedyString ->
            triggerEquip(maskName.string)
        }.description("Automatically swaps to a specified mask.").requires("&cAuto Invincibility module is disabled!") { enabled }

        on<WorldEvent.Change> {
            resetSwapState()
        }

        on<TickEvent.Start> {
            if (stopMoving && swapping) player.stop()
        }

        on<ChatEvent.Packet> {
            if (dungeonsOnly && !Dungeon.inDungeons) return@on
            if (bossOnly && !Dungeon.inBoss) return@on
            if (p3Only && !Dungeon.inP3) return@on
            val messageRaw = message.noControlCodes

            val bonzoMsg = messageRaw == "Your Bonzo's Mask saved your life!" || messageRaw == "Your ⚚ Bonzo's Mask saved your life!"
            val spiritMsg = messageRaw == "Second Wind Activated! Your Spirit Mask saved your life!"
            val phoenixMsg = messageRaw == "Your Phoenix Pet saved you from certain death!"

            if (bonzoMsg || spiritMsg || phoenixMsg) {
                triggerNextItem(
                    when {
                        bonzoMsg -> InvincibilityType.BONZO
                        spiritMsg -> InvincibilityType.SPIRIT
                        else -> InvincibilityType.PHOENIX
                    }
                )
            }
        }
    }

    private fun triggerNextItem(triggered: InvincibilityType) {
        if (Dungeon.isDead || swapping || Dungeon.inTerminal) return

        when (getNextItem(triggered)) {
            InvincibilityType.SPIRIT -> triggerEquip("spirit mask")
            InvincibilityType.BONZO -> triggerEquip("bonzo's mask")
            InvincibilityType.PHOENIX -> triggerPhoenixSwap()
            null -> Unit
        }
    }

    private fun getNextItem(triggered: InvincibilityType): InvincibilityType? {
        return listOf(
            InvincibilityType.SPIRIT.takeIf { useSpiritMask && canUseSpirit(triggered) },
            InvincibilityType.BONZO.takeIf { useBonzoMask && canUseBonzo(triggered) },
            InvincibilityType.PHOENIX.takeIf { usePhoenixPet && canUsePhoenix(triggered) }
        ).firstOrNull { it != null }
    }

    private fun canUseSpirit(triggered: InvincibilityType): Boolean {
        if (triggered == InvincibilityType.SPIRIT) return false
        if (InvincibilityType.SPIRIT.currentCooldown > 0) return false
        return SkyblockPlayer.currentMask != Mask.SPIRIT
    }

    private fun canUseBonzo(triggered: InvincibilityType): Boolean {
        if (triggered == InvincibilityType.BONZO) return false
        if (InvincibilityType.BONZO.currentCooldown > 0) return false
        return SkyblockPlayer.currentMask != Mask.BONZO
    }

    private fun canUsePhoenix(triggered: InvincibilityType): Boolean {
        if (triggered == InvincibilityType.PHOENIX) return false
        if (InvincibilityType.PHOENIX.currentCooldown > 0) return false
        return !SkyblockPlayer.currentPet.contains("phoenix", true)
    }

    fun triggerEquip(maskName: String) {
        if (Dungeon.isDead || swapping || Dungeon.inTerminal) return

        val currentHelmet = player.inventory.getItem(39)
        val helmetName = currentHelmet.displayName.string

        if (helmetName.contains(maskName, ignoreCase = true)) return

        swapping = true
        scope.launch {
            try {
                swapHudText = "Equipping ${maskName.split(" ").joinToString(" ") { it.replaceFirstChar(Char::uppercase) }}"
                equipMask(maskName)
            } finally {
                resetSwapState()
            }
        }
    }

    private fun triggerPhoenixSwap() {
        if (Dungeon.isDead || swapping || Dungeon.inTerminal) return

        swapping = true
        scope.launch {
            try {
                swapHudText = "Equipping Phoenix"
                when (phoenixSwapMethod.selected) {
                    PhoenixSwapMethod.RodSwap -> triggerRodSwap()
                    PhoenixSwapMethod.PetMenu -> triggerPetMenuSwap()
                }
            } finally {
                resetSwapState()
            }
        }
    }

    private suspend fun triggerRodSwap() {
        val player = player
        val rodSlot = (0..8).firstOrNull { player.inventory.getItem(it).item == Items.FISHING_ROD }
            ?: return modMessage("§cCould not find a rod in your hotbar.")

        val swapped = SwapManager.swapToSlot(rodSlot)
        if (!swapped.success) return
        if (!swapped.already) wait(1)

        player.rightClick()
        wait(4)
        player.rightClick()
    }

    private suspend fun triggerPetMenuSwap() {
        val queued = PetUtils.switchPet("Phoenix", preventMove = stopMoving)
        if (!queued) return modMessage("§cFailed to queue Phoenix pet switch.")

        while (PetUtils.isBusy()) {
            wait(1)
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
        }
    }

    private fun resetSwapState() {
        swapping = false
        swapHudText = null
    }

    private enum class PhoenixSwapMethod {
        RodSwap,
        PetMenu
    }
}
