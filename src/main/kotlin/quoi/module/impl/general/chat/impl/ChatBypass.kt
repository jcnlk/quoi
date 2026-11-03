package quoi.module.impl.general.chat.impl

import quoi.api.events.ChatEvent
import quoi.api.events.core.on
import quoi.api.skyblock.location.Location.onHypixel
import quoi.module.impl.general.chat.Chat
import quoi.module.settings.group.ToggleableGroup
import quoi.utils.ChatUtils.commandAny
import quoi.utils.ChatUtils.say

// based on: https://github.com/jcnlk/ChatFilterBypass/blob/main/index.js
object ChatBypass : ToggleableGroup(
    Chat,
    "Chat bypass",
    desc = "Bypasses chat filters on servers."
) {
    private val mode by selector("Mode", BypassMode.Dots, desc = "Bypass mode.")

    private var bypass = false

    init {
        on<ChatEvent.Sent> {
            if (bypass) {
                bypass = false
                return@on
            }

            if (isCommand) {
                socialCommands.firstOrNull { message.equals(it, true) || message.startsWith("$it ", true) }?.let { cmd ->
                    val isPm = socialCommands.drop(4).any { message.startsWith(it) }
                    val text = message.removePrefix(cmd).trimStart()
                    val shit = if (isPm) text.split(" ").first() else ""
                    val content = text.removePrefix(shit).trimStart()

                    val t = buildString {
                        append(cmd)
                        if (shit.isNotEmpty()) append(" $shit")
                        if (content.isNotEmpty()) append(" ${apply(content)}")
                    }

                    cancel()
                    bypass = true
                    commandAny(t)
                }
            } else {
                cancel()
                bypass = true
                say(apply(message))
            }
        }
    }

    val socialCommands = setOf("pc", "ac", "gc", "cc", "r", "msg", "w", "m", "message", "whisper", "tell", "pm") // maybe there are more

    private fun apply(str: String) = mode.selected.apply(str)

    @Suppress("unused")
    private enum class BypassMode(private val separator: String = "") {
        Dots("."),
        SmallCaps {
            private val hMap = mapOf('b' to 'ʙ', 'g' to 'ɢ', 'h' to 'ʜ', 'j' to 'ᴊ', 'q' to 'ǫ', 'z' to 'ᴢ', 'x' to 'x')
            private val map by lazy { "abcdefghijklmnopqrstuvwxyz".zip("ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ").toMap() }

            override fun apply(str: String): String {
                val m = if (onHypixel) hMap else map
                return str.lowercase().map { m[it] ?: it }.joinToString("")
            }
        },
        Spaces(" "),
        Asterisks("*"),
        Dashes("-");

        open fun apply(str: String): String = str.split(' ').joinToString(" ") { word ->
            word.codePoints().toArray().joinToString(separator) { String(Character.toChars(it)) }
        }
    }
}