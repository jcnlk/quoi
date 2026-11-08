package quoi.api.skyblock.dungeon.terminals

import quoi.utils.StringUtils.noControlCodes

/**
 * modified NoammAddons (CC0-1.0)
 * original: https://github.com/Noamm9/NoammAddons/tree/3f8775e2038c028ebc3ad41d0aeb79c6fc1f1009/src/main/kotlin/com/github/noamm9/features/impl/floor7/terminals/impl
 *
 * Terminal types identified by their menu titles
 *
 * @property displayName readable name of the terminal type
 * @property title fixed title or title prefix for puzzles with a letter or color in the title
 * @property slotCount number of terminal slots, excluding the player inventory
 * @property gridSize width and height of the puzzle grid, excluding the menu border
 */
enum class TerminalType(val displayName: String, val title: String, val slotCount: Int, val gridSize: Pair<Int, Int>, val titleRegex: Regex = Regex(Regex.escape(title))) {
    RED_GREEN("Red Green", "Correct all the panes!", 45, 5 to 3),
    RUBIX("Rubix", "Change all to same color!", 45, 3 to 3),
    NUMBERS("Numbers", "Click in order!", 36, 5 to 2),
    STARTS_WITH("Starts With", "What starts with:", 45, 7 to 3, Regex("What starts with: '(\\w)'\\?")),
    COLORS("Colors", "Select all the", 54, 7 to 4, Regex("Select all the ([\\w ]+) items!")),
    MELODY("Melody", "Click the button on time!", 54, 6 to 3);

    companion object {
        /**
         * Finds the terminal type for [title], ignoring formatting codes
         */
        fun fromTitle(title: String): TerminalType? = entries.firstOrNull { it.titleRegex.matches(title.noControlCodes) }
    }
}
