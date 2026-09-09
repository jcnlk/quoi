package quoi.utils.skyblock.player

import quoi.api.animations.Animation
import quoi.utils.skyblock.player.RotationAnimation.Step
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Interpolates yaw and pitch for camera rotations
 *
 * @param startYaw Starting yaw in degrees
 * @param startPitch Starting pitch in degrees
 * @param durationMillis Base duration in milliseconds. Actual duration depends on the angle and mode.
 * @param mode Type of rotation
 * @param random Random source for natural movement
 */
class RotationAnimation(
    private val startYaw: Double,
    private val startPitch: Double,
    private val durationMillis: Int = 180,
    private val mode: Mode = Mode.Natural,
    random: Random = Random.Default,
) {
    enum class Mode { None, Natural, Curve }
    data class Step(val yaw: Double, val pitch: Double, val finished: Boolean)

    init {
        require(durationMillis > 0) { "Rotation duration must be positive" }
    }

    private val natural = if (mode == Mode.Natural) {
        NaturalRotation(startYaw, startPitch, 40.0 * 180 / durationMillis, 10.0, 40.0, random)
    } else null
    private var startedAt: Long? = null
    private var turnDurationMillis = durationMillis.toDouble()

    /**
     * Returns the current rotation and whether the turn has finished
     *
     * @param targetYaw Target yaw in degrees
     * @param targetPitch Target pitch in degrees
     * @param mouseSensitivity Minecraft mouse sensitivity, used for natural movement
     * @param nowMillis Monotonic time in milliseconds
     */
    fun sample(
        targetYaw: Double,
        targetPitch: Double,
        mouseSensitivity: Double,
        nowMillis: Long = System.nanoTime() / 1_000_000,
    ): Step {
        if (mode == Mode.None) return Step(startYaw, startPitch, true)
        natural?.let { return it.sample(targetYaw, targetPitch, mouseSensitivity, nowMillis) }

        val deltaYaw = wrap(targetYaw - startYaw)
        val deltaPitch = targetPitch - startPitch
        val start = startedAt ?: nowMillis.also {
            startedAt = it
            val factor = (0.8 + 0.2 * sqrt(hypot(deltaYaw, deltaPitch) / 30)).coerceAtMost(1.75)
            turnDurationMillis = durationMillis * factor
        }
        val progress = ((nowMillis - start) / turnDurationMillis).coerceIn(0.0, 1.0)
        val amount = Animation.Style.SmootherStep.getValue(progress.toFloat())
        return Step(startYaw + deltaYaw * amount, (startPitch + deltaPitch * amount).coerceIn(-90.0, 90.0), progress >= 1.0)
    }
}

private class NaturalRotation(
    private val startYaw: Double,
    private val startPitch: Double,
    private val speed: Double,
    private val randomness: Double,
    private val tremorFrequency: Double,
    private val random: Random = Random.Default,
) {
    private var previousFrameMillis: Long? = null
    private var segmentMillis = 0L
    private var simulationMillis = 0L
    private var segmentStart = Step(startYaw, startPitch, false)
    private var segmentEnd = segmentStart

    fun sample(targetYaw: Double, targetPitch: Double, mouseSensitivity: Double, nowMillis: Long): Step {
        val previous = previousFrameMillis
        previousFrameMillis = nowMillis
        if (previous == null) {
            segmentEnd = step(segmentStart.yaw, segmentStart.pitch, targetYaw, targetPitch, mouseSensitivity, simulationMillis)
            return segmentStart
        }
        if (segmentStart.finished) return segmentStart

        segmentMillis += (nowMillis - previous).coerceIn(0L, 250L)
        while (segmentMillis >= 50L) {
            segmentStart = segmentEnd
            segmentMillis -= 50L
            simulationMillis += 50L
            if (segmentStart.finished) return segmentStart
            segmentEnd = step(segmentStart.yaw, segmentStart.pitch, targetYaw, targetPitch, mouseSensitivity, simulationMillis)
        }

        val end = segmentEnd
        val amount = segmentMillis / 50.0
        return Step(
            segmentStart.yaw + (end.yaw - segmentStart.yaw) * amount,
            segmentStart.pitch + (end.pitch - segmentStart.pitch) * amount,
            false,
        )
    }

    private fun step(yaw: Double, pitch: Double, targetYaw: Double, targetPitch: Double, mouseSensitivity: Double, nowMillis: Long): Step {
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
        if (tremorFrequency > 0) {
            val tremorStrength = sensitivity(mouseSensitivity) * 0.2
            val phase = 2 * PI * tremorFrequency * nowMillis / 1000.0
            newYaw += tremorStrength * sin(phase)
            newPitch += tremorStrength * cos(phase)
        }
        return quantize(yaw, pitch, newYaw, newPitch, mouseSensitivity)
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

    private fun sensitivity(value: Double) = value * 0.6 + 0.2
}

private fun wrap(value: Double): Double {
    val wrapped = value % 360
    return when {
        wrapped >= 180 -> wrapped - 360
        wrapped < -180 -> wrapped + 360
        else -> wrapped
    }
}
