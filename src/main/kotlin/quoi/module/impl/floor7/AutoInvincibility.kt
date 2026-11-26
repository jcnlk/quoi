package quoi.module.impl.floor7

import kotlinx.coroutines.*
import net.minecraft.world.item.Items
import quoi.api.events.PetEvent
import quoi.api.events.SkyblockPlayerEvent
import quoi.api.events.TerminalEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.*
import quoi.api.skyblock.SkyblockPlayer
import quoi.api.skyblock.SkyblockPlayer.InvincibilityType
import quoi.api.skyblock.SkyblockPlayer.Mask
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.Floor7
import quoi.api.skyblock.dungeon.enums.Phase
import quoi.api.skyblock.dungeon.terminals.TerminalUtils
import quoi.api.skyblock.location.Island
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.UIComponent.Companion.visibleIf
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

object AutoInvincibility : Module(
    "Auto Invincibility",
    desc = "Automatically swaps to invincibility items.",
    area = Island.Dungeon
) {
    private val items by multiSelect(
        "Invincibility Items",
        setOf(InvincibilityType.SPIRIT, InvincibilityType.BONZO, InvincibilityType.PHOENIX),
        listOf(InvincibilityType.SPIRIT, InvincibilityType.BONZO, InvincibilityType.PHOENIX)
    )
    private val priority by order(
        "Priority",
        listOf(InvincibilityType.SPIRIT, InvincibilityType.PHOENIX, InvincibilityType.BONZO),
        desc = "Invincibility priority from first to last. Drag to reorder.",
    ) { type ->
        when (type) {
            InvincibilityType.SPIRIT -> "Spirit Mask"
            InvincibilityType.BONZO -> "Bonzo's Mask"
            InvincibilityType.PHOENIX -> "Phoenix Pet"
        }
    }
    private val swapDelay by slider(
        "Swap delay",
        0,
        0,
        40,
        1,
        desc = "Ticks to wait before swapping after an invincibility proc.",
        unit = "t"
    )
    private val phoenixSwapMethod by selector(
        "Swap method",
        PhoenixSwapMethod.RodSwap,
        desc = "Method used to swap to the Phoenix pet."
    ).childOf(::items).visibleIf { InvincibilityType.PHOENIX in items }
    private val swapBackPet by switch(
        "Swap back to previous pet",
        true,
        desc = "Swaps back to the previous pet after Phoenix procced or timed out."
    ).childOf(::items).visibleIf { InvincibilityType.PHOENIX in items }
    private val swapBackItem by switch(
        "Swap back to previous item",
        true,
        desc = "Swaps back to the previously held item after a rod swap."
    ).childOf(::items)
        .visibleIf { InvincibilityType.PHOENIX in items && phoenixSwapMethod.selected == PhoenixSwapMethod.RodSwap }
    private val bossOnly by switch("Boss only", desc = "Only triggers while being in boss room.")
    private val p3Only by switch("Phase 3 only", desc = "Only triggers during phase 3.")
    private val containerSettings = ContainerSettings(this)

    private var swapJob: Job? = null
    private var previousPet: String? = null
    private var phoenixWatchJob: Job? = null

    private val rodSwapBlacklist = setOf("SOUL_WHIP", "FLAMING_FLAY", "GRAPPLING_HOOK")

    override fun onDisable() {
        resetAllState()
        super.onDisable()
    }

    init {
        on<WorldEvent.Change> {
            removeAsyncScope()
            resetAllState()
        }

        on<PetEvent.Change> {
            if (pet?.matches("Phoenix") != true) phoenixWatchJob?.cancel()
        }

        on<SkyblockPlayerEvent.Invincibility.Proc> {
            if (type == InvincibilityType.PHOENIX) phoenixWatchJob?.cancel()

            asyncScope.startSwap {
                val nextItem = priority.order.firstOrNull { type ->
                    type in items && type.currentCooldown <= 0 && when (type) {
                        InvincibilityType.SPIRIT -> SkyblockPlayer.currentMask != Mask.SPIRIT
                        InvincibilityType.BONZO -> SkyblockPlayer.currentMask != Mask.BONZO
                        InvincibilityType.PHOENIX -> !isPhoenixEquipped()
                    }
                } ?: run {
                    schedulePetRestore("§cNo invincibility left, swapping back to previous pet.")
                    return@startSwap
                }

                when (nextItem) {
                    InvincibilityType.SPIRIT -> equipMask("Spirit Mask")
                    InvincibilityType.BONZO -> equipMask("Bonzo's Mask")
                    InvincibilityType.PHOENIX -> {
                        previousPet = PetUtils.currentPet?.name?.takeIf { it.isNotBlank() && !isPhoenixEquipped() }
                        if (swapPet("Phoenix") && InvincibilityType.PHOENIX.currentCooldown <= 0) {
                            schedulePetRestore(
                                "§cPhoenix didn't proc within 5s, swapping back to previous pet.",
                                delay = 100
                            )
                        }
                    }
                }
            }
        }
    }

    private fun CoroutineScope.startSwap(delay: Int = swapDelay, action: suspend () -> Unit) {
        if (!canSwap()) return

        val previousSwap = swapJob
        swapJob = launch {
            previousSwap?.join()
            wait(delay, server = true)
            if (!waitUntilNotInTerminal() || !canSwap()) return@launch
            action()
        }
    }

    private fun canSwap(): Boolean = inEnvironment() && !Dungeon.isDead &&
        (!bossOnly || Dungeon.inBoss) && (!p3Only || Floor7.inPhase(Phase.P3))

    private suspend fun equipMask(maskName: String) {
        modMessage("§eEquipping $maskName.")
        containerTask(name = "Equip $maskName", settings = containerSettings) {
            equipItem(maskName)
        }.await()
    }

    private suspend fun swapPet(name: String): Boolean = coroutineScope {
        val equipped = async(start = CoroutineStart.UNDISPATCHED) {
            await<PetEvent.Change>(timeout = 100, serverTicks = true) { pet?.matches(name) == true }
        }

        try {
            when (phoenixSwapMethod.selected) {
                PhoenixSwapMethod.RodSwap -> if (!triggerRodSwap()) return@coroutineScope false
                PhoenixSwapMethod.PetMenu -> {
                    modMessage("§eSwapping to $name.")
                    val result = containerTask(name = "Pet: $name", settings = containerSettings) {
                        equipPet(name)
                    }.await()
                    if (result != ContainerTaskResult.Success) return@coroutineScope false
                }
            }

            val confirmed = equipped.await() != null && PetUtils.currentPet?.matches(name) == true
            if (!confirmed) modMessage("§cPet swap to $name was not confirmed.")
            confirmed
        } finally {
            equipped.cancel()
        }
    }

    private suspend fun triggerRodSwap(): Boolean {
        val rodSlot = (0..<8).firstOrNull { slot ->
            val stack = player.inventory.getItem(slot)
            stack.item == Items.FISHING_ROD && stack.skyblockId !in rodSwapBlacklist
        }
            ?: run {
                modMessage("§cCould not find a rod in your hotbar.")
                return false
            }

        val previousSlot = player.inventory.selectedSlot
        val swapped = SwapManager.swapToSlot(rodSlot)
        if (!swapped.success) return false
        if (!swapped.already) wait(1)

        modMessage("§eRod swapping.")

        val wasCasted = player.fishing != null
        player.rightClick()

        if (wasCasted) {
            wait(4)
            player.rightClick()
        }

        if (swapBackItem && previousSlot != rodSlot) {
            wait(1)
            SwapManager.swapToSlot(previousSlot)
        }
        return true
    }

    private fun schedulePetRestore(message: String, delay: Int = 0) {
        if (!swapBackPet) return
        val pet = previousPet?.takeIf { it.isNotBlank() } ?: return
        phoenixWatchJob?.cancel()

        phoenixWatchJob = asyncScope.launch {
            wait(delay, server = true)
            while (swapJob?.isActive == true) swapJob?.join()
            if (!isPhoenixEquipped()) return@launch

            startSwap(delay = 0) {
                if (!isPhoenixEquipped()) return@startSwap
                phoenixWatchJob = null
                modMessage(message)
                if (swapPet(pet)) previousPet = null
            }
        }
    }

    private suspend fun waitUntilNotInTerminal(): Boolean {
        while (TerminalUtils.inTerminal) {
            await<TerminalEvent.Close>()
        }
        return !Dungeon.isDead
    }

    private fun isPhoenixEquipped(): Boolean {
        return PetUtils.currentPet?.matches("Phoenix") == true
    }

    private fun resetAllState() {
        swapJob?.cancel()
        swapJob = null
        phoenixWatchJob?.cancel()
        phoenixWatchJob = null
        previousPet = null
    }

    private enum class PhoenixSwapMethod {
        RodSwap, PetMenu;
    }
}
