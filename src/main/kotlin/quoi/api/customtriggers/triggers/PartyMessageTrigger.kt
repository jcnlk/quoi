package quoi.api.customtriggers.triggers

import quoi.api.abobaui.elements.ElementScope
import quoi.api.customtriggers.*
import quoi.config.TypeName

@TypeName("party_message")
class PartyMessageTrigger(
    var message: TextMatcher = TextMatcher(),
    var sender: TextMatcher = TextMatcher(pattern = "", mode = TextMatchMode.INCLUDES)
) : Trigger {
    override val eventKind get() = TriggerContext.Kind.PARTY_MESSAGE
    override fun validationError() = message.validationError()
    override fun matches(ctx: TriggerContext): Boolean {
        if (ctx !is TriggerContext.PartyMessage || sender.pattern.isNotBlank() && !sender.matches(ctx.sender)) return false
        val captures = message.captures(ctx.message) ?: return false
        ctx.data["%sender%"] = ctx.sender
        ctx.data["%message%"] = ctx.message
        ctx.data.putAll(captures)
        return true
    }
    override fun displayString() = "Party message: ${message.pattern}"
    override fun ElementScope<*>.draw() = settingRow(
        { textField("Message", message.pattern) { message.pattern = it } },
        { choiceField("Match", { message.mode }, TextMatchMode.entries, { it.label }) { message.mode = it } },
        { textField("Sender (optional)", sender.pattern) { sender.pattern = it } }
    )
}
