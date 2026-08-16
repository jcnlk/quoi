package quoi.module.impl.mining

import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack
import quoi.api.abobaui.dsl.px
import quoi.api.abobaui.elements.impl.Text.Companion.shadow
import quoi.api.abobaui.elements.impl.Text.Companion.textSupplied
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.events.ChatEvent
import quoi.api.events.GuiEvent
import quoi.api.events.PacketEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.on
import quoi.api.skyblock.location.Island
import quoi.api.skyblock.location.Location.currentArea
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.visibleIf
import quoi.utils.WorldUtils.tablist
import quoi.utils.render.DrawContextUtils.rect
import quoi.utils.skyblock.item.ItemUtils.lore
import quoi.utils.skyblock.player.PlayerUtils
import quoi.utils.ui.hud.HudManager
import java.util.*

object CommissionDisplay : Module(
    "Commision Display",
    area = Island.Mining,
    desc = "Displays your commissions without you having to open the tab menu!"
) {
    private val completionTitle by switch("Completion title", desc = "Shows a title when a commission is completed.")
    private val highlightDoneCommissions by switch("Highlight done commissions", desc = "Highlights completed commissions in the commissions menu.")
    private val doneCommissionColour by colourPicker("Done commission colour", Colour.GREEN.withAlpha(90), allowAlpha = true).visibleIf { highlightDoneCommissions }

    @Suppress("unused")
    private val hud by textHud("Commision Display") {
        visibleIf { this@CommissionDisplay.enabled && inCommissionArea() }
        column {
            text(
                string = "&cCommissions:",
                colour = colour,
                font = font,
                size = 18.px,
            ).shadow = shadow

            repeat(5) { index ->
                textSupplied(
                    supplier = { commissionLines.getOrNull(index) ?: if (index == 0) NO_COMMISSIONS else "" },
                    colour = colour,
                    font = font,
                    size = 18.px,
                ).shadow = shadow
            }
        }
    }.setting()

    private const val NO_COMMISSIONS = "&cNo commissions available!"
    private const val COMPLETION_SUFFIX = " Commission Complete! Visit the King to claim your rewards!"

    private val commissionRegex = Regex("^ ([^:]+): (\\d+(?:\\.\\d+)?%|DONE)$")

    private var commissionLines: List<String> = emptyList()
    private val completedBooks = WeakHashMap<ItemStack, Boolean>()

    override fun onEnable() = refreshCommissions()

    override fun onDisable() {
        commissionLines = emptyList()
    }

    init {
        on<PacketEvent.ReceivedPost, ClientboundPlayerInfoUpdatePacket> {
            if (packet.actions().none {
                    it == ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER ||
                        it == ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME
                }) return@on

            refreshCommissions()
        }

        on<WorldEvent.Change> {
            commissionLines = emptyList()
        }

        on<ChatEvent.Packet> {
            if (!completionTitle || !inCommissionArea()) return@on
            if (!unformatted.endsWith(COMPLETION_SUFFIX)) return@on
            val commissionName = unformatted.dropLast(COMPLETION_SUFFIX.length).ifEmpty { return@on }

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
            if (screen.title.string != "Commissions") return@on
            if (slot.container is Inventory) return@on
            if (!completedBooks.getOrPut(slot.item) { slot.item.lore?.any { it == "COMPLETED" } == true }) return@on

            ctx.rect(slot.x, slot.y, 16, 16, doneCommissionColour.rgb)
        }
    }

    private fun inCommissionArea(): Boolean = currentArea.isArea(Island.DwarvenMines, Island.CrystalHollows, Island.Mineshaft)

    private fun refreshCommissions() {
        val lines = parseCommissions().map(::formatCommissionLine)
        val layoutChanged = lines.size != commissionLines.size
        commissionLines = lines
        if (layoutChanged) HudManager.reinit(immediately = false)
    }

    private fun parseCommissions(): List<CommissionEntry> {
        if (!inCommissionArea()) return emptyList()

        return tablist
            .asSequence()
            .mapNotNull { it.tabListDisplayName?.string }
            .dropWhile { it != "Commissions:" }
            .drop(1)
            .takeWhile { it.startsWith(' ') }
            .mapNotNull { line ->
                val match = commissionRegex.matchEntire(line) ?: return@mapNotNull null

                CommissionEntry(name = match.groupValues[1], percentage = parsePercentage(match.groupValues[2]))
            }
            .toList()
    }

    internal fun currentActiveCommissionNames(): List<String> = parseCommissions()
        .filter { it.percentage < 100f }
        .map { it.name }

    private fun parsePercentage(value: String): Float =
        if (value == "DONE") 100f else value.dropLast(1).toFloat().coerceIn(0f, 100f)

    private fun formatCommissionLine(entry: CommissionEntry): String {
        val percentage = entry.percentage
        val colour = when {
            percentage >= 100f -> "&a"
            percentage >= 75f -> "&b"
            percentage >= 50f -> "&e"
            percentage >= 25f -> "&6"
            else -> "&c"
        }

        return "&7- &f${entry.name}: $colour${percentage.toString().removeSuffix(".0")}%"
    }

    private data class CommissionEntry(
        val name: String,
        val percentage: Float,
    )
}
