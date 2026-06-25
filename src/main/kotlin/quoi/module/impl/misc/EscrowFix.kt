package quoi.module.impl.misc

import quoi.api.events.ChatEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.on
import quoi.api.skyblock.Location
import quoi.api.skyblock.SkyblockPlayer
import quoi.module.Module
import quoi.utils.ChatUtils.command

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
            if (!Location.inSkyblock) return@on

            val reopenCommand = unformatted.reopenCommand() ?: return@on
            reopenMenu(reopenCommand)
        }
    }

    private fun reopenMenu(reopenCommand: String) {
        val now = System.currentTimeMillis()
        if (now - lastCommandAt < 750) return
        if (!SkyblockPlayer.canUseCommands) return

        lastCommandAt = now
        command(reopenCommand)
    }

    private fun String.reopenCommand(): String? {
        if (this in auctionHouseCloseMessages) return "ah"
        if (bazaarEscrowRefund.matches(this)) return "bz"
        return null
    }

    private val auctionHouseCloseMessages = setOf(
        "There was an error with the auction house! (AUCTION_EXPIRED_OR_NOT_FOUND)",
        "There was an error with the auction house! (INVALID_BID)",
        "Claiming BIN auction...",
        "Visit the Auction House to collect your item!"
    )

    private val bazaarEscrowRefund = Regex("^Escrow refunded \\d+ coins for Bazaar Instant Buy Submit!$")
}
