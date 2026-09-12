package quoi.api.customtriggers.triggers

import quoi.api.abobaui.constraints.impl.size.Copying
import quoi.api.abobaui.dsl.*
import quoi.api.abobaui.elements.ElementScope
import quoi.api.customtriggers.*
import quoi.config.TypeName

@TypeName("command_sent")
class CommandTrigger(var command: String = "", var cancel: Boolean = true) : Trigger {
    override val eventKind get() = TriggerContext.Kind.COMMAND
    override fun validationError() = if (command.trim().removePrefix("/").isBlank()) "Enter a command." else null

    override fun matches(ctx: TriggerContext): Boolean {
        if (ctx !is TriggerContext.Command) return false
        val expected = command.trim().removePrefix("/")
        if (expected.isEmpty() || !(ctx.command == expected || ctx.command.startsWith("$expected "))) return false
        ctx.data["%command%"] = ctx.command
        ctx.data["%args%"] = ctx.command.removePrefix(expected).trimStart()
        ctx.cancelled = ctx.cancelled || cancel
        return true
    }
    override fun displayString() = "Command /${command.removePrefix("/")}"
    override fun ElementScope<*>.draw() = settingRow(
        { textField("Command, without arguments", command) { command = it } },
        { toggleField("Consume matching command", ::cancel) },
    )
}
