package quoi.api.customtriggers.actions

import quoi.api.abobaui.elements.ElementScope
import quoi.api.customtriggers.*
import quoi.config.TypeName

@TypeName("delay")
class DelayAction(var ticks: Int = 20, var server: Boolean = false) : TriggerAction {
    override fun validationError() = if (ticks !in 0..72000) "Delay must be between 0 and 72000 ticks." else null
    override fun execute(ctx: TriggerContext) = Unit // The engine suspends the sequence at this action.
    override fun displayString() = "Wait $ticks ${if (server) "server" else "client"} ticks"
    override fun ElementScope<*>.draw() = fieldRow(
        { intField("Ticks", { ticks }, 0, 72000) { ticks = it } },
        { choiceField("Clock", { server }, listOf(false, true), { if (it) "Server ticks" else "Client ticks" }) { server = it } }
    )
}
