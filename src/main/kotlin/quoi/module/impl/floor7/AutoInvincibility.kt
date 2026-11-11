package quoi.module.impl.floor7

import kotlinx.coroutines.launch
import net.minecraft.world.item.Items
import quoi.api.events.ChatEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.asyncScope
import quoi.api.events.core.on
import quoi.api.events.core.wait
import quoi.api.skyblock.SkyblockPlayer
import quoi.api.skyblock.SkyblockPlayer.InvincibilityType
import quoi.api.skyblock.SkyblockPlayer.Mask
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.Floor7
import quoi.api.skyblock.dungeon.enums.Phase
import quoi.api.skyblock.location.Island
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.utils.ChatUtils.modMessage
import quoi.utils.skyblock.item.ItemUtils.skyblockId
import quoi.utils.skyblock.player.PetUtils
import quoi.utils.skyblock.player.PlayerUtils.rightClick
import quoi.utils.skyblock.player.SwapManager
import quoi.utils.skyblock.player.container.ContainerSettings
import quoi.utils.skyblock.player.container.task.ContainerTaskResult
import quoi.utils.skyblock.player.container.task.containerTask
import quoi.utils.skyblock.player.container.task.presets.equipItem
import quoi.utils.skyblock.player.container.task.presets.equipPet

/**
 * TODO:
 *  - make this les shit
 */

