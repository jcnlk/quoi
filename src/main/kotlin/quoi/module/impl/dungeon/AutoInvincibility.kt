package quoi.module.impl.dungeon

import kotlinx.coroutines.launch
import net.minecraft.world.item.Items
import quoi.QuoiMod.scope
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
import quoi.utils.skyblock.player.PlayerUtils.rightClick
import quoi.utils.skyblock.player.SwapManager

// Kyleen
object AutoInvincibility : Module( // todo remove in the future
    "Auto Invincibility",
    desc = "Automatically swaps to invincibility items."
) {

    private val prioritySettings by text("Available items")
    private val useSpiritMask by switch("Spirit Mask", true).childOf(::prioritySettings)
    private val useBonzoMask by switch("Bonzo's Mask", true).childOf(::prioritySettings)
    private val usePhoenixPet by switch("Phoenix Pet", true).childOf(::prioritySettings)
    private val dungeonsOnly by switch("Dungeons only")
    private val bossOnly by switch("Boss only")
    private val p3Only by switch("Phase 3 only")
    private val stopMoving by switch("Prevent moving", true)

    private var swapping = false

    init {
        command.sub("equip") { maskName: GreedyString ->
            triggerEquip(maskName.string)
        }.description("Automatically swaps to a specified mask.").requires("&cAuto Invincibility module is disabled!") { enabled }

        on<WorldEvent.Change> {
            swapping = false
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
                equipMask(maskName)
            } finally {
                swapping = false
            }
        }
    }

    private fun triggerPhoenixSwap() {
        if (Dungeon.isDead || swapping || Dungeon.inTerminal) return

        swapping = true
        scope.launch {
            try {
                val player = player
                val rodSlot = (0..8).firstOrNull { player.inventory.getItem(it).item == Items.FISHING_ROD }
                    ?: return@launch modMessage("§cCould not find a rod in your hotbar.")

                val swapped = SwapManager.swapToSlot(rodSlot)
                if (!swapped.success) return@launch
                if (!swapped.already) wait(1)

                player.rightClick()
                wait(4)
                player.rightClick()
            } finally {
                swapping = false
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
        }
    }
}