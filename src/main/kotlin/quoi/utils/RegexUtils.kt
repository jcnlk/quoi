package quoi.utils

object RegexUtils {
    inline fun <T> Regex.ifMatches(text: CharSequence, block: MatchResult.() -> T): T? =
        matchEntire(text)?.let(block)

    inline fun <T> Regex.ifFound(text: CharSequence, block: MatchResult.() -> T): T? =
        find(text)?.let(block)

    inline fun <T> Regex.firstMatch(lines: Iterable<CharSequence>, block: MatchResult.() -> T): T? {
        for (line in lines) {
            val match = find(line) ?: continue
            return match.block()
        }
        return null
    }

    inline fun Regex.forEachMatch(lines: Iterable<CharSequence>, block: MatchResult.() -> Unit) {
        for (line in lines) find(line)?.block()
    }

    fun Regex.findGroup(text: CharSequence, index: Int = 1): String? =
        find(text)?.groups?.takeIf { index in it.indices }?.get(index)?.value

    fun Regex.findGroup(text: CharSequence, group: String): String? =
        find(text)?.groupOrNull(group)

    fun Regex.firstGroup(lines: Iterable<CharSequence>, index: Int = 1): String? =
        lines.firstNotNullOfOrNull { findGroup(it, index) }

    fun Regex.firstGroup(lines: Iterable<CharSequence>, group: String): String? =
        lines.firstNotNullOfOrNull { findGroup(it, group) }

    fun Regex.anyMatch(lines: Iterable<CharSequence>): Boolean = lines.any { containsMatchIn(it) }

    fun Iterable<Regex>.anyMatches(text: CharSequence): Boolean = any { it.matches(text) }

    /**
     * Also `null` if the group doesn't exist.
     */
    fun MatchResult.groupOrNull(name: String): String? =
        runCatching { groups[name]?.value }.getOrNull()

    fun MatchResult.groupOrEmpty(name: String): String = groupOrNull(name).orEmpty()
}