object AutoInvincibility : Module(
    "Auto Invincibility",
    desc = "Automatically swaps to invincibility items.",
    area = Island.Dungeon
) {
    private val useSpiritMask by switch("Spirit Mask", desc = "Equips Spirit Mask after proccing.")
    private val useBonzoMask by switch("Bonzo's Mask", desc = "Equips Bonzo's Mask after proccing.")
    private val usePhoenixPet by switch("Phoenix Pet", desc = "Swaps to Phoenix pet after proccing.")
    private val priority by selector("Priority", Priority.PhoenixFirst, desc = "Invincibility priority from first to last.")
    private val swapDelay by slider("Swap delay", 0, 0, 40, 1, desc = "Ticks to wait before swapping after an invincibility proc.", unit = "t")
    private val phoenixSwapMethod by selector("Swap method", PhoenixSwapMethod.RodSwap, desc = "Method used to swap to the Phoenix pet. Rod Swap ignores input blocking.").childOf(::usePhoenixPet)
    private val bossOnly by switch("Boss only", desc = "Only triggers while being in boss room.")
    private val p3Only by switch("Phase 3 only", desc = "Only triggers during phase 3.")
    private val containerSettings = ContainerSettings(this)

    private var swapping = false
    private var previousPet: String? = null
    private var phoenixSwapId = 0
    private var phoenixWatchId = 0

    private val rodSwapBlacklist = setOf("SOUL_WHIP", "FLAMING_FLAY", "GRAPPLING_HOOK")
    private val invincibilityPriority
        get() = when (priority.selected) {
            Priority.MaskFirst -> listOf(InvincibilityType.SPIRIT, InvincibilityType.BONZO, InvincibilityType.PHOENIX)
            Priority.PhoenixFirst -> listOf(InvincibilityType.SPIRIT, InvincibilityType.PHOENIX, InvincibilityType.BONZO)
        }

    override fun onDisable() {
        resetAllState()
        super.onDisable()
    }

    init {
        on<WorldEvent.Change> {
            resetAllState()
        }

        on<ChatEvent.Packet> {
            if (bossOnly && !Dungeon.inBoss) return@on
            if (p3Only && !Floor7.inPhase(Phase.P3)) return@on

            val proc = InvincibilityType.fromMessage(unformatted) ?: return@on

            when (proc) {
                InvincibilityType.PHOENIX -> phoenixWatchId++
                InvincibilityType.BONZO, InvincibilityType.SPIRIT -> {
                    if (Dungeon.isDead || swapping) return@on
                }
            }

            val nextItem = invincibilityPriority.firstOrNull { type ->
                type.currentCooldown <= 0 && when (type) {
                    InvincibilityType.SPIRIT -> useSpiritMask && SkyblockPlayer.currentMask != Mask.SPIRIT
                    InvincibilityType.BONZO -> useBonzoMask && SkyblockPlayer.currentMask != Mask.BONZO
                    InvincibilityType.PHOENIX -> usePhoenixPet && !isPhoenixEquipped()
                }
            } ?: run {
                swapToPreviousPet("§cNo invincibility left, swapping back to previous pet.")
                return@on
            }

            when (nextItem) {
                InvincibilityType.SPIRIT -> triggerEquip("Spirit Mask")
                InvincibilityType.BONZO -> triggerEquip("Bonzo's Mask")
                InvincibilityType.PHOENIX -> triggerPhoenixSwap()
            }

            if (proc == InvincibilityType.PHOENIX) {
                startPhoenixProcWatch()
            }
        }
    }

    private fun startPhoenixProcWatch() {
        val watchId = ++phoenixWatchId

        asyncScope.launch {
            wait(100, server = true)

            if (watchId != phoenixWatchId || Dungeon.isDead || !isPhoenixEquipped()) return@launch

            swapToPreviousPet("§cPhoenix didn't proc within 5s, swapping back to previous pet.")
        }
    }

    private fun triggerEquip(maskName: String) {
        if (Dungeon.isDead || swapping) return

        swapping = true
        asyncScope.launch {
            try {
                wait(swapDelay, server = true)

                if (!waitUntilNotInTerminal()) return@launch

                modMessage("§eEquipping $maskName.")

                containerTask(name = "Equip $maskName", settings = containerSettings) {
                    equipItem(maskName)
                }.await()
            } finally {
                resetSwapState()
            }
        }
    }

    private fun triggerPhoenixSwap() {
        if (Dungeon.isDead || swapping) return

        val swapId = ++phoenixSwapId
        swapping = true
        asyncScope.launch {
            try {
                wait(swapDelay, server = true)

                if (!waitUntilNotInTerminal()) return@launch

                val currentPet = PetUtils.currentPet?.name.orEmpty()
                if (currentPet.isNotEmpty() && !isPhoenixEquipped()) {
                    previousPet = currentPet
                }

                when (phoenixSwapMethod.selected) {
                    PhoenixSwapMethod.RodSwap -> triggerRodSwap()
                    PhoenixSwapMethod.PetMenu -> {
                        modMessage("§eSwapping to Phoenix.")

                        val result = containerTask(name = "Pet: Phoenix", settings = containerSettings) {
                            equipPet("Phoenix")
                        }.await()
                        if (result != ContainerTaskResult.Success) return@launch
                    }
                }
            } finally {
                resetSwapState()
            }

            if (swapId != phoenixSwapId) return@launch

            startPhoenixProcWatch()
        }
    }

    private suspend fun triggerRodSwap() {
        if (!waitUntilNotInTerminal()) return

        val rodSlot = (0..<8).firstOrNull { slot ->
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

    private fun swapToPreviousPet(message: String) {
        val pet = previousPet?.takeIf { it.isNotBlank() } ?: return
        val swapId = phoenixSwapId
        val watchId = phoenixWatchId

        asyncScope.launch {
            while (swapping) wait(1)

            if (swapId != phoenixSwapId || watchId != phoenixWatchId) return@launch
            if (Dungeon.isDead) return@launch
            if (!isPhoenixEquipped()) return@launch
            if (!waitUntilNotInTerminal()) return@launch

            swapping = true
            try {
                modMessage(message)
                when (phoenixSwapMethod.selected) {
                    PhoenixSwapMethod.RodSwap -> triggerRodSwap()
                    PhoenixSwapMethod.PetMenu -> {
                        containerTask(name = "Pet: $pet", settings = containerSettings) {
                            equipPet(pet)
                        }.await()
                    }
                }
            } finally {
                resetSwapState()
            }
        }
    }

    private suspend fun waitUntilNotInTerminal(): Boolean {
        while (Dungeon.inTerminal) {
            wait(1)
            if (Dungeon.isDead) return false
        }

        return true
    }

    private fun isPhoenixEquipped(): Boolean {
        return PetUtils.currentPet?.matches("Phoenix") == true
    }

    private fun resetSwapState() {
        swapping = false
    }

    private fun resetAllState() {
        resetSwapState()
        phoenixWatchId++
        phoenixSwapId++
        previousPet = null
    }

    private enum class PhoenixSwapMethod {
        RodSwap, PetMenu;
    }

    private enum class Priority {
        MaskFirst, PhoenixFirst;
    }
}
