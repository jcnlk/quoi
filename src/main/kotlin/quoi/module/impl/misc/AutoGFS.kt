package quoi.module.impl.misc

import quoi.api.events.ChatEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.on
import quoi.api.skyblock.Location
import quoi.api.skyblock.SkyblockPlayer
import quoi.api.skyblock.dungeon.Dungeon
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.childOf
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

    private val mode by selector("Mode", "Amount", arrayListOf("Amount", "Time"))
    private val amount by slider("Amount", 50, 5, 95, 5, unit = "%").childOf(::mode) { it.selected == "Amount" }
    private val time by slider("Time", 5, 1, 60, 1, unit = "s").childOf(::mode) { it.selected == "Time" }

    private val dungeonsOnly by switch("Dungeons only", desc = "Only refill items when in dungeons.")

    private var tickCount = 0
    private var commandCooldown = 0
    private var nextItemIndex = 0
    private val emptySacks = hashSetOf<RefillItem>()

    init {
        on<ChatEvent.Packet> {
            RefillItem.entries.firstOrNull { unformatted == "You have no ${it.itemName} in your Sacks!" }?.let {
                emptySacks.add(it)
            }
        }

        on<WorldEvent.Change> {
            emptySacks.clear()
        }

        on<TickEvent.End> {
            if (commandCooldown > 0) commandCooldown--

            if (dungeonsOnly && !Dungeon.inDungeons) return@on
            if (Dungeon.isDead || !Location.inSkyblock || mc.gui.screen() != null) return@on
            if (!SkyblockPlayer.canUseCommands || commandCooldown > 0) return@on

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

    private fun isBelowPercentage(n: Int, max: Int) = n < (amount / 100.0) * max

    private fun refillNextItem(): Boolean {
        val items = RefillItem.entries

        repeat(items.size) {
            val item = items[nextItemIndex]
            nextItemIndex = (nextItemIndex + 1) % items.size

            if (item in emptySacks) return@repeat
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

        val enabled get() = when (this) {
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
