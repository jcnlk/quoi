package quoi.module.impl.dungeon.autocroesus

import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack
import quoi.utils.StringUtils.formattedString
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.StringUtils.toFixed
import kotlin.math.roundToInt

internal fun ItemStack.cleanLore(): List<String> =
    get(DataComponents.LORE)?.lines?.map { it.string.noControlCodes } ?: emptyList()

internal fun ItemStack.formattedLore(): List<String> =
    get(DataComponents.LORE)?.lines?.map { it.formattedString } ?: emptyList()

internal fun Boolean?.orFalse() = this ?: false

internal fun parseRoman(value: String): Int? {
    if (!Regex("^[IVXLCDM]+$").matches(value)) return null
    val numerals = mapOf('I' to 1, 'V' to 5, 'X' to 10, 'L' to 50, 'C' to 100, 'D' to 500, 'M' to 1000)
    var sum = 0
    var i = 0
    while (i < value.length) {
        val current = numerals[value[i]] ?: return null
        val next = value.getOrNull(i + 1)?.let(numerals::get) ?: 0
        if (current < next) {
            sum += next - current
            i += 2
        } else {
            sum += current
            i++
        }
    }
    return sum
}

internal fun Collection<String>.containsId(id: String): Boolean = any { it.equals(id, true) }

internal fun Double.coinsFromMillions(): Int = (this * 1_000_000).roundToInt()

internal fun displayNameFromId(id: String): String =
    id.split("_", ";").joinToString(" ") { it.lowercase().replaceFirstChar(Char::uppercaseChar) }

internal fun formatCoins(value: Int): String {
    val abs = kotlin.math.abs(value)
    val sign = if (value < 0) "-" else ""
    return if (abs >= 1_000_000) "$sign${(abs / 1_000_000.0).toFixed(2)}M" else "$sign${(abs / 1_000.0).toFixed(1)}K"
}

internal fun profitText(value: Int): String = if (value <= 0) "§c${formatCoins(value)}" else "§a+${formatCoins(value)}"
