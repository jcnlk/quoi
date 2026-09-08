package quoi.module.impl.floor7

import kotlin.math.sqrt

internal object SimonSaysRotationTiming {
    fun duration(baseTime: Int, minimumTime: Int, angle: Float, scale: Boolean): Int {
        if (!scale) return baseTime
        val factor = (0.8f + 0.2f * sqrt(angle.coerceAtLeast(0f) / 30f)).coerceAtMost(1.75f)
        return (baseTime * factor).toInt().coerceAtLeast(minimumTime)
    }
}
