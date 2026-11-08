package quoi.utils

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.fabricmc.fabric.impl.command.client.ClientCommandInternals
import net.minecraft.client.gui.components.ChatComponent
import net.minecraft.client.multiplayer.chat.GuiMessage
import net.minecraft.client.multiplayer.chat.GuiMessageTag
import net.minecraft.network.chat.*
import net.minecraft.world.entity.player.ChatVisiblity
import quoi.QuoiMod.mc
import quoi.api.colour.Colour
import quoi.mixininterfaces.IChatComponent
import quoi.mixininterfaces.IGuiMessage
import quoi.utils.StringUtils.noControlCodes
import kotlin.math.floor

object ChatUtils {

    @Suppress("cast_never_succeeds")
    inline var GuiMessage.id: Int
        get() = (this as IGuiMessage).`quoi$getId`()
        set(value) {
            (this as IGuiMessage).`quoi$setId`(value)
        }

    fun ChatComponent.add(text: Component, id: Int) =
        (this as IChatComponent).`quoi$add`(text, id)

    fun ChatComponent.toChatLineMX(x: Double): Double =
        x / scale - 4.0

    fun ChatComponent.toChatLineMY(y: Double): Double =
        (mc.window.guiScaledHeight - y - 40.0) / (scale * lineHeight)

    fun ChatComponent.getMessageLineIdx(chatLineX: Double, chatLineY: Double): Int = run {
        if (!isChatFocused || mc.options.chatVisibility().get() == ChatVisiblity.HIDDEN) return -1
        if (chatLineX < -4.0 || chatLineX > floor(width.toDouble() / scale)) return -1

        val lineCount = minOf(linesPerPage, trimmedMessages.size)
        if (chatLineY < 0.0 || chatLineY >= lineCount) return -1

        val idx = floor(chatLineY + chatScrollbarPos.toDouble()).toInt()
        idx.takeIf { it in trimmedMessages.indices } ?: -1
    }

    fun prefix(): Component {
        return literal("[").withColor(Colour.WHITE.rgb)
            .append(literal("quoi!").withColor(Colour.GREEN.rgb))
            .append("]").withColor(Colour.WHITE.rgb)
    }

    fun literal(string: String): MutableComponent {
        return Component.literal(string.replace("&", "§"))
    }

    fun removeLines(id: Int, text: String): Boolean {
        return removeLines { it.id == id || it.content().string.noControlCodes == text }
    }

    fun removeLines(id: Int): Boolean {
        return removeLines { it.id == id }
    }

    fun removeLines(cb: (GuiMessage) -> Boolean): Boolean {
        var removedLine = false
        val messageList = mc.gui.hud.chat.allMessages.listIterator()

        while (messageList.hasNext()) {
            val msg = messageList.next()
            if (!cb(msg)) continue

            messageList.remove()
            removedLine = true
        }

        if (removedLine) mc.gui.hud.chat.refreshTrimmedMessages()

        return removedLine
    }

    fun editLines(id: Int, replaceWith: Component): Boolean {
        return editLines({ it.id == id }, replaceWith)
    }

    fun editLines(cb: (GuiMessage) -> Boolean, replaceWith: Component): Boolean {
        var editedLine = false
        val indicator =
            if (mc.hasSingleplayerServer()) GuiMessageTag.systemSinglePlayer()
            else GuiMessageTag.system()
        val messageList = mc.gui.hud.chat.allMessages.listIterator()

        while (messageList.hasNext()) {
            val msg = messageList.next()
            if (!cb(msg)) continue

            editedLine = true
            messageList.remove()

            val line = GuiMessage(msg.addedTime(), replaceWith, null, msg.source(), indicator)
            line.id = msg.id
            messageList.add(line)
        }

        if (editedLine) mc.gui.hud.chat.refreshTrimmedMessages()

        return editedLine
    }

    fun command(command: String, client: Boolean = false) {
        val cmd = command.removePrefix("/")
        if (!client) player.connection.sendCommand(cmd)
        else executeClientCommand(cmd)
    }

    fun commandAny(command: String) {
        if (!executeClientCommand(command)) command(command)
    }

    private fun executeClientCommand(command: String): Boolean {
        val source = mc.connection?.suggestionsProvider as? FabricClientCommandSource ?: return false
        return ClientCommandInternals.executeCommand(command, source, null)
    }

    fun say(message: String) = connection.sendChat(message)

    fun button(text: String, command: String, hoverText: String? = null, action: ClickEvent.Action = ClickEvent.Action.RUN_COMMAND): MutableComponent {
        val literal = literal(text)

        val clickEvent = when (action) {
            ClickEvent.Action.RUN_COMMAND -> ClickEvent.RunCommand(command)
            ClickEvent.Action.SUGGEST_COMMAND -> ClickEvent.SuggestCommand(command)
            else -> null
        }

        clickEvent?.let {
            literal.style = Style.EMPTY.withClickEvent(it)
            hoverText?.let {
                literal.style = literal.style.withHoverEvent(HoverEvent.ShowText(literal(hoverText)))
            }
        }

        return literal
    }

    // TODO: drop support for custom prefix?
    fun modMessage(message: Any?, id: Int? = null, prefix: Any = this.prefix(), chatStyle: Style? = null) {
        val p: MutableComponent = when (prefix) {
            is Component -> Component.empty().append(prefix).append(" ")
            is String -> literal(if (prefix.isEmpty()) "" else "$prefix ")
            else -> literal("$prefix ")
        }
        val text: Component = when (message) {
            is Component -> p.append(message)
            else -> p.append(literal(message.toString()))
        }.also { chatStyle?.let(it::setStyle) }

        mc.execute {
            id?.let { mc.gui.hud.chat.add(text, it) } ?: mc.gui.hud.chat.addClientSystemMessage(text)
        }
    }
}
