package quoi.api.commands

import quoi.api.commands.internal.BaseCommand
import quoi.utils.skyblock.player.PlayerUtils

object RefillCommands {
    fun addTo(command: BaseCommand) = with(command) {
        "ep" { amount: Int? -> refill("ENDER_PEARL", "ender_pearl", amount ?: 16) }
        "ij" { amount: Int? -> refill("INFLATABLE_JERRY", "inflatable_jerry", amount ?: 64) }
        "sl" { amount: Int? -> refill("SPIRIT_LEAP", "spirit_leap", amount ?: 16) }
        "sb" { amount: Int? -> refill("SUPERBOOM_TNT", "superboom_tnt", amount ?: 64) }
        "dd" { amount: Int? -> refill("DUNGEON_DECOY", "dungeon_decoy", amount ?: 64) }
        "tap" { amount: Int? -> refill("TOXIC_ARROW_POISON", "toxic_arrow_poison", amount ?: 64) }
        "twap" { amount: Int? -> refill("TWILIGHT_ARROW_POISON", "twilight_arrow_poison", amount ?: 64) }
    }

    private fun refill(itemId: String, sackName: String, amount: Int) {
        if (amount > 0) PlayerUtils.fillItemFromSack(itemId, amount, sackName)
    }
}
