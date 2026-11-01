package quoi.utils

/**
 * modified NoammAddons (CC0-1.0)
 * original: https://github.com/Noamm9/NoammAddons/blob/5e1785f533516fd63a777bf52c992296db1af1ef/src/main/kotlin/com/github/noamm9/utils/dungeons/DungeonListener.kt#L46-L50
 * Captures observed ticks and real time in milliseconds.
 */
data class DualTime(val ticks: Long, val real: Long = System.currentTimeMillis()) {
    operator fun minus(other: DualTime) = DualTime(ticks - other.ticks, real - other.real)
}
