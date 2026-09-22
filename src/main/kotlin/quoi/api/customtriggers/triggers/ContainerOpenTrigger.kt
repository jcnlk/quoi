package quoi.api.customtriggers.triggers

import quoi.api.abobaui.elements.ElementScope
import quoi.api.customtriggers.*
import quoi.config.TypeName

@TypeName("container_opened")
class ContainerOpenTrigger(var title: TextMatcher = TextMatcher()) : Trigger {
    override val eventKind get() = TriggerContext.Kind.CONTAINER
    override fun validationError() = title.validationError()
    override fun matches(ctx: TriggerContext): Boolean {
        if (ctx !is TriggerContext.Container) return false
        val captures = title.captures(ctx.title) ?: return false
        ctx.data["%title%"] = ctx.title
        ctx.data.putAll(captures)
        return true
    }
    override fun displayString() = "Container opened: ${title.pattern}"
    override fun ElementScope<*>.draw() = settingRow(
        { textField("Title", title.pattern) { title.pattern = it } },
        { choiceField("Match", { title.mode }, TextMatchMode.entries, { it.label }) { title.mode = it } }
    )
}
