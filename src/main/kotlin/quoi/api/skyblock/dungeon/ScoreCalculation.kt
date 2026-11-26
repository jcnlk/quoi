package quoi.api.skyblock.dungeon

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import quoi.QuoiMod.logger
import quoi.QuoiMod.scope
import quoi.api.skyblock.dungeon.enums.Floor
import quoi.api.skyblock.dungeon.enums.PuzzleStatus
import quoi.api.skyblock.dungeon.odonscanning.ScanUtils
import quoi.module.impl.dungeon.ScoreCalculator
import quoi.utils.WebUtils
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * modified NoammAddons (CC0-1.0)
 * original: https://github.com/Noamm9/NoammAddons/blob/1bca286c77939638c13029b1b6d7f387cf92b3f4/src/main/kotlin/com/github/noamm9/utils/dungeons/map/handlers/ScoreCalculation.kt
 */
object ScoreCalculation {
    data class Score(val secrets: Int, val rooms: Int, val skill: Int, val speed: Int, val bonus: Int) {
        val total get() = secrets + rooms + skill + speed + bonus
    }

    @Volatile
    private var paul = false
    private var lastElectionCheck = 0L

    val isPaul get() = paul || ScoreCalculator.forcePaul

    fun updateMayor() {
        val now = System.currentTimeMillis()
        if (now - lastElectionCheck < 5 * 60_000L) return
        lastElectionCheck = now
        scope.launch(Dispatchers.IO) {
            runCatching {
                WebUtils.setupConnection("https://api.hypixel.net/v2/resources/skyblock/election", useCaches = false)
                    .bufferedReader().use { reader ->
                        val election = JsonParser.parseReader(reader).asJsonObject
                        check(election["success"]?.asBoolean == true)
                        paul = hasPaulBonus(election)
                    }
            }.onFailure { logger.warn("Could not update dungeon mayor bonus", it) }
        }
    }

    private fun hasPaulBonus(election: JsonObject): Boolean {
        val mayor = election.getAsJsonObject("mayor")
        val minister = mayor?.get("minister")?.takeIf { it.isJsonObject }?.asJsonObject
        return mayor?.getAsJsonArray("perks")?.any { it.asJsonObject["name"]?.asString == "EZPZ" } == true ||
            minister?.getAsJsonObject("perk")?.get("name")?.asString == "EZPZ"
    }

    val totalSecrets: Int
        get() {
            val stats = Dungeon.dungeonStats
            if (stats.secretsFound > 0 && stats.secretsPercent > 0f) {
                return (stats.secretsFound * 100.0 / stats.secretsPercent).roundToInt()
                    .coerceAtLeast(stats.secretsFound)
            }
            return ScanUtils.scannedRooms.sumOf { it.data.secrets }.coerceAtLeast(stats.secretsFound)
        }

    val bonusScore: Int
        get() {
            var bonus = Dungeon.cryptCount.coerceIn(0, 5)
            if (Dungeon.mimicKilled && (Dungeon.floor?.floorNumber ?: 0) >= 6) bonus += 2
            if (Dungeon.princeKilled) bonus++
            bonus += Dungeon.batCount
            if (isPaul) bonus += 10
            return bonus
        }

    val score: Score
        get() {
            val dungeonFloor = Dungeon.floor
            if (!Dungeon.hasStarted || dungeonFloor == null) return Score(0, 0, 0, 0, 0)

            val stats = Dungeon.dungeonStats
            val completed = stats.completedRooms + (if (stats.bloodDone) 0 else 1) + (if (Dungeon.inBoss) 0 else 1)
            val total = Dungeon.totalRooms
            val ratio = if (total > 0) (completed.toDouble() / total).coerceIn(0.0, 1.0) else 0.0
            val secrets = floor(stats.secretsPercent / dungeonFloor.secretPercentage * 0.4).toInt().coerceIn(0, 40)
            val puzzlePenalty = (maxOf(
                stats.puzzleCount,
                Dungeon.puzzles.size
            ) - Dungeon.puzzles.count { it.status == PuzzleStatus.Completed }) * 10
            val deathPenalty = (stats.deaths * 2 - 1).coerceAtLeast(0)
            val skill = (20 + floor(ratio * 80).toInt() - puzzlePenalty - deathPenalty).coerceIn(20, 100)

            return Score(
                secrets,
                floor(ratio * 60).toInt(),
                skill,
                speedScore(dungeonFloor, stats.secondsElapsed),
                bonusScore
            )
        }

    val secretsUntil300: Int
        get() {
            val current = score
            if (current.total >= 300) return 0
            val total = totalSecrets
            val dungeonFloor = Dungeon.floor ?: return total
            if (total == 0 || current.total - current.secrets + 40 < 300) return total
            val requiredSecrets = ceil(total * dungeonFloor.secretPercentage).toInt()
            val requiredPoints = 300 - (current.total - current.secrets)
            return (ceil(requiredPoints / 40.0 * requiredSecrets).toInt() - Dungeon.secretCount).coerceAtLeast(0)
        }

    private fun speedScore(dungeonFloor: Floor, seconds: Int): Int {
        val limit = when (dungeonFloor) {
            Floor.E, Floor.F1, Floor.F2, Floor.F3, Floor.F5, Floor.M6 -> 600
            Floor.F4, Floor.F6 -> 720
            Floor.F7, Floor.M7 -> 840
            Floor.M1, Floor.M2, Floor.M3, Floor.M4, Floor.M5 -> 480
        }
        if (seconds <= limit) return 100
        var percentage = (seconds - limit) * 100.0 / limit
        var deduction = 0.0
        for ((cap, divisor) in listOf(20 to 2.0, 20 to 3.5, 10 to 4.0, 10 to 5.0)) {
            val part = percentage.coerceIn(0.0, cap.toDouble())
            deduction += part / divisor
            percentage -= part
        }
        deduction += percentage / 6.0
        return (100 - deduction).toInt().coerceAtLeast(0)
    }

    fun colour(score: Int): String = when {
        score >= 300 -> "§a"
        score >= 270 -> "§e"
        else -> "§c"
    }
}
