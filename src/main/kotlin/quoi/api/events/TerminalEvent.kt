package quoi.api.events

import quoi.api.events.core.Event
import quoi.api.skyblock.dungeon.terminals.Terminal
import quoi.api.skyblock.dungeon.terminals.TerminalUtils

/**
 * modified NoammAddons (CC0-1.0)
 * original: https://github.com/Noamm9/NoammAddons/blob/5dff2334228c6cc78ebace0167052395ee192687/src/main/kotlin/com/github/noamm9/event/impl/TerminalEvent.kt
 *
 * Terminal events posted on the client thread after their associated state is updated
 */
sealed class TerminalEvent(val terminal: Terminal) : Event() {
    /**
     * The window opened and its type, title and ID are available
     */
    class Open(terminal: Terminal) : TerminalEvent(terminal) {
        /**
         * The window finished its initial loading and the opening time and tick are available.
         * Posted once per window
         */
        class Full(terminal: Terminal) : TerminalEvent(terminal)
    }

    /**
     * The terminal's opening timestamps are cleared and [TerminalUtils.current] no longer refers to it.
     * Server closes reset the state one client tick after their packet arrives
     */
    class Close(terminal: Terminal) : TerminalEvent(terminal)
}
