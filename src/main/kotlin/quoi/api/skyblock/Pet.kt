package quoi.api.skyblock

data class Pet(
    val name: String,
    val level: Int? = null,
    val rarity: PetRarity = PetRarity.UNKNOWN,
    val uuid: String? = null,
    val heldItem: String? = null,
) {
    val maxLevel: Int
        get() = if (normalizedName == "golden dragon" || normalizedName == "jade dragon") 200 else 100

    val normalizedName: String
        get() = normalizeName(name)

    fun matches(name: String, rarity: PetRarity = PetRarity.UNKNOWN): Boolean =
        normalizedName == normalizeName(name) &&
            (rarity == PetRarity.UNKNOWN || this.rarity == rarity)

    companion object {
        private val formattingPattern = Regex("§[0-9A-FK-OR]", RegexOption.IGNORE_CASE)
        private val levelPattern = Regex("""^\[Lvl\s+\d+]\s*""")

        fun cleanName(name: String): String = name
            .replace(formattingPattern, "")
            .trim()
            .removePrefix("⭐")
            .trim()
            .replace(levelPattern, "")
            .removeSuffix("✦")
            .trim()

        fun normalizeName(name: String): String = cleanName(name).lowercase()
    }
}

enum class PetRarity(val colorCode: String) {
    COMMON("§f"),
    UNCOMMON("§a"),
    RARE("§9"),
    EPIC("§5"),
    LEGENDARY("§6"),
    MYTHIC("§d"),
    UNKNOWN("");

    companion object {
        fun fromName(name: String?): PetRarity = entries.firstOrNull {
            it != UNKNOWN && it.name.equals(name, ignoreCase = true)
        } ?: UNKNOWN
    }
}
