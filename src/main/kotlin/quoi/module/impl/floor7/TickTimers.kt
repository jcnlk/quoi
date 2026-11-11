package quoi.module.impl.floor7

import quoi.api.abobaui.elements.impl.Text.Companion.shadow
import quoi.api.abobaui.elements.impl.Text.Companion.textSupplied
import quoi.api.events.ChatEvent
import quoi.api.events.DungeonEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.on
import quoi.api.skyblock.dungeon.Dungeon.deathTick
import quoi.api.skyblock.dungeon.Dungeon.inBoss
import quoi.api.skyblock.dungeon.Dungeon.inClear
import quoi.api.skyblock.dungeon.Floor7
import quoi.api.skyblock.dungeon.enums.Phase
import quoi.api.skyblock.dungeon.enums.Stage
import quoi.api.skyblock.location.Island
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.visibleIf
import quoi.utils.StringUtils.toFixed
import quoi.utils.ThemeManager.theme
import quoi.utils.ui.hud.Hud

object TickTimers : Module(
    "Tick Timers",
    desc = "Displays death tick, pad, and Goldor timers.",
    area = Island.Dungeon
) {
    private const val GOLDOR_START_TICKS = 64

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
        visibleIf { (goldorStart >= 0 && startTimer) || goldorTick >= 0 }
        textSupplied(
            supplier = { if (goldorStart >= 0 && startTimer) formatTime(goldorStart, GOLDOR_START_TICKS) else formatTime(if (preview) 40 else goldorTick, 60) },
            size = theme.textSize,
            font = font,
            colour = colour
        ).shadow = shadow
    }.setting()

    private val startTimer by switch("Goldor start timer").visibleIf { goldorHud.enabled }

    @Suppress("unused")
    private val deathTickHud by textHud("Death tick") {
        visibleIf { inClear && deathTick >= 0 }
        textSupplied(
            supplier = { formatTime(if (preview) 15 else deathTick, 40) },
            size = theme.textSize,
            font = font,
            colour = colour
        ).shadow = shadow
    }.setting()

    private var goldorTick = -1
    private var goldorStart = -1
    private var goldorStartTriggered = false
    private var padTick = -1

    init {
        on<WorldEvent.Change> {
            reset()
        }

        on<TickEvent.Server> {
            if (!inBoss) return@on
            if (goldorTick == 0 && goldorStart <= 0 && goldorHud.enabled) goldorTick = 60
            if (goldorTick >= 0 && goldorHud.enabled) goldorTick--
            if (goldorStart >= 0 && goldorHud.enabled) goldorStart--
            if (padTick == 0 && padHud.enabled) padTick = 20
            if (padTick >= 0 && padHud.enabled) padTick--
        }

        on<ChatEvent.Packet> {
            if (!Floor7.inF7Boss || !goldorHud.enabled || goldorStartTriggered) return@on
            if (unformatted == "[BOSS] Storm: I should have known that I stood no chance.") {
                goldorStart = GOLDOR_START_TICKS
                goldorStartTriggered = true
            }
        }

        on<DungeonEvent.PhaseComplete> {
            if (goldorHud.enabled && phase == Phase.P2 && Floor7.inPhase(Phase.P3)) goldorTick = 60
            if (padHud.enabled && phase == Phase.P1) padTick = 20
        }

        on<DungeonEvent.StageComplete.Full> {
            if (stage != Stage.S4) return@on
            goldorStart = -1
            goldorTick = -1
        }
    }

    override fun onDisable() = reset()

    private fun reset() {
        goldorTick = -1
        goldorStart = -1
        goldorStartTriggered = false
        padTick = -1
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