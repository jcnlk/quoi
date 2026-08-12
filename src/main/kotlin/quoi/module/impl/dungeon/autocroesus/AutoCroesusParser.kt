package quoi.module.impl.dungeon.autocroesus

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import quoi.api.skyblock.SkyblockPrices
import quoi.api.skyblock.SkyblockPrices.BazaarPriceType
import quoi.utils.StringUtils.noControlCodes
import kotlin.math.roundToInt

internal class AutoCroesusChestParser(
    private val worthless: Collection<String>,
    private val bazaarPriceType: () -> BazaarPriceType,
) {
    private val chestNameRegex = Regex("^(Wood|Gold|Diamond|Emerald|Obsidian|Bedrock)$")
    private val enchantedBookRegex = Regex("^Enchanted Book \\(([\\w ]+) ([IVX\\d]+)\\)$")
    private val formattedBookRegex = Regex("^(?:§.)*Enchanted Book \\((§d§l)?([\\w ]+) ([IVX\\d]+)(?:§.)*\\)$")
    private val essenceRegex = Regex("^(Wither|Undead) Essence x(\\d+)$")
    private val formattedEssenceRegex = Regex("^§5§o§d(\\w+) Essence §8x(\\d+)$")
    private val petRegex = Regex("^\\[Lvl 1] ([\\w ]+)$")
    private val formattedPetRegex = Regex("^(?:§.)*\\[Lvl 1] §([0-9a-f])([\\w ]+)$")
    private val costRegex = Regex("^(\\d[\\d,]+) Coins$")
    private val itemReplacements = mapOf(
        "Shiny Wither Boots" to "WITHER_BOOTS",
        "Shiny Wither Leggings" to "WITHER_LEGGINGS",
        "Shiny Wither Chestplate" to "WITHER_CHESTPLATE",
        "Shiny Wither Helmet" to "WITHER_HELMET",
        "Shiny Necron's Handle" to "NECRON_HANDLE",
        "Bonzo Shard" to "SHARD_BONZO",
        "Wither Shard" to "SHARD_WITHER",
        "Thorn Shard" to "SHARD_THORN",
        "Apex Dragon Shard" to "SHARD_APEX_DRAGON",
        "Power Dragon Shard" to "SHARD_POWER_DRAGON",
        "Scarf Shard" to "SHARD_SCARF",
        "Necron Dye" to "DYE_NECRON",
        "Livid Dye" to "DYE_LIVID",
        "Necromancer's Brooch" to "NECROMANCER_BROOCH",
        "Wither Shield" to "WITHER_SHIELD_SCROLL",
        "Implosion" to "IMPLOSION_SCROLL",
        "Shadow Warp" to "SHADOW_WARP_SCROLL",
        "Warped Stone" to "AOTE_STONE",
        "Spirit Stone" to "SPIRIT_DECOY",
    )

    fun parseChestData(container: AbstractContainerScreen<*>): ChestParseResult {
        val result = mutableListOf<ChestData>()
        for (idx in 0..27) {
            val stack = container.menu.slots.getOrNull(idx)?.item ?: continue
            val name = stack.hoverName.string.noControlCodes
            if (!chestNameRegex.matches(name)) continue

            val formattedLore = stack.formattedLore()
            val lore = stack.cleanLore()
            if (lore.isEmpty()) continue
            val chest = ChestData(name, idx)
            var inContents = false
            for ((lineIndex, line) in lore.withIndex()) {
                when {
                    line == "Already opened!" -> {
                        chest.purchased = true
                        break
                    }
                    line == "Contents" -> {
                        inContents = true
                        continue
                    }
                    line == "Cost" -> {
                        inContents = false
                        continue
                    }
                    inContents && line.isNotBlank() -> {
                        when (val parsed = parseReward(line, formattedLore.getOrNull(lineIndex) ?: line)) {
                            is ParsedReward.Item -> chest.items.add(parsed.item)
                            is ParsedReward.Error -> return ChestParseResult(error = "${parsed.reason} in $name Chest")
                        }
                    }
                    line == "FREE" -> chest.cost = 0
                    costRegex.matches(line) -> chest.cost = costRegex.matchEntire(line)!!.groupValues[1].replace(",", "").toInt()
                    line == "Dungeon Chest Key" -> {
                        val keyPrice = SkyblockPrices.buyPrice("DUNGEON_CHEST_KEY", bazaarPriceType())
                            ?: return ChestParseResult(error = "Could not find price for Dungeon Chest Key")
                        chest.cost += keyPrice.roundToInt()
                    }
                }
            }
            chest.value = chest.items.sumOf { it.value * it.amount }
            chest.profit = chest.value - chest.cost
            if (!chest.purchased) result.add(chest)
        }
        return ChestParseResult(result)
    }

    private fun parseReward(line: String, formattedLine: String): ParsedReward {
        val idAndAmount = when {
            formattedBookRegex.matches(formattedLine) -> {
                val match = formattedBookRegex.matchEntire(formattedLine)!!.groupValues
                val cleanName = match[2].uppercase().replace(" ", "_")
                val tier = parseRoman(match[3]) ?: match[3].toIntOrNull()
                    ?: return ParsedReward.Error("Could not parse book tier \"$line\"")
                val id = "ENCHANTMENT_${if (match[1].isNotEmpty()) "ULTIMATE_" else ""}${cleanName}_$tier"
                    .replace("ULTIMATE_ULTIMATE_", "ULTIMATE_")
                id to 1
            }
            enchantedBookRegex.matches(line) -> {
                val match = enchantedBookRegex.matchEntire(line)!!.groupValues
                val cleanName = match[1].uppercase().replace(" ", "_")
                val tier = parseRoman(match[2]) ?: match[2].toIntOrNull()
                    ?: return ParsedReward.Error("Could not parse book tier \"$line\"")
                val normal = "ENCHANTMENT_${cleanName}_$tier"
                val ultimate = "ENCHANTMENT_ULTIMATE_${cleanName}_$tier".replace("ULTIMATE_ULTIMATE_", "ULTIMATE_")
                val id = if (SkyblockPrices.buyPrice(normal, bazaarPriceType()) != null) normal else ultimate
                id to 1
            }
            formattedEssenceRegex.matches(formattedLine) -> {
                val match = formattedEssenceRegex.matchEntire(formattedLine)!!.groupValues
                "ESSENCE_${match[1].uppercase()}" to match[2].toInt()
            }
            essenceRegex.matches(line) -> {
                val match = essenceRegex.matchEntire(line)!!.groupValues
                "ESSENCE_${match[1].uppercase()}" to match[2].toInt()
            }
            formattedPetRegex.matches(formattedLine) -> {
                val match = formattedPetRegex.matchEntire(formattedLine)!!.groupValues
                val tier = if (match[1] == "6") 4 else 3
                "${match[2].uppercase().replace(" ", "_")};$tier" to 1
            }
            petRegex.matches(line) -> {
                val match = petRegex.matchEntire(line)!!.groupValues
                "${match[1].uppercase().replace(" ", "_")};3" to 1
            }
            else -> {
                val itemName = line.trim()
                (itemReplacements[itemName] ?: SkyblockPrices.itemIdByName(itemName)
                    ?: return ParsedReward.Error("Could not find item id for \"$line\"")) to 1
            }
        }

        val (id, amount) = idAndAmount
        val price = if (worthless.containsId(id)) 0.0 else SkyblockPrices.buyPrice(id, bazaarPriceType())
            ?: return ParsedReward.Error("Could not find price for \"$line\" ($id)")
        return ParsedReward.Item(ChestItem(id, line, amount, price.roundToInt()))
    }
}
