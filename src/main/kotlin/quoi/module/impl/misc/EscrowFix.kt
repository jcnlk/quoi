package quoi.module.impl.misc

import quoi.api.events.ChatEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.on
import quoi.api.skyblock.location.Location
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

            val cmd = when {
                unformatted in auctionHouseCloseMessages -> "ah"
                bazaarEscrowRefund.matches(unformatted) -> "bz"
                else -> return@on
            }

            val now = System.currentTimeMillis()
            if (!SkyblockPlayer.canUseCommands || now - lastCommandAt < 750) return@on

            lastCommandAt = now
            command(cmd)
        }
    }

    private val auctionHouseCloseMessages = setOf(
        "There was an error with the auction house! (AUCTION_EXPIRED_OR_NOT_FOUND)",
        "There was an error with the auction house! (INVALID_BID)",
        "Claiming BIN auction...",
        "Visit the Auction House to collect your item!"
    )

    private val bazaarEscrowRefund = Regex("^Escrow refunded \\d+ coins for Bazaar Instant Buy Submit!$")
}
