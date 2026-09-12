package quoi.api.customtriggers.triggers

import quoi.api.abobaui.elements.ElementScope
import quoi.api.customtriggers.*
import quoi.config.TypeName

@TypeName("chat_received")
class ChatTrigger(var matcher: TextMatcher = TextMatcher()) : Trigger {
    override val eventKind get() = TriggerContext.Kind.CHAT
    override fun validationError() = matcher.validationError()
    override fun matches(ctx: TriggerContext): Boolean {
        if (ctx !is TriggerContext.Chat) return false
        val captures = matcher.captures(ctx.message) ?: return false
        ctx.data["%message%"] = ctx.message
        ctx.data.putAll(captures)
        return true
    }
    override fun displayString() = "Chat: ${matcher.pattern}"
    override fun ElementScope<*>.draw() = settingRow(
        { textField("Message", matcher.pattern) { matcher.pattern = it } },
        { choiceField("Match", { matcher.mode }, TextMatchMode.entries, { it.label }) { matcher.mode = it } }
    )
}
