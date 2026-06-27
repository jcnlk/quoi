package quoi.module.impl.dungeon

import quoi.api.events.ChatEvent
import quoi.api.events.core.on
import quoi.api.skyblock.dungeon.Dungeon
import quoi.module.Module
import quoi.utils.ChatUtils.modMessage
import quoi.utils.StringUtils
import quoi.utils.ui.textPair

object WarpCooldown : Module(
    "Warp Cooldown",
    desc = "Dungeon warp cooldown display"
) {
    private val hud by textHud("Warp cooldown", toggleable = false) {
        visibleIf { Dungeon.warpCooldown != 0L }

        textPair(
            string = "Warp:",
            supplier = { StringUtils.formatTime(Dungeon.warpCooldown) },
            labelColour = colour,
            shadow = shadow,
            font = font
        )
    }.setting()

    private val blockInstanceCommands by switch("Block instance commands", desc = "Blocks instance commands while the dungeon warp cooldown is active.")

    init {
        on<ChatEvent.Sent> {
            if (!blockInstanceCommands || !isCommand) return@on
            if (Dungeon.warpCooldown <= 0L) return@on

            val commandName = message
                .trimStart()
                .removePrefix("/")
                .substringBefore(" ")

            if (!commandName.equals("joininstance", ignoreCase = true)) return@on

            cancel()
            modMessage("&cYou are still on warp cooldown for &e${StringUtils.formatTime(Dungeon.warpCooldown)}")
        }
    }
}