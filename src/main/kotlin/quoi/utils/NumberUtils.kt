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
