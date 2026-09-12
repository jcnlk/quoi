package quoi.api.customtriggers

/**
 * Text matching settings shared by triggers and conditions.
 * Literal comparisons ignore case; regex comparisons use the flags in [pattern].
 */
data class TextMatcher(var pattern: String = "", var mode: TextMatchMode = TextMatchMode.INCLUDES) {
    @Transient private var cachedPattern: String? = null
    @Transient private var cachedRegex: Regex? = null

    private fun regex(): Regex? {
        if (cachedPattern != pattern) {
            cachedPattern = pattern
            cachedRegex = runCatching { Regex(pattern) }.getOrNull()
        }
        return cachedRegex
    }

    fun validationError(): String? = when {
        pattern.isBlank() -> "Enter text to match."
        mode == TextMatchMode.REGEX && regex() == null -> "Invalid regular expression."
        else -> null
    }

    fun matches(value: String): Boolean = validationError() == null && when (mode) {
        TextMatchMode.EQUALS -> value.equals(pattern, ignoreCase = true)
        TextMatchMode.INCLUDES -> value.contains(pattern, ignoreCase = true)
        TextMatchMode.REGEX -> regex()!!.containsMatchIn(value)
    }

    /**
     * Gets numbered and named regex captures keyed as `%0%`, `%1%`, `%name%`, etc.
     *
     * @return `null` if the text does not match, or an empty map for a literal match
     */
    fun captures(value: String): Map<String, String>? {
        if (validationError() != null) return null
        if (mode != TextMatchMode.REGEX) return if (matches(value)) emptyMap() else null
        val match = regex()!!.find(value) ?: return null
        return buildMap {
            match.groups.forEachIndexed { index, group -> group?.let { put("%$index%", it.value) } }
            regex()!!.toPattern().namedGroups().keys.forEach { name ->
                match.groups[name]?.let { put("%$name%", it.value) }
            }
        }
    }
}
