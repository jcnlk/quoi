package quoi.api.skyblock.dungeon.terminals

/**
 * modified NoammAddons (CC0-1.0)
 * original: https://github.com/Noamm9/NoammAddons/blob/5dff2334228c6cc78ebace0167052395ee192687/src/main/kotlin/com/github/noamm9/features/impl/floor7/terminals/TerminalListener.kt
 *
 * Metadata of one terminal window
 */
class Terminal internal constructor(val type: TerminalType, val title: String, val windowId: Int) {
    val displayName: String get() = type.displayName
    val gridSize: Pair<Int, Int> get() = type.gridSize

    /**
     * Observed server tick when the initial items finished loading. Zero before loading and after reset
     */
    var initialOpenTick = 0
        private set

    /**
     * Time the initial items finished loading, in milliseconds since the epoch. Zero before loading and after reset
     */
    var initialOpenTime = 0L
        private set

    var fullyOpened = false
        private set

    internal fun loaded(tick: Int, now: Long) {
        initialOpenTick = tick
        initialOpenTime = now
        fullyOpened = true
    }

    internal fun clear() {
        fullyOpened = false
        initialOpenTick = 0
        initialOpenTime = 0
    }
}
