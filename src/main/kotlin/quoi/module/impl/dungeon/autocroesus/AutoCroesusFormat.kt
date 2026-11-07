package quoi.module.impl.dungeon.autocroesus

import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack
import quoi.utils.StringUtils.formattedString
import quoi.utils.StringUtils.toFixed
import kotlin.math.roundToInt

internal fun ItemStack.formattedLore(): List<String> =
    get(DataComponents.LORE)?.lines?.map { it.formattedString } ?: emptyList()

internal fun Boolean?.orFalse() = this ?: false

internal fun Collection<String>.containsId(id: String): Boolean = any { it.equals(id, true) }

internal fun Double.coinsFromMillions(): Int = (this * 1_000_000).roundToInt()

internal fun displayNameFromId(id: String): String =
    id.split("_", ";").joinToString(" ") { it.lowercase().replaceFirstChar(Char::uppercaseChar) }

internal fun formatCoins(value: Int): String = formatCoins(value.toLong())

internal fun formatCoins(value: Long): String {
    val abs = kotlin.math.abs(value.toDouble())
    val sign = if (value < 0) "-" else ""
    return if (abs >= 1_000_000) "$sign${(abs / 1_000_000.0).toFixed(2)}M" else "$sign${(abs / 1_000.0).toFixed(1)}K"
}

internal fun profitText(value: Int): String = if (value <= 0) "§c${formatCoins(value)}" else "§a+${formatCoins(value)}"
