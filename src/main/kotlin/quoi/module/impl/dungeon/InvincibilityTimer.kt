package quoi.module.impl.dungeon

import quoi.api.events.core.on
import quoi.api.abobaui.constraints.impl.positions.Centre
import quoi.api.abobaui.dsl.constrain
import quoi.api.abobaui.dsl.minus
import quoi.api.abobaui.dsl.px
import quoi.api.abobaui.elements.impl.Text.Companion.shadow
import quoi.api.abobaui.elements.impl.Text.Companion.textSupplied
import quoi.api.colour.Colour
import quoi.api.colour.colour
import quoi.api.events.ChatEvent
import quoi.api.skyblock.Location.inSkyblock
import quoi.api.skyblock.SkyblockPlayer
import quoi.api.skyblock.dungeon.Dungeon.inBoss
import quoi.api.skyblock.dungeon.Dungeon.inDungeons
import quoi.module.Module
import quoi.utils.ChatUtils.command as sendCommand
import quoi.utils.StringUtils.noControlCodes

object InvincibilityTimer : Module(
    "Invincibility Timer",
    desc = "Gives visual information about your invincibility times."
) {
    private val invincibilityAnnounce by switch("Announce Invincibility", desc = "Announces when you get invincibility in party chat.")
    private val dungeonOnly by switch("Dungeons only", desc = "Active in dungeons only.")
    private val bossOnly by switch("Boss only", desc = "Active in boss room only.")
//    private val serverTicks by BooleanSetting("Use server ticks", desc = "Uses server ticks instead of real time.")
    val mageReduction by switch("Mage reduction", desc = "Accounts for mage cooldown reduction.")
    val cataLevel by slider("Catacombs level", 0, 0, 50, desc = "Catacombs level for Bonzo's mask ability.")

    private val hud by textHud("Invincibility timer", Colour.PINK, toggleable = false) {
        visibleIf { inSkyblock && (!bossOnly || inBoss) && (!dungeonOnly || inDungeons || bossOnly) }
        column {
            SkyblockPlayer.InvincibilityType.entries.forEach { type ->
                val (col, time) = type.getTime()
                val displayTime = { time().let { if (font.name == "Minecraft" || it != "✔") it else "√" } }
                row(gap = 1.px) {
                    block(
                        constraints = constrain(y = Centre - 5.px, w = 10.px, h = 10.px),
                        colour = colour { if (type.shouldDot()) colour.rgb else Colour.TRANSPARENT.rgb },
                    )
                    row {
                        text(
                            string = "${type.displayName}: ",
                            font = font,
                            size = 18.px,
                            colour = colour
                        ).shadow = shadow
                        textSupplied(
                            supplier = displayTime,
                            font = font,
                            size = 18.px,
                            colour = colour { col().rgb }
                        ).shadow = shadow
                    }
                }
            }
        }
    }.withSettings(::dungeonOnly, ::bossOnly, ::mageReduction, ::cataLevel).setting()

    init {
        on<ChatEvent.Packet> {
            if (dungeonOnly && !inDungeons) return@on
            if (bossOnly && !inBoss) return@on

            val type = SkyblockPlayer.InvincibilityType.entries.firstOrNull { message.noControlCodes.matches(it.regex) } ?: return@on
            type.proc()
            announceProc(type)
        }
    }

    private fun announceProc(type: SkyblockPlayer.InvincibilityType) {
        val used = SkyblockPlayer.InvincibilityType.entries.count { it.currentCooldown > 0 }
        if (invincibilityAnnounce) sendCommand("pc ${type.displayName} Procced! ($used/${SkyblockPlayer.InvincibilityType.entries.size})")
    }
}
