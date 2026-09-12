package quoi.api.customtriggers.actions

import quoi.api.abobaui.elements.ElementScope
import quoi.api.customtriggers.TriggerContext
import quoi.api.customtriggers.textField
import quoi.api.customtriggers.choiceField
import quoi.api.customtriggers.fieldRow
import quoi.config.TypeName
import quoi.utils.ChatUtils

@TypeName("send_message")
class SendMessageAction(var message: String = "", var client: Boolean = true) : TriggerAction {
    override fun validationError() = if (message.isBlank()) "Enter a message." else null

    override fun execute(ctx: TriggerContext) {
        val msg = ctx.expand(message)

        if (client) ChatUtils.modMessage(msg, prefix = "")
        else ChatUtils.say(msg)
    }

    override fun displayString(): String {
        val msg = if (message.length > 25) message.take(25) + "..." else message
        val side = if (client) "client" else "server"
        return "Send \"$msg\" $side side"
    }

    override fun ElementScope<*>.draw() = fieldRow(
        { textField("Message", message) { message = it } },
        { choiceField("Destination", { client }, listOf(true, false), { if (it) "Client" else "Server" }) { client = it } },
        weights = listOf(3f, 1f)
    )
}