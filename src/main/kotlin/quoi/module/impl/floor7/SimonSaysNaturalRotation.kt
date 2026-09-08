package quoi.module.impl.floor7

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

internal class SimonSaysNaturalRotation(
    private val startYaw: Double,
    private val startPitch: Double,
    private val speed: Double,
    private val randomness: Double,
    private val tremorFrequency: Double,
    private val random: Random = Random.Default,
) {
    data class Step(val yaw: Double, val pitch: Double, val finished: Boolean)

    private var previousFrameNanos: Long? = null
    private var segmentNanos = 0L
    private var simulationMillis = 0L
    private var segmentStart = Step(startYaw, startPitch, false)
    private var segmentEnd: Step? = null

    fun sample(targetYaw: Double, targetPitch: Double, mouseSensitivity: Double, nowNanos: Long, fps: Int): Step {
        val previous = previousFrameNanos
        previousFrameNanos = nowNanos
        if (previous == null) {
            segmentEnd = step(segmentStart.yaw, segmentStart.pitch, targetYaw, targetPitch, mouseSensitivity, simulationMillis, fps)
            return segmentStart
        }
        if (segmentStart.finished) return segmentStart

        segmentNanos += (nowNanos - previous).coerceIn(0L, 50_000_000L)
        while (segmentNanos >= 50_000_000L) {
            segmentStart = requireNotNull(segmentEnd)
            segmentNanos -= 50_000_000L
            simulationMillis += 50L
            if (segmentStart.finished) return segmentStart
            segmentEnd = step(segmentStart.yaw, segmentStart.pitch, targetYaw, targetPitch, mouseSensitivity, simulationMillis, fps)
        }

        val end = requireNotNull(segmentEnd)
        val amount = segmentNanos / 50_000_000.0
        return Step(
            segmentStart.yaw + (end.yaw - segmentStart.yaw) * amount,
            segmentStart.pitch + (end.pitch - segmentStart.pitch) * amount,
            false,
        )
    }

    private fun step(yaw: Double, pitch: Double, targetYaw: Double, targetPitch: Double, mouseSensitivity: Double, nowMillis: Long, fps: Int): Step {
        val deltaYaw = wrap(targetYaw - yaw)
        val deltaPitch = targetPitch - pitch
        if (abs(deltaYaw) <= 1.0 && abs(deltaPitch) <= 1.0) {
            return quantize(yaw, pitch, yaw + deltaYaw, targetPitch, mouseSensitivity).copy(finished = true)
        }

        val distance = hypot(deltaYaw, deltaPitch)
        val maxYaw = speed / 2 * abs(deltaYaw / distance) * fader(abs(wrap(startYaw - targetYaw)))
        val maxPitch = speed / 2 * abs(deltaPitch / distance) * fader(abs(wrap(startPitch - targetPitch)))
        var newYaw = yaw + deltaYaw.coerceIn(-maxYaw, maxYaw) + noise() * fader(deltaYaw)
        var newPitch = pitch + deltaPitch.coerceIn(-maxPitch, maxPitch) + noise() * fader(deltaPitch)
        val tremorStrength = abs(sensitivity(mouseSensitivity)) * 0.2
        newYaw += tremorStrength * sin(tremorFrequency * nowMillis)
        newPitch += tremorStrength * cos(tremorFrequency * nowMillis)

        var iteration = 1
        while (iteration <= fps.coerceAtLeast(0) / 20.0 + random.nextDouble() * 10) {
            val adjusted = quantize(yaw, pitch, newYaw, newPitch, mouseSensitivity)
            newYaw = adjusted.yaw
            newPitch = adjusted.pitch
            iteration++
        }
        return Step(newYaw, newPitch.coerceIn(-90.0, 90.0), false)
    }

    private fun quantize(yaw: Double, pitch: Double, targetYaw: Double, targetPitch: Double, mouseSensitivity: Double): Step {
        val multiplier = sensitivity(mouseSensitivity).pow(3) * 8 * 0.15
        return Step(
            yaw + (((targetYaw - yaw) / multiplier).roundToInt() * multiplier),
            (pitch + (((targetPitch - pitch) / multiplier).roundToInt() * multiplier)).coerceIn(-90.0, 90.0),
            false,
        )
    }

    private fun noise() = (random.nextDouble() - 0.5) * randomness

    private fun fader(diff: Double) = 1 - exp(-abs(diff) * 0.02)

    private fun sensitivity(value: Double) = value * (1 + random.nextDouble() / 10_000_000) * 0.6 + 0.2

    private fun wrap(value: Double): Double {
        val wrapped = value % 360
        return when {
            wrapped >= 180 -> wrapped - 360
            wrapped < -180 -> wrapped + 360
            else -> wrapped
        }
    }
}
