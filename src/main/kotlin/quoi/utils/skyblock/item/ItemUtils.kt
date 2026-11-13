package quoi.utils.skyblock.item

import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import quoi.mixins.accessors.CustomDataAccessor
import quoi.utils.StringUtils.noControlCodes

object ItemUtils {
    private val stackSizeRegex = Regex("^(?:[1-9]|[1-5]\\d|6[0-4])(?:\\s*[xX×])?\\s+|\\s+(?:[xX×]\\s*(?:[1-9]|[1-5]\\d|6[0-4])|(?:[1-9]|[1-5]\\d|6[0-4])\\s*[xX×])$")
    private val whitespaceRegex = Regex("\\s+")
    private val upgradeRegex = Regex("[✪➊➋➌➍➎⚚◆]")

    inline val ItemStack?.extraAttributes: CompoundTag?
        get() = this?.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)?.copyTag()

    /**
     * Returns the custom data tag without copying it. Do not modify it.
     */
    val ItemStack?.extraAttributesView: CompoundTag?
        get() {
            // cast through Any since the accessor is added at runtime
            val data: Any = this?.get(DataComponents.CUSTOM_DATA) ?: return null
            return (data as CustomDataAccessor).`quoi$getTag`()
        }

    val ItemStack?.skyblockId: String?
        get() = this.extraAttributesView?.getString("id")?.orElse(null)

    val ItemStack?.skyblockUuid: String?
        get() = this.extraAttributesView?.getString("uuid")?.orElse(null)

    val ItemStack?.cleanName: String
        get() = this?.hoverName?.string.noControlCodes

    val ItemStack?.reforgeModifier: String?
        get() = extraAttributesView?.getString("modifier")?.orElse(null)?.takeUnless(String::isBlank)

    fun ItemStack?.getCleanLore(): List<String> = lore.orEmpty().map { it.noControlCodes }

    val ItemStack?.normalizedName: String?
        get() = this?.takeUnless { it.isEmpty }?.let {
            it.cleanName.normalizeItemName(it.reforgeModifier).takeIf(String::isNotEmpty)
        }

    fun String.normalizeItemName(reforge: String? = null): String {
        val normalized = noControlCodes
            .replace(upgradeRegex, "")
            .trim()
            .replace(stackSizeRegex, "")
            .trim()
            .replace("'", "")
            .lowercase()
            .replace(whitespaceRegex, " ")
        val prefix = reforge?.replace('_', ' ')?.lowercase()?.trim()?.takeIf { it.isNotEmpty() }
        return if (prefix == null) normalized else normalized.removePrefix("$prefix ")
    }

    inline val ItemStack?.lore: List<String>?
        get() = this?.get(DataComponents.LORE)?.lines?.map { it.string }

    inline val ItemStack?.loreString: String?
        get() = this?.get(DataComponents.LORE)?.lines?.joinToString("\n") { it.string }

    inline val ItemStack?.isShortbow: Boolean
        get() = this?.loreString?.noControlCodes?.contains("Shortbow: Instantly shoots!") == true

    inline val ItemStack.texture: String?
        get() = get(DataComponents.PROFILE)?.partialProfile()?.properties?.get("textures")?.firstOrNull()?.value

    inline val ItemStack.registryPath: String
        get() = BuiltInRegistries.ITEM.getKey(this.item).path

    inline val LocalPlayer.hasTerminator: Boolean
        get() {
            for (i in 0..8) {
                val stack = inventory.getItem(i)
                if (stack.skyblockId == "TERMINATOR") {
                    return true
                }
            }
            return false
        }
}
