package quoi.module.impl.mining.glacitetunnels

import net.minecraft.world.item.ItemStack
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.skyblock.item.ItemUtils.lore

internal data class ActiveGlaciteCommission(val routeTarget: String?)

internal fun List<ItemStack>.selectActiveGlaciteCommission(
    graph: TunnelGraph,
    preferredSlot: Int?,
): ActiveGlaciteCommission? {
    if (preferredSlot != null) return getOrNull(preferredSlot)?.activeGlaciteCommission(graph)

    val activeCommissions = mapNotNull { it.activeGlaciteCommission(graph) }
    return activeCommissions.firstOrNull { it.routeTarget != null } ?: activeCommissions.firstOrNull()
}

private fun ItemStack.activeGlaciteCommission(graph: TunnelGraph): ActiveGlaciteCommission? {
    val lines = lore?.map { it.noControlCodes.trim() }.orEmpty()
    if (lines.none(GLACITE_REWARD::matches) || isCompletedCommission()) return null

    val name = sequenceOf(hoverName.string.noControlCodes.trim())
        .plus(lines.asSequence())
        .firstOrNull(COLLECTOR_COMMISSION::matches)
        ?: return null
    return ActiveGlaciteCommission(graph.targetNameForCommission(name))
}

internal fun ItemStack.isCompletedCommission(): Boolean =
    lore?.any { it.noControlCodes.contains("COMPLETED", ignoreCase = true) } == true

private val COLLECTOR_COMMISSION = Regex("^.+ Collector$")
private val GLACITE_REWARD = Regex("^- [\\d,]+ Glacite Powder$")
