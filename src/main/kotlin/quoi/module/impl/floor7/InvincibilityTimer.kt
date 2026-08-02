package quoi.module.impl.floor7

import quoi.api.abobaui.constraints.impl.positions.Centre
import quoi.api.abobaui.dsl.constrain
import quoi.api.abobaui.dsl.minus
import quoi.api.abobaui.dsl.px
import quoi.api.abobaui.elements.impl.Text.Companion.shadow
import quoi.api.abobaui.elements.impl.Text.Companion.textSupplied
import quoi.api.colour.Colour
import quoi.api.colour.colour
import quoi.api.events.ChatEvent
import quoi.api.events.core.on
import quoi.api.skyblock.SkyblockPlayer
import quoi.api.skyblock.dungeon.Dungeon.inBoss
import quoi.api.skyblock.location.Island
import quoi.api.skyblock.location.Location.inSkyblock
import quoi.module.Module
import quoi.utils.ChatUtils.command as sendCommand

object InvincibilityTimer : Module(
    "Invincibility Timer",
    desc = "Gives visual information about your invincibility times.",
    area = Island.Dungeon
) {
    private val invincibilityAnnounce by switch("Announce Invincibility", desc = "Announces when you get invincibility in party chat.")
    private val bossOnly by switch("Boss only", desc = "Active in boss room only.")
    val mageReduction by switch("Mage reduction", desc = "Accounts for mage cooldown reduction.")
    val cataLevel by slider("Catacombs level", 0, 0, 50, desc = "Catacombs level for Bonzo's mask ability.")

    @Suppress("unused")
    private val hud by textHud("Invincibility timer", Colour.PINK, toggleable = false) {
        visibleIf { inSkyblock && (!bossOnly || inBoss) }
        column {
            SkyblockPlayer.InvincibilityType.entries.forEach { type ->
                val displayTime = {
                    type.getTime().let { if (font.name == "Minecraft" || !it.endsWith("✔")) it else "${it.dropLast(1)}√" }
                }
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
                            colour = colour
                        ).shadow = shadow
                    }
                }
            }
        }
    }.withSettings(::bossOnly, ::mageReduction, ::cataLevel).setting()

    init {
        on<ChatEvent.Packet> {
            if (bossOnly && !inBoss) return@on

            val type = SkyblockPlayer.InvincibilityType.entries.firstOrNull { unformatted.matches(it.regex) } ?: return@on
            type.proc()

            val used = SkyblockPlayer.InvincibilityType.entries.count { it.currentCooldown > 0 }
            if (invincibilityAnnounce) sendCommand("pc ${type.displayName} Procced! ($used/${SkyblockPlayer.InvincibilityType.entries.size})")
        }
    }
}
