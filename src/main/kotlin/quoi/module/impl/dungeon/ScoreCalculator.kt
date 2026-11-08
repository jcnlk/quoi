package quoi.module.impl.dungeon

import quoi.api.abobaui.elements.impl.Text.Companion.shadow
import quoi.api.abobaui.elements.impl.Text.Companion.textSupplied
import quoi.api.skyblock.dungeon.Dungeon.hasStarted
import quoi.api.skyblock.dungeon.Dungeon.score
import quoi.api.skyblock.dungeon.ScoreCalculation
import quoi.api.skyblock.location.Island
import quoi.api.skyblock.location.invoke
import quoi.module.Module
import quoi.utils.ThemeManager.theme

object ScoreCalculator : Module(
    "Score Calculator",
    desc = "Calculates dungeon score, including secrets, time and bonus points.",
    area = Island.Dungeon()
) {
    val forcePaul by switch("Force Paul", desc = "Adds the EZPZ bonus even when the mayor API is unavailable.")

    @Suppress("unused")
    private val hud by textHud("Dungeon score", toggleable = false) {
        visibleIf { preview || hasStarted }
        textSupplied(
            supplier = {
                val s = if (preview) 300 else score
                "§eScore: ${ScoreCalculation.colour(s)}$s"
            },
            font = font,
            colour = colour,
            size = theme.textSize
        ).shadow = shadow
    }.setting()
}
