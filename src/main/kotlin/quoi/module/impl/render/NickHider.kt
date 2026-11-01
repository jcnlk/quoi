package quoi.module.impl.render

import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import net.minecraft.util.FormattedCharSequence
import quoi.api.colour.Colour
import quoi.module.Module
import quoi.utils.ChatUtils.literal

/**
 * modified Client-Custom-Name (GPL-3.0) (c) Ownwn
 * original: https://github.com/Ownwn/Client-Custom-Name
 */
object NickHider : Module(
    "Nick Hider",
    desc = "Visually hides player name."
) {
    val customName by textInput("Name", "cat", desc = "Player name to display.")
    private val nameColour by colourPicker("Name colour", Colour.ORANGE, desc = "Colour of the displayed name.")

    private fun getUsername() = mc.player?.name?.string?.takeIf { enabled && customName != it }

    @JvmStatic
    fun replaceName(string: String): String {
        val u = getUsername() ?: return string
        return string.replace(u, customName)
    }

    @JvmStatic
    fun replaceName(text: FormattedCharSequence): FormattedCharSequence {
        val username = getUsername() ?: return text
        return replaceOrderedText(
            text,
            username,
            literal(customName).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(nameColour.rgb)))
        )
    }
}

internal fun replaceOrderedText(original: FormattedCharSequence, target: String, replacement: Component): FormattedCharSequence {
    if (target.isEmpty()) return original

    val characters = mutableListOf<Component>()
    val string = buildString {
        original.accept { _, style, codePoint ->
            val character = Character.toString(codePoint)
            append(character)
            characters.add(Component.literal(character).setStyle(style))
            true
        }
    }
    var nextMatch = string.indexOf(target)
    if (nextMatch < 0) return original

    val result = Component.empty()
    var offset = 0
    var skipUntil = 0
    for (character in characters) {
        if (offset == nextMatch) {
            result.append(replacement.copy())
            skipUntil = nextMatch + target.length
            nextMatch = string.indexOf(target, skipUntil)
        }
        if (offset >= skipUntil) result.append(character)
        offset += character.string.length
    }
    return result.visualOrderText
}