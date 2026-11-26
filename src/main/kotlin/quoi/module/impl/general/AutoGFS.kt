package quoi.module.impl.general

import quoi.api.events.ChatEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.on
import quoi.api.skyblock.SkyblockPlayer
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.enums.DungeonClass
import quoi.api.skyblock.dungeon.enums.Floor
import quoi.api.skyblock.location.Island
import quoi.api.skyblock.location.Location
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.utils.ChatUtils
import quoi.utils.skyblock.player.PlayerUtils

object AutoGFS : Module( // untested
    "Auto GFS",
    desc = "Automatically refills certain items from your sacks."
) {
    private const val COMMAND_COOLDOWN_TICKS = 5

    private val itemsDropdown by text("Items to refill")
    private val pearls by switch("Pearls").childOf(::itemsDropdown)
    private val booms by switch("Super booms").childOf(::itemsDropdown)
    private val jerries by switch("Inflatable Jerries").childOf(::itemsDropdown)
    private val leaps by switch("Spirit Leaps").childOf(::itemsDropdown)
    private val twilight by switch("Twilight Arrow Poison").childOf(::itemsDropdown)
    private val twilightAfterLightning by switch("Refill after lightning", true).childOf(::twilight)
    private val twilightInCore by switch("Refill in Core").childOf(::twilight)
    private val twilightAfterRelics by switch("Refill after M7 relics", true).childOf(::twilight)
    private val twilightAmount by slider(
        "Twilight Amount", 8, 4, 32, 1,
        desc = "Amount to withdraw at each enabled Twilight trigger."
    ).childOf(::twilight)
    private val toxic by switch("Toxic Arrow Poison").childOf(::itemsDropdown)
    private val toxicAfterBuild by switch("Refill after Kuudra build", true).childOf(::toxic)
    private val toxicAmount by slider(
        "Toxic Amount", 8, 1, 64, 1,
        desc = "Amount to withdraw when Kuudra's Ballista is ready."
    ).childOf(::toxic)

    private val mode by selector("Mode", "Amount", arrayListOf("Amount", "Time"))
    private val amount by slider("Amount", 50, 5, 95, 5, unit = "%").childOf(::mode) { it.selected == "Amount" }
    private val time by slider("Time", 5, 1, 60, 1, unit = "s").childOf(::mode) { it.selected == "Time" }

    private val dungeonsOnly by switch(
        "Dungeons only",
        desc = "Only refill regular items in dungeons. Toxic can still refill after Kuudra builds."
    )

    private var tickCount = 0
    private var commandCooldown = 0
    private var nextItemIndex = 0
    private val emptySacks = hashSetOf<String>()
    private val emptySackMessage = Regex("^You have no (.+) in your Sacks!$")
    private var lightningRefilled = false
    private var pendingPoisonArea: Island? = null

    init {
        on<ChatEvent.Packet> {
            emptySackMessage.matchEntire(unformatted)?.let {
                emptySacks.add(it.groupValues[1])
            }
            if (Location.currentArea == Island.Kuudra) {
                if (toxic && toxicAfterBuild && unformatted == "[NPC] Elle: Phew! The Ballista is finally ready! It should be strong enough to tank Kuudra's blows now!") {
                    pendingPoisonArea = Island.Kuudra
                }
                return@on
            }

            if (!twilight || !Dungeon.inDungeons || !Dungeon.inBoss || Dungeon.floor != Floor.M7) return@on
            val clazz = Dungeon.currentDungeonPlayer.clazz
            if (clazz == DungeonClass.Unknown) return@on
            val dps = clazz == DungeonClass.Archer || clazz == DungeonClass.Berserk

            // based on: https://github.com/Noamm9/NoammAddons/blob/58975d7df18cba5323c427a31fc8596524ef1a58/src/main/kotlin/com/github/noamm9/features/impl/dungeon/AutoGFS.kt
            when {
                twilightInCore && dps && unformatted == "The Core entrance is opening!" -> pendingPoisonArea =
                    Island.Dungeon

                twilightAfterRelics && !dps && unformatted == "[BOSS] Wither King: I no longer wish to fight, but I know that will not stop you." -> pendingPoisonArea =
                    Island.Dungeon

                twilightAfterLightning && !lightningRefilled && clazz == DungeonClass.Archer &&
                    (unformatted == "[BOSS] Storm: ENERGY HEED MY CALL!" || unformatted == "[BOSS] Storm: THUNDER LET ME BE YOUR CATALYST!") -> {
                    pendingPoisonArea = Island.Dungeon
                    lightningRefilled = true
                }
            }
        }

        on<WorldEvent.Change> {
            reset()
        }

        on<TickEvent.End> {
            if (commandCooldown > 0) commandCooldown--

            if (Dungeon.isDead || !Location.inSkyblock || mc.gui.screen() != null) return@on
            if (!SkyblockPlayer.canUseCommands || commandCooldown > 0) return@on

            pendingPoisonArea?.let { area ->
                pendingPoisonArea = null
                val kuudra = area == Island.Kuudra
                val refillEnabled = if (kuudra) toxic && toxicAfterBuild else twilight
                val itemName = if (kuudra) "Toxic Arrow Poison" else "Twilight Arrow Poison"
                val refillAmount = if (kuudra) toxicAmount else twilightAmount
                if (refillEnabled && Location.currentArea == area && itemName !in emptySacks) {
                    val sackName = itemName.lowercase().replace(' ', '_')
                    ChatUtils.command("gfs $sackName $refillAmount")
                    commandCooldown = COMMAND_COOLDOWN_TICKS
                    return@on
                }
            }

            if (dungeonsOnly && !Dungeon.inDungeons) return@on

            if (++tickCount < when (mode.selected) {
                    "Amount" -> 20
                    "Time" -> time * 20
                    else -> Int.MAX_VALUE
                }
            ) return@on
            tickCount = 0

            if (refillNextItem()) {
                commandCooldown = COMMAND_COOLDOWN_TICKS
            }
        }
    }

    override fun onDisable() = reset()

    private fun reset() {
        emptySacks.clear()
        lightningRefilled = false
        pendingPoisonArea = null
    }

    private fun isBelowPercentage(n: Int, max: Int) = n < (amount / 100.0) * max

    private fun refillNextItem(): Boolean {
        val items = RefillItem.entries

        repeat(items.size) {
            val item = items[nextItemIndex]
            nextItemIndex = (nextItemIndex + 1) % items.size

            if (item.itemName in emptySacks) return@repeat
            if (item.shouldRefill() && item.refill()) return true
        }

        return false
    }

    private enum class RefillItem(
        val maxStack: Int,
        val itemId: String,
        val sackName: String,
        val itemName: String
    ) {
        PEARL(16, "ENDER_PEARL", "ender_pearl", "Ender Pearls"),
        BOOM(64, "SUPERBOOM_TNT", "superboom_tnt", "Superboom TNT"),
        JERRY(64, "INFLATABLE_JERRY", "inflatable_jerry", "Inflatable Jerries"),
        LEAP(16, "SPIRIT_LEAP", "spirit_leap", "Spirit Leaps");

        val enabled
            get() = when (this) {
                PEARL -> pearls
                BOOM -> booms
                JERRY -> jerries
                LEAP -> leaps
            }

        fun shouldRefill(): Boolean {
            if (!enabled) return false

            val currentAmount = PlayerUtils.getItemsAmount(itemId)
            return when (mode.selected) {
                "Amount" -> isBelowPercentage(currentAmount, maxStack)
                "Time" -> currentAmount < maxStack
                else -> false
            }
        }

        fun refill(): Boolean {
            return PlayerUtils.fillItemFromSack(itemId, maxStack, sackName)
        }
    }
}
