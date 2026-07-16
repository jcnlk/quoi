package quoi.module.impl.render

import quoi.api.ServerInfo.averagePing
import quoi.api.ServerInfo.averageTps
import quoi.api.ServerInfo.currentPing
import quoi.api.ServerInfo.currentTps
import quoi.api.ServerInfo.medianPing
import quoi.api.abobaui.constraints.Constraint
import quoi.api.abobaui.constraints.impl.measurements.Undefined
import quoi.api.abobaui.dsl.size
import quoi.api.abobaui.dsl.withScale
import quoi.api.abobaui.elements.Element
import quoi.api.colour.Colour
import quoi.api.commands.QuoiCommand
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.visibleIf
import quoi.utils.ChatUtils
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.StringUtils.percentColour
import quoi.utils.StringUtils.toFixed
import quoi.utils.WorldUtils.day
import quoi.utils.render.DrawContextUtils.drawText
import quoi.utils.ui.data.Anchor
import quoi.utils.ui.elements.clock
import quoi.utils.ui.hud.impl.TextHud
import quoi.utils.ui.rendering.UIRenderer
import quoi.utils.ui.textPair
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object InfoHud : Module(
    name = "Info HUD",
    desc = "Shows useful information on the screen.",
) {
    private val nameColour by colourPicker("Name colour", Colour.RGB(50, 150, 220), desc = "Colour of the stat labels.")
    private val direction by selector("Direction", Direction.Horizontal)
    private val showFps by switch("Show FPS", true, desc = "Shows the FPS in the HUD.")
    private val showTps by switch("Show TPS", true, desc = "Shows the TPS in the HUD.")
    private val showPing by switch("Show Ping", true, desc = "Shows the ping in the HUD.")
    private val showDay by switch("Show Day", true, desc = "Shows the current Minecraft day in the HUD.")
    private val tpsType by selector("TPS type", TpsType.Average).visibleIf { showTps }
    private val pingType by selector("Ping type", PingType.Average).visibleIf { showPing }

    private val hud: TextHud = textHud("Info HUD") hudScope@ {
        visibleIf { preview || enabledMetrics().isNotEmpty() }

        object : Element(size(InfoSize(true), InfoSize(false))), InfoElement {
            init {
                usingCtx = font.name == "Minecraft"
            }

            private val lineHeight get() = 18f

            override fun getDefaultPositions() = Undefined to Undefined

            override fun measuredSize(horizontal: Boolean): Float {
                val metrics = displayMetrics()
                if (metrics.isEmpty()) return 1f

                return if (direction.selected == Direction.Horizontal) {
                    if (horizontal) 1f + metrics.sumOf { metricWidth(it, trailingSpace = true).toDouble() }.toFloat()
                    else lineHeight
                } else {
                    if (horizontal) 1f + metrics.maxOf { metricWidth(it, trailingSpace = false) }
                    else lineHeight * metrics.size
                }
            }

            override fun drawCtx() {
                withScale {
                    drawMetrics { text, x, y, colour ->
                        ctx.drawText(text, x, y, colour.rgb, lineHeight / mc.font.lineHeight, shadow)
                    }
                }
            }

            override fun drawNvg() {
                if (font.name == "Minecraft") return

                drawMetrics { text, x, y, colour ->
                    if (shadow) {
                        val offset = lineHeight / 25f
                        UIRenderer.formattedText(text, this.x + x + offset, this.y + y + offset, lineHeight, Colour.BLACK.rgb, font)
                    }
                    UIRenderer.formattedText(text, this.x + x, this.y + y, lineHeight, colour.rgb, font)
                }
            }

            private fun drawMetrics(drawText: (String, Float, Float, Colour) -> Unit) {
                val metrics = displayMetrics()
                var x = 1f
                var y = 0f

                metrics.forEach { metric ->
                    val label = "${metric.label} "
                    val value = "${metric.value()}${if (direction.selected == Direction.Horizontal) " " else ""}"

                    drawText(label, x, y, nameColour)
                    x += textWidth(label)
                    drawText(value, x, y, this@hudScope.colour)

                    if (direction.selected == Direction.Horizontal) {
                        x += textWidth(value)
                    } else {
                        x = 1f
                        y += lineHeight
                    }
                }
            }

            private fun displayMetrics(): List<Metric> {
                return enabledMetrics()
            }

            private fun metricWidth(metric: Metric, trailingSpace: Boolean): Float {
                val label = "${metric.label} "
                val value = "${metric.value()}${if (trailingSpace) " " else ""}"
                return textWidth(label) + textWidth(value)
            }

            private fun textWidth(text: String): Float =
                if (font.name == "Minecraft") mc.font.width(text) * (lineHeight / mc.font.lineHeight)
                else UIRenderer.textWidth(text.noControlCodes, lineHeight, font)
        }.add()
    }

    @Suppress("unused")
    private val hudSetting by hud.withSettings(::nameColour, ::direction, ::showFps, ::showTps, ::showPing, ::showDay, ::tpsType, ::pingType).setting()

    private val t12 = DateTimeFormatter.ofPattern("hh:mm a")
    private val t24 = DateTimeFormatter.ofPattern("HH:mm")
    private val clockType by segmented("Type", "Text", listOf("Text", "Clock²"))
    private val clockFormat by switch("Twelve hours")
    private val clockColour by colourPicker("Colour", Colour.WHITE).visibleIf { clockType.selected == "Text" }
    private val clockShadow by switch("Shadow", true).visibleIf { clockType.selected == "Text" }
    private val clockFont by segmented("Font", TextHud.HudFont.Minecraft).visibleIf { clockType.selected == "Text" }
    private val clockAnchor by selector("Anchor", Anchor.TopLeft).visibleIf { clockType.selected == "Text" }
    private val clockSpeed by slider("Speed", 0.4, 0.1, 1.0, 0.1, unit = "s").visibleIf { clockType.selected != "Text" }

    @Suppress("unused")
    private val timeHud by hud("Time display") {
        if (clockType.selected == "Text") {
            textPair(
                string = "Time:",
                supplier = { LocalTime.now().format(if (clockFormat) t12 else t24) },
                labelColour = clockColour,
                shadow = clockShadow,
                font = clockFont.selected.get()
            )
        } else {
            clock(clockFormat, clockSpeed)
        }
    }.withSettings(::clockType, ::clockFormat, ::clockColour, ::clockShadow, ::clockFont, ::clockAnchor, ::clockSpeed).setting()

    init {
        QuoiCommand.command.sub("tps") {
            val current = currentTps
            val average = averageTps
            ChatUtils.modMessage(
                "Tps: ${(current - 15).percentColour(5.0)}${current.toFixed(0)}&r, " +
                    "Average: ${(average - 15).percentColour(5.0)}${average.toFixed(2)}"
            )
        }.description("Shows tps.")

        QuoiCommand.command.sub("fps") {
            ChatUtils.modMessage("FPS: &a${mc.fps}")
        }.description("Shows fps.")

        QuoiCommand.command.sub("ping") {
            val current = currentPing
            val average = averagePing
            val currentColour = when {
                current < 50.0 -> "a"
                current < 100.0 -> "2"
                current < 150.0 -> "e"
                current < 200.0 -> "6"
                else -> "c"
            }
            val averageColour = when {
                average < 50.0 -> "a"
                average < 100.0 -> "2"
                average < 150.0 -> "e"
                average < 200.0 -> "6"
                else -> "c"
            }
            ChatUtils.modMessage(
                "Ping: §$currentColour${current.toFixed(2)} §7ms&r, " +
                    "Average: §$averageColour${average.toFixed(2)} §7ms"
            )
        }.description("Shows ping.")
    }

    private fun enabledMetrics(): List<Metric> = buildList {
        if (showTps) add(Metric("TPS:") { tpsType.selected.value().formatTps(1) })
        if (showFps) add(Metric("FPS:") { mc.fps })
        if (showPing) add(Metric("Ping:") { pingType.selected.value().formatPing })
        if (showDay) add(Metric("Day:") { mc.level?.day ?: 0 })
    }

    private val Double.formatPing get() = "${toFixed(0)}ms"

    private fun Float.formatTps(decimals: Int = 0) = this.toFixed(decimals)

    private data class Metric(val label: String, val value: () -> Any?)

    @Suppress("unused")
    private enum class Direction {
        Horizontal,
        Vertical
    }

    @Suppress("unused")
    private enum class PingType(val value: () -> Double) {
        Average({ averagePing }),
        Current({ currentPing }),
        Median({ medianPing })
    }

    @Suppress("unused")
    private enum class TpsType(val value: () -> Float) {
        Average({ averageTps }),
        Current({ currentTps })
    }

    private interface InfoElement {
        fun measuredSize(horizontal: Boolean): Float
    }

    private class InfoSize(private val horizontal: Boolean) : Constraint.Size {
        override fun calculateSize(element: Element, horizontal: Boolean): Float {
            return (element as? InfoElement)?.measuredSize(this.horizontal) ?: 1f
        }
    }
}
