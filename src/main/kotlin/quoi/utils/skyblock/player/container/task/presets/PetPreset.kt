package quoi.utils.skyblock.player.container.task.presets

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.minecraft.world.item.ItemStack
import quoi.api.skyblock.Pet
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.skyblock.item.ItemUtils.cleanName
import quoi.utils.skyblock.item.ItemUtils.extraAttributes
import quoi.utils.skyblock.item.ItemUtils.getCleanLore
import quoi.utils.skyblock.item.ItemUtils.normalizeItemName
import quoi.utils.skyblock.player.container.task.ContainerTaskBuilder
import quoi.utils.skyblock.player.container.task.item

private val petMenu = Regex("""^(?:\(\d+/\d+\) )?Pets(?: \(\d+/\d+\))?$""", RegexOption.IGNORE_CASE)

/**
 * Equips a pet by name, skipping an already equipped pet
 * @param heldItem optional held item name to distinguish matching pets
 */
fun ContainerTaskBuilder.equipPet(name: String, heldItem: String? = null) {
    val cleanedName = Pet.cleanName(name)
    val cleanedItem = heldItem?.noControlCodes?.trim()?.takeIf(String::isNotEmpty)
    val label = cleanedItem?.let { "$cleanedName ($it)" } ?: cleanedName
    var state = PetState.UNAVAILABLE

    check("Pet name must not be empty") { cleanedName.isNotEmpty() }
    openContainer("petsmenu", petMenu)
    pickup(
        item { it.matchesPet(cleanedName, cleanedItem) }.menu,
        failureMessage = "Couldn't find $label",
    ).unless { stack ->
        state = PetState.fromLore(stack.getCleanLore())
        state != PetState.SUMMONABLE
    }
    check("$label cannot be summoned") { state != PetState.UNAVAILABLE }
    closeContainer()
}

private fun ItemStack.matchesPet(name: String, heldItem: String?): Boolean {
    if (!Pet.normalizeName(cleanName).contains(Pet.normalizeName(name))) return false
    if (heldItem == null) return true

    val rawPetInfo = extraAttributes?.getString("petInfo")?.orElse(null)
    val itemId = rawPetInfo?.let {
        runCatching { Json.parseToJsonElement(it).jsonObject["heldItem"]?.jsonPrimitive?.contentOrNull }.getOrNull()
    }
    if (itemId?.contains(heldItem, ignoreCase = true) == true) return true

    return getCleanLore().firstOrNull { it.startsWith("Held Item:", ignoreCase = true) }
        ?.substringAfter(':')?.normalizeItemName()?.contains(heldItem.normalizeItemName()) == true
}

private enum class PetState {
    SUMMONABLE, EQUIPPED, UNAVAILABLE;

    companion object {
        fun fromLore(lore: List<String>): PetState = when {
            lore.any { it.contains("Click to despawn!") } -> EQUIPPED
            lore.any { it.contains("Left-click to summon!") } -> SUMMONABLE
            else -> UNAVAILABLE
        }
    }
}
