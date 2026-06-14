package quoi.module.impl.dungeon.autocroesus

internal val defaultAutoCroesusWorthless = setOf(
    "DUNGEON_DISC_5", "DUNGEON_DISC_4", "DUNGEON_DISC_3", "DUNGEON_DISC_2", "DUNGEON_DISC_1",
    "MAXOR_THE_FISH", "STORM_THE_FISH", "GOLDOR_THE_FISH",
    "ENCHANTMENT_ULTIMATE_NO_PAIN_NO_GAIN_1", "ENCHANTMENT_ULTIMATE_NO_PAIN_NO_GAIN_2",
    "ENCHANTMENT_ULTIMATE_NO_PAIN_NO_GAIN_3", "ENCHANTMENT_ULTIMATE_NO_PAIN_NO_GAIN_4",
    "ENCHANTMENT_ULTIMATE_NO_PAIN_NO_GAIN_5", "ENCHANTMENT_ULTIMATE_COMBO_1",
    "ENCHANTMENT_ULTIMATE_COMBO_2", "ENCHANTMENT_ULTIMATE_COMBO_3", "ENCHANTMENT_ULTIMATE_COMBO_4",
    "ENCHANTMENT_ULTIMATE_COMBO_5", "ENCHANTMENT_ULTIMATE_BANK_1", "ENCHANTMENT_ULTIMATE_BANK_2",
    "ENCHANTMENT_ULTIMATE_BANK_3", "ENCHANTMENT_ULTIMATE_BANK_4", "ENCHANTMENT_ULTIMATE_BANK_5",
    "ENCHANTMENT_ULTIMATE_JERRY_1", "ENCHANTMENT_ULTIMATE_JERRY_2", "ENCHANTMENT_ULTIMATE_JERRY_3",
    "ENCHANTMENT_ULTIMATE_JERRY_4", "ENCHANTMENT_ULTIMATE_JERRY_5", "ENCHANTMENT_FEATHER_FALLING_6",
    "ENCHANTMENT_FEATHER_FALLING_7", "ENCHANTMENT_FEATHER_FALLING_8", "ENCHANTMENT_FEATHER_FALLING_9",
    "ENCHANTMENT_FEATHER_FALLING_10", "ENCHANTMENT_INFINITE_QUIVER_6", "ENCHANTMENT_INFINITE_QUIVER_7",
    "ENCHANTMENT_INFINITE_QUIVER_8", "ENCHANTMENT_INFINITE_QUIVER_9", "ENCHANTMENT_INFINITE_QUIVER_10"
)

internal val defaultAutoCroesusAlwaysBuy = setOf(
    "NECRON_HANDLE", "DARK_CLAYMORE", "FIRST_MASTER_STAR", "SECOND_MASTER_STAR", "THIRD_MASTER_STAR",
    "FOURTH_MASTER_STAR", "FIFTH_MASTER_STAR", "SHADOW_FURY", "SHADOW_WARP_SCROLL", "IMPLOSION_SCROLL",
    "WITHER_SHIELD_SCROLL", "DYE_LIVID"
)

internal val autoCroesusFloors = listOf("F1", "F2", "F3", "F4", "F5", "F6", "F7", "M1", "M2", "M3", "M4", "M5", "M6", "M7")

internal val autoCroesusChestNames = setOf("Wood", "Gold", "Diamond", "Emerald", "Obsidian", "Bedrock")

internal val autoCroesusChestSlots = listOf(
    10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25,
    28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43
)

internal data class ClaimingChestInfo(
    val floor: String,
    val page: Int,
    val runSlot: Int,
    var chestSlot: Int = -1,
    var skipKismet: Boolean = false,
)

internal data class ChestData(
    val name: String,
    val slot: Int,
    val items: MutableList<ChestItem> = mutableListOf(),
    var cost: Int = 0,
    var value: Int = 0,
    var profit: Int = 0,
    var purchased: Boolean = false,
)

internal data class ChestItem(val id: String, val displayName: String, val amount: Int, val value: Int)

internal data class ChestParseResult(val chests: List<ChestData> = emptyList(), val error: String? = null)

internal sealed interface ParsedReward {
    data class Item(val item: ChestItem) : ParsedReward
    data class Error(val reason: String) : ParsedReward
}

internal data class LoggedRun(
    val floor: String,
    val score: Int,
    val chestCost: Int,
    val items: Map<String, Int>,
    val time: Long = System.currentTimeMillis(),
)

internal data class LootFilters(
    val score: Int = 300,
    val floor: String? = null,
    val limit: Int? = null,
)

internal data class LootSummaryItem(val id: String, val amount: Int, val unitValue: Double) {
    val totalValue: Double get() = amount * unitValue
}

internal val previewChestData = listOf(
    ChestData(
        name = "Bedrock",
        slot = 13,
        items = mutableListOf(
            ChestItem("NECRON_HANDLE", "Necron's Handle", 1, 812_500_000),
            ChestItem("ESSENCE_WITHER", "Wither Essence x250", 250, 3_200),
        ),
        cost = 35_000_000,
        value = 813_300_000,
        profit = 778_300_000,
    ),
    ChestData(
        name = "Obsidian",
        slot = 12,
        items = mutableListOf(
            ChestItem("WITHER_SHIELD_SCROLL", "Wither Shield", 1, 268_000_000),
        ),
        cost = 25_000_000,
        value = 268_000_000,
        profit = 243_000_000,
    ),
)
