package quoi.module.impl.general.chat.impl

import quoi.api.events.ChatEvent
import quoi.api.events.core.on
import quoi.module.impl.general.chat.Chat
import quoi.module.settings.group.ToggleableGroup
import quoi.utils.ChatUtils.command
import quoi.utils.ChatUtils.say

object ChatEmojis : ToggleableGroup(
    Chat,
    "Chat emojis",
    desc = "Replaces supported chat emote codes with symbols."
) {
    private var pendingSentReplacement: String? = null

    init {
        on<ChatEvent.Sent> {
            if (pendingSentReplacement == message) {
                pendingSentReplacement = null
                return@on
            }

            val replacedMessage = replaceEmojis(message)
            if (replacedMessage == message) return@on

            cancel()
            pendingSentReplacement = replacedMessage
            if (isCommand) command(replacedMessage) else say(replacedMessage)
        }
    }

    override fun onDisable() {
        pendingSentReplacement = null
    }

    private fun replaceEmojis(message: String): String {
        var replaced = false
        val replacedMessage = message.split(" ").joinToString(" ") { word ->
            emojiReplacements[word]?.also { replaced = true } ?: word
        }
        return if (replaced) replacedMessage else message
    }

    private val emojiReplacements = mapOf(
        "<3" to "❤",
        "o/" to "( ﾟ◡ﾟ)/",
        ":star:" to "✮",
        ":yes:" to "✔",
        ":no:" to "✖",
        ":java:" to "☕",
        ":arrow:" to "➜",
        ":shrug:" to "¯\\_(ツ)_/¯",
        ":tableflip:" to "(╯°□°）╯︵ ┻━┻",
        ":totem:" to "☉_☉",
        ":typing:" to "✎...",
        ":maths:" to "√(π+x)=L",
        ":snail:" to "@'-'",
        ":thinking:" to "(0.o?)",
        ":gimme:" to "༼つ◕_◕༽つ",
        ":wizard:" to "(' - ')⊃━☆ﾟ.*･｡ﾟ",
        ":pvp:" to "⚔",
        ":peace:" to "✌",
        ":puffer:" to "<('O')>",
        "h/" to "ヽ(^◇^*)/",
        ":sloth:" to "(・⊝・)",
        ":dog:" to "(ᵔᴥᵔ)",
        ":dj:" to "ヽ(⌐■_■)ノ♬",
        ":yey:" to "ヽ (◕◡◕) ﾉ",
        ":snow:" to "☃",
        ":dab:" to "<o/",
        ":cat:" to "= ＾● ⋏ ●＾ =",
        ":cute:" to "(✿◠‿◠)",
        ":skull:" to "☠"
    )
}
