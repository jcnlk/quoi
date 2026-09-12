package quoi.api.customtriggers.actions

import quoi.api.abobaui.constraints.impl.size.Copying
import quoi.api.abobaui.dsl.*
import quoi.api.abobaui.elements.ElementScope
import quoi.api.customtriggers.*
import quoi.config.TypeName
import quoi.utils.ChatUtils

@TypeName("replace_message")
class ReplaceMessageAction(var replacement: String = "") : TriggerAction {
    override val requiredEvent get() = TriggerContext.Kind.CHAT
    override val allowsDelay get() = false
    override fun execute(ctx: TriggerContext) {
        if (ctx !is TriggerContext.Chat) return
        ctx.cancelled = true
        ChatUtils.modMessage(ctx.expand(replacement), prefix = "")
    }
    override fun displayString() = "Replace message: $replacement"
    override fun ElementScope<*>.draw() = column(size(w = Copying)) {
        textField("Replacement, supports %0% and named captures", replacement) { replacement = it }
    }
}
