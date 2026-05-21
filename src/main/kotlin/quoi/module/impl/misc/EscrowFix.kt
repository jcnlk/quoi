package quoi.module.impl.misc

import quoi.api.events.ChatEvent
import quoi.api.events.WorldEvent
import quoi.api.skyblock.Location
import quoi.api.skyblock.SkyblockPlayer
import quoi.module.Module
import quoi.utils.ChatUtils.command
import quoi.utils.StringUtils.noControlCodes

object EscrowFix : Module(
    "Escrow Fix",
    desc = "Automatically reopens the Auction House or Bazaar after escrow closes it."
) {
    private var lastCommandAt = 0L

    init {
        on<WorldEvent.Change> {
            lastCommandAt = 0L
        }

        on<ChatEvent.Packet> {
            val cleanMessage = message.noControlCodes
            if (!Location.inSkyblock) return@on

            val reopenCommand = cleanMessage.reopenCommand() ?: return@on
            reopenMenu(reopenCommand)
        }
    }

    private fun reopenMenu(reopenCommand: String) {
        val now = System.currentTimeMillis()
        if (now - lastCommandAt < COMMAND_COOLDOWN_MS) return
        if (!SkyblockPlayer.canUseCommands) return

        lastCommandAt = now
        command(reopenCommand)
    }

    private fun String.reopenCommand(): String? {
        if (this in auctionHouseCloseMessages) return "ah"
        if (bazaarEscrowRefund.matches(this)) return "bz"
        return null
    }

    private const val COMMAND_COOLDOWN_MS = 750L

    private val auctionHouseCloseMessages = setOf(
        "There was an error with the auction house! (AUCTION_EXPIRED_OR_NOT_FOUND)",
        "There was an error with the auction house! (INVALID_BID)",
        "Claiming BIN auction...",
        "Visit the Auction House to collect your item!"
    )

    private val bazaarEscrowRefund = Regex("^Escrow refunded \\d+ coins for Bazaar Instant Buy Submit!$")
}
