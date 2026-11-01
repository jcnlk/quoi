package quoi.module.impl.misc.catmode

import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Component.literal
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvents
import net.minecraft.util.FormattedCharSequence
import quoi.module.Module
import quoi.module.impl.misc.catmode.impl.CatModel
import quoi.module.impl.misc.catmode.impl.FallingCats
import quoi.utils.StringUtils.FORMATTING_CODE_PATTERN

/**
 * https://github.com/Noamm9/CatgirlAddons/blob/main/src/main/kotlin/catgirlroutes/module/impl/misc/CatMode.kt
 * https://github.com/Noamm9/CatgirlAddons/blob/main/src/main/kotlin/catgirlroutes/utils/render/FallingKittens.kt
 * https://github.com/jcnlk/quoi/blob/main/src/main/kotlin/quoi/module/impl/misc/CatMode.kt
 */
object CatMode : Module(
    "Cat Mode",
    desc = "MEOWMEOWMEOW"
) {
    private val meowSound by switch("Meowound", desc = "Meow sound everywhere")
    private val meowText by switch("Meow meow?", desc = "Meow everywhere")

    @Suppress("unused")
    private val feat = listOf(FallingCats, CatModel)

    @JvmStatic
    fun replaceSound(sound: Identifier): Identifier {
        if (!enabled || !meowSound) return sound
        return SoundEvents.CAT_AMBIENT_BABY.value().location()
    }

    @JvmStatic
    fun replaceText(text: String): String {
        if (!enabled || !meowText) return text
        return meowify(text)
    }

    @JvmStatic
    fun replaceText(text: FormattedCharSequence): FormattedCharSequence {
        if (!enabled || !meowText) return text

        val result = Component.empty()
        var style: Style? = null

        text.accept { _, s, codepoint ->
            if (Character.isLetter(codepoint)) {
                style = style ?: s
            } else {
                style?.let { result.append(literal("meow").withStyle(it)) }
                style = null

                result.append(literal(String(Character.toChars(codepoint))).withStyle(s))
            }
            true
        }

        style?.let { result.append(literal("meow").withStyle(it)) }

        return result.visualOrderText
    }

    private fun meowify(text: String): String {
        if (text.isBlank()) return text

        return MEOWIFY_PATTERN.replace(text) { res ->
            if (res.groupValues[1].isNotEmpty()) res.value else "meow"
        }
    }

    private val MEOWIFY_PATTERN = Regex("(${FORMATTING_CODE_PATTERN.pattern})|(\\p{L}+)", RegexOption.IGNORE_CASE)
}
