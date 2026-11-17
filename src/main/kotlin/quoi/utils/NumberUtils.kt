package quoi.utils

import java.text.NumberFormat
import java.util.*
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.round

fun Number.round(decimals: Int): Number {
    require(decimals >= 0) { "Decimals must be non-negative" }
    val factor = 10.0.pow(decimals)
    return round(this.toDouble() * factor) / factor
}

inline val Number.rad get() = (toFloat() * PI / 180).toFloat()
inline val Number.deg get() = (toFloat() * 180 / Math.PI).toFloat()
inline val Int.sq get() = this * this
inline val Float.sq get() = this * this
inline val Double.sq get() = this * this

fun Number.commas(): String = NumberFormat.getInstance(Locale.US).format(this)

fun Double.clean() = round(this * 1_000_000.0) / 1_000_000.0

private val romanMap = mapOf('I' to 1, 'V' to 5, 'X' to 10, 'L' to 50, 'C' to 100, 'D' to 500, 'M' to 1000)
private val numberRegex = Regex("^[0-9]+$")
private val romanRegex = Regex("M{0,3}(CM|CD|D?C{0,3})(XC|XL|L?X{0,3})(IX|IV|V?I{0,3})")

fun romanToInt(s: String): Int? {
    if (s.matches(numberRegex)) return s.toIntOrNull()
    if (s.isEmpty() || !s.matches(romanRegex)) return null

    var result = 0
    for (i in 0 until s.length - 1) {
        val current = romanMap.getValue(s[i])
        val next = romanMap.getValue(s[i + 1])
        result += if (current < next) -current else current
    }
    return result + romanMap.getValue(s.last())
}

fun Number.lerp(a: Double, b: Double): Double {
    val t = this.toDouble().coerceIn(0.0, 1.0)
    return a + (b - a) * t
}

private val shortSuffixes = arrayOf("k", "m", "b", "t", "q")
private val shortMultipliers = mapOf('k' to 1e3, 'm' to 1e6, 'b' to 1e9, 't' to 1e12, 'q' to 1e15)

/**
 * 1234 -> 1.2k, 2_500_000 -> 2.5m
 */
fun Number.shortFormat(decimals: Int = 1): String {
    require(decimals >= 0) { "Decimals must be non-negative" }
    val value = toDouble()
    if (!value.isFinite()) return value.toString()

    val sign = if (value < 0) "-" else ""
    var abs = kotlin.math.abs(value)
    if (abs < 1000) return sign + Math.round(abs)

    var index = -1
    while (abs >= 1000 && index < shortSuffixes.lastIndex) {
        abs /= 1000
        index++
    }

    // 999.95k rounds to 1000k, so bump it to 1m
    val factor = 10.0.pow(decimals)
    var rounded = kotlin.math.floor(abs * factor + 0.5) / factor
    if (rounded >= 1000 && index < shortSuffixes.lastIndex) {
        rounded /= 1000
        index++
    }

    val text = "%.${decimals}f".format(Locale.US, rounded).let {
        if ('.' in it) it.trimEnd('0').trimEnd('.') else it
    }
    return "$sign$text${shortSuffixes[index]}"
}

/**
 * 1,234 / 1.5k / 2.3M / 1b -> Double
 */
fun String.parseShortNumberOrNull(): Double? {
    val text = trim().replace(",", "").replace("_", "")
    if (text.isEmpty()) return null
    val multiplier = shortMultipliers[text.last().lowercaseChar()]
    val number = if (multiplier != null) text.dropLast(1) else text
    if (number.isEmpty() || number.any { it.isLetter() }) return null
    return number.toDoubleOrNull()?.let { it * (multiplier ?: 1.0) }
}

private val romanSymbols = listOf(
    1000 to "M", 900 to "CM", 500 to "D", 400 to "CD", 100 to "C", 90 to "XC",
    50 to "L", 40 to "XL", 10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I",
)

fun Int.toRoman(): String {
    require(this in 1..3999) { "Roman numerals only support 1..3999, got $this" }
    var remaining = this
    return buildString {
        for ((value, symbol) in romanSymbols) {
            while (remaining >= value) {
                append(symbol)
                remaining -= value
            }
        }
    }
}

fun Number.ordinalSuffix(): String {
    val n = kotlin.math.abs(toLong())
    if (n % 100 in 11..13) return "th"
    return when (n % 10) {
        1L -> "st"
        2L -> "nd"
        3L -> "rd"
        else -> "th"
    }
}

fun Number.ordinal(): String = "$this${ordinalSuffix()}"

fun Number.withSign(): String = if (toDouble() > 0) "+$this" else toString()

/**
 * Clamped to 0..1, returns 1 if [max] is 0.
 */
fun Number.fractionOf(max: Number): Double {
    val m = max.toDouble()
    if (m == 0.0) return 1.0
    return (toDouble() / m).coerceIn(0.0, 1.0)
}

fun Number.remap(fromMin: Double, fromMax: Double, toMin: Double, toMax: Double): Double {
    if (fromMin == fromMax) return toMin
    return ((toDouble() - fromMin) / (fromMax - fromMin)).lerp(toMin, toMax)
}
