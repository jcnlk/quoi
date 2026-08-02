package quoi.module.impl.floor7

import quoi.api.abobaui.elements.impl.Text.Companion.shadow
import quoi.api.abobaui.elements.impl.Text.Companion.textSupplied
import quoi.api.events.DungeonEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.on
import quoi.api.skyblock.dungeon.Dungeon.deathTick
import quoi.api.skyblock.dungeon.Dungeon.inBoss
import quoi.api.skyblock.dungeon.Phase
import quoi.api.skyblock.dungeon.Stage
import quoi.api.skyblock.location.Island
import quoi.api.skyblock.location.invoke
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.visibleIf
import quoi.utils.StringUtils.toFixed
import quoi.utils.ThemeManager.theme
import quoi.utils.ui.hud.Hud

object TickTimers : Module(
    "Tick Timers",
    desc = "Displays tick timers for floor seven boss fight.",
    area = Island.Dungeon(7)
) {
    private val showInTicks by switch("Show in ticks")

    private val padHud by textHud("Pad tick") {
        visibleIf { padTick >= 0 }
        textSupplied(
            supplier = { formatTime(if (preview) 15 else padTick, 20) },
            size = theme.textSize,
            font = font,
            colour = colour
        ).shadow = shadow
    }.setting()

    private val goldorHud: Hud by textHud("Goldor death tick") {
        visibleIf { goldorStart >= 0 || goldorTick >= 0 }
        textSupplied(
            supplier = { if (goldorStart >= 0 && startTimer) formatTime(goldorStart, 104) else formatTime(if (preview) 40 else goldorTick, 60) },
            size = theme.textSize,
            font = font,
            colour = colour
        ).shadow = shadow
    }.setting()

    private val startTimer by switch("Goldor start timer").visibleIf { goldorHud.enabled }

    @Suppress("unused")
    private val deathTickHud by textHud("Death tick") { // maybe make an option to show it before dung start only
        visibleIf { deathTick >= 0 }
        textSupplied(
            supplier = { formatTime(if (preview) 15 else deathTick, 40) },
            size = theme.textSize,
            font = font,
            colour = colour
        ).shadow = shadow
    }.setting()

    private var goldorTick = -1
    private var goldorStart = -1
    private var padTick = -1

    init {
        on<WorldEvent.Change> {
            goldorTick = -1
            goldorStart = -1
            padTick = -1
        }

        on<TickEvent.Server> {
            if (!inBoss) return@on
            if (goldorTick == 0 && goldorStart <= 0 && goldorHud.enabled) goldorTick = 60
            if (goldorTick >= 0 && goldorHud.enabled) goldorTick--
            if (goldorStart >= 0 && goldorHud.enabled) goldorStart--
            if (padTick == 0 && padHud.enabled) padTick = 20
            if (padTick >= 0 && padHud.enabled) padTick--
        }

        on<DungeonEvent.PhaseComplete> {
            if (goldorHud.enabled && phase == Phase.P2) goldorTick = 60
            if (padHud.enabled && phase == Phase.P1) padTick = 20
        }

        on<DungeonEvent.StageComplete.Full> {
            if (stage != Stage.S4) return@on
            goldorStart = -1
            goldorTick = -1
        }
    }

    private fun formatTime(time: Int, max: Int): String {
        val col = when {
            time.toFloat() >= max * 0.66 -> "§a"
            time.toFloat() >= max * 0.33 -> "§6"
            else -> "§c"
        }
        val display = if (showInTicks) "$time" else (time / 20f).toFixed()
        return "$col$display"
    }
}