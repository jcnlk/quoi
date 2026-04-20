package quoi.module.impl.mining

import quoi.api.abobaui.dsl.px
import quoi.api.abobaui.elements.impl.Text.Companion.shadow
import quoi.api.abobaui.elements.impl.Text.Companion.textSupplied
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.skyblock.Island
import quoi.api.skyblock.IslandArea
import quoi.api.skyblock.Location.currentArea
import quoi.module.Module
import quoi.utils.StringUtils.formattedString
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.WorldUtils.tablist
import quoi.utils.ui.hud.impl.TextHud

object CommissionDisplay : Module(
    "Commission display",
    area = IslandArea.MiningIslands,
    desc = "Displays your commissions without you having to open the tab menu!"
) {
    private const val MAX_DISPLAYED_COMMISSIONS = 8
    private const val TITLE = "&cCommissions:"
    private const val NONE_AVAILABLE = "&cNo commissions available!"
    private val commissionRegex = Regex("^(.+?):\\s*(.+)$")
    private val percentRegex = Regex("([0-9]+(?:\\.[0-9]+)?)%")
    private val previewLines = listOf(
        TITLE,
        "&7- &fExample: &a100%",
        "&7- &fExample: &b75%",
        "&7- &fExample: &c7%",
    )

    private val hud by textHud("Commission display", font = TextHud.HudFont.Minecraft, toggleable = false) {
        visibleIf { this@CommissionDisplay.enabled && inCommissionArea() }

        column {
            if (preview) {
                previewLines.forEach { line ->
                    text(
                        string = line,
                        font = font,
                        size = 18.px,
                        colour = colour,
                    ).shadow = shadow
                }
                return@column
            }

            textSupplied(
                supplier = { TITLE },
                colour = colour,
                font = font,
                size = 18.px,
            ).shadow = shadow

            val noneLine = textSupplied(
                supplier = { NONE_AVAILABLE },
                colour = colour,
                font = font,
                size = 18.px,
            )
            noneLine.shadow = shadow
            noneLine.visibleIf { commissions.isEmpty() }

            repeat(MAX_DISPLAYED_COMMISSIONS) { index ->
                val commissionLine = textSupplied(
                    supplier = { commissions.getOrNull(index)?.let(::formatCommissionLine).orEmpty() },
                    colour = colour,
                    font = font,
                    size = 18.px,
                )
                commissionLine.shadow = shadow
                commissionLine.visibleIf { commissions.getOrNull(index) != null }
            }
        }
    }.setting()

    private var commissions: List<CommissionEntry> = emptyList()

    override fun onEnable() {
        commissions = parseCommissions()
        super.onEnable()
    }

    override fun onDisable() {
        commissions = emptyList()
        super.onDisable()
    }

    init {
        on<TickEvent.Server> {
            if (ticks % 20 != 0) return@on
            commissions = parseCommissions()
        }

        on<WorldEvent.Change> {
            commissions = emptyList()
        }
    }

    private fun inCommissionArea(): Boolean =
        currentArea.isArea(Island.DwarvenMines, Island.CrystalHollows, Island.Mineshaft)

    private fun parseCommissions(): List<CommissionEntry> {
        if (!inCommissionArea()) return emptyList()

        var inSection = false
        val parsed = mutableListOf<CommissionEntry>()

        for (line in tablist.asSequence().map { TabLine.from(it.tabListDisplayName?.formattedString ?: it.profile.name) }) {
            val trimmed = line.clean.trim()

            if (!inSection) {
                if (trimmed.equals("Commissions", true) || trimmed.equals("Commissions:", true)) {
                    inSection = true
                }
                continue
            }

            if (!line.clean.startsWith(" ")) break

            val entry = commissionRegex.matchEntire(trimmed) ?: continue
            val name = entry.groupValues[1].trim()
            val progress = parseProgress(entry.groupValues[2])

            parsed += CommissionEntry(name, progress)
        }

        return parsed
    }

    private fun parseProgress(value: String): Float {
        val percent = percentRegex.find(value)?.groupValues?.getOrNull(1)?.toFloatOrNull()
        return ((percent ?: 100f) / 100f).coerceIn(0f, 1f)
    }

    private fun formatCommissionLine(entry: CommissionEntry): String {
        val percent = (entry.progress * 100f).coerceIn(0f, 100f)
        val value = if (percent % 1f == 0f) percent.toInt().toString() else "%.1f".format(percent)
        return "&7- &f${entry.name}: ${progressColour(percent)}$value%"
    }

    private fun progressColour(percent: Float): String = when {
        percent >= 100f -> "&a"
        percent >= 75f -> "&b"
        percent >= 50f -> "&e"
        percent >= 25f -> "&6"
        else -> "&c"
    }

    private data class CommissionEntry(
        val name: String,
        val progress: Float,
    )

    private data class TabLine(
        val clean: String,
    ) {
        companion object {
            fun from(raw: String) = TabLine(raw.noControlCodes.trimEnd())
        }
    }

}
