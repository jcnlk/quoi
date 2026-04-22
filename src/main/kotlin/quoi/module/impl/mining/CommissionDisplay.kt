package quoi.module.impl.mining

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack
import quoi.api.abobaui.dsl.px
import quoi.api.abobaui.elements.impl.Text.Companion.shadow
import quoi.api.abobaui.elements.impl.Text.Companion.textSupplied
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.events.AreaEvent
import quoi.api.events.ChatEvent
import quoi.api.events.GuiEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.skyblock.Island
import quoi.api.skyblock.IslandArea
import quoi.api.skyblock.Location.currentArea
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.visibleIf
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.WorldUtils.tablist
import quoi.utils.render.DrawContextUtils.rect
import quoi.utils.skyblock.ItemUtils.lore
import quoi.utils.skyblock.player.PlayerUtils
import quoi.utils.ui.hud.HudManager
import quoi.utils.ui.hud.impl.TextHud

object CommissionDisplay : Module(
    "Commision Display",
    area = IslandArea.MiningIslands,
    desc = "Displays your commissions without you having to open the tab menu!"
) {
    private const val MAX_DISPLAYED_COMMISSIONS = 8
    private const val COMMISSION_SECTION_TITLE = "Commissions"
    private const val TITLE = "&cCommissions:"
    private const val NONE_AVAILABLE = "&cNo commissions available!"

    private val infoSectionRegex = Regex("^(?:Info|Account Info|Player Stats|Dungeon Stats)$")
    private val commissionRegex = Regex("^(.*): ([\\d,.]+%|DONE)$")
    private val commissionCompleteRegex = Regex("^(.*) Commission Complete! Visit the King to claim your rewards!$")
    private val previewLines = listOf(
        TITLE,
        "&7- &fExample: &a100%",
        "&7- &fExample: &b75%",
        "&7- &fExample: &c7%",
    )
    private val completionTitle by switch("Completion title", desc = "Shows a title when a commission is completed.")
    private val highlightDoneCommissions by switch("Highlight done commissions", desc = "Highlights completed commissions in the commissions menu.")
    private val doneCommissionColour by colourPicker("Done commission colour", Colour.GREEN.withAlpha(90), allowAlpha = true)
        .visibleIf { highlightDoneCommissions }

    private val hud by textHud("Commision Display", font = TextHud.HudFont.Minecraft, toggleable = false) {
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

            repeat(MAX_DISPLAYED_COMMISSIONS) { index ->
                textSupplied(
                    supplier = { displayLine(index) },
                    colour = colour,
                    font = font,
                    size = 18.px,
                ).shadow = shadow
            }
        }
    }.setting()

    private var commissions: List<CommissionEntry> = emptyList()
    private var clientTicks = 0

    override fun onEnable() {
        refreshCommissions()
        super.onEnable()
    }

    override fun onDisable() {
        commissions = emptyList()
        super.onDisable()
    }

    init {
        on<TickEvent.End> {
            clientTicks++
            if (clientTicks % 5 == 0) refreshCommissions()
        }

        on<WorldEvent.Change> {
            commissions = emptyList()
            clientTicks = 0
        }

        on<AreaEvent.Main> {
            refreshCommissions()
        }

        on<ChatEvent.Packet> {
            if (!completionTitle || !inCommissionArea()) return@on

            val commissionName = commissionCompleteRegex.matchEntire(message.noControlCodes)
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?: return@on

            PlayerUtils.setTitle(
                title = commissionName,
                subtitle = "§aCommission Complete!",
                fadeIn = 0,
                stayAlive = 40,
                fadeOut = 10,
            )
        }

        on<GuiEvent.Slot.Draw> {
            if (!highlightDoneCommissions || !inCommissionArea()) return@on

            val screen = screen as? AbstractContainerScreen<*> ?: return@on
            if (screen.title.string.noControlCodes != COMMISSION_SECTION_TITLE) return@on
            if (slot.container is Inventory) return@on
            if (!slot.item.isCompletedCommissionBook()) return@on

            ctx.rect(slot.x, slot.y, 16, 16, doneCommissionColour.rgb)
        }
    }

    private fun inCommissionArea(): Boolean = currentArea.isArea(
        Island.DwarvenMines,
        Island.CrystalHollows,
        Island.Mineshaft,
    )

    private fun refreshCommissions() {
        val parsed = parseCommissions()
        if (parsed.size != commissions.size) {
            commissions = parsed
            HudManager.reinit(immediately = false)
            return
        }
        commissions = parsed
    }

    private fun parseCommissions(): List<CommissionEntry> {
        if (!inCommissionArea()) return emptyList()

        var inCommissionWidget = false
        val parsed = mutableListOf<CommissionEntry>()

        for (line in infoTabLines()) {
            val trimmed = line.trim()

            if (trimmed.equals(COMMISSION_SECTION_TITLE, true) || trimmed.equals("$COMMISSION_SECTION_TITLE:", true)) {
                inCommissionWidget = true
                continue
            }

            if (!inCommissionWidget) continue

            val match = commissionRegex.matchEntire(trimmed) ?: break
            parsed += CommissionEntry(
                name = match.groupValues[1].trim(),
                progress = parseProgress(match.groupValues[2]),
            )
        }

        return parsed
    }

    private fun infoTabLines(): List<String> = tablist
        .take(80)
        .map { it.tabListDisplayName?.string ?: it.profile.name }
        .chunked(20)
        .filter { chunk -> chunk.firstOrNull()?.noControlCodes?.trim()?.matches(infoSectionRegex) == true }
        .flatMap { chunk -> chunk.drop(1) }
        .map { it.noControlCodes.trimEnd() }
        .filter(String::isNotBlank)

    private fun displayLine(index: Int): String = when {
        commissions.isEmpty() && index == 0 -> NONE_AVAILABLE
        commissions.isEmpty() -> ""
        else -> commissions.getOrNull(index)?.let(::formatCommissionLine).orEmpty()
    }

    private fun parseProgress(value: String): Float {
        if (value.equals("DONE", true)) return 1f

        val percent = value
            .removeSuffix("%")
            .replace(",", "")
            .toFloatOrNull()
            ?: return 0f

        return (percent / 100f).coerceIn(0f, 1f)
    }

    private fun formatCommissionLine(entry: CommissionEntry): String {
        val percent = (entry.progress * 100f).coerceIn(0f, 100f)
        val value = if (percent % 1f == 0f) percent.toInt().toString() else "%.1f".format(percent)
        return "&7- &f${entry.name}: ${progressColour(percent)}${value}%"
    }

    private fun progressColour(percent: Float): String = when {
        percent >= 100f -> "&a"
        percent >= 75f -> "&b"
        percent >= 50f -> "&e"
        percent >= 25f -> "&6"
        else -> "&c"
    }

    private fun ItemStack.isCompletedCommissionBook(): Boolean {
        if (isEmpty) return false
        return lore?.any { it.noControlCodes.contains("COMPLETED", ignoreCase = true) } == true
    }

    private data class CommissionEntry(
        val name: String,
        val progress: Float,
    )
}
