package quoi.api.customtriggers.actions

import quoi.api.abobaui.elements.ElementScope
import quoi.api.customtriggers.*
import quoi.config.TypeName
import quoi.utils.ChatUtils

@TypeName("command")
class CommandAction(var command: String = "", var target: Target = Target.AUTO) : TriggerAction {
    enum class Target { AUTO, CLIENT, SERVER }
    override fun validationError() = if (command.trim().removePrefix("/").isBlank()) "Enter a command." else null

    override fun execute(ctx: TriggerContext) {
        val value = ctx.expand(command).trim().removePrefix("/")
        when (target) {
            Target.AUTO -> ChatUtils.commandAny(value)
            Target.CLIENT -> ChatUtils.command(value, client = true)
            Target.SERVER -> ChatUtils.command(value)
        }
    }
    override fun displayString() = "Run /$command (${target.name.lowercase()})"
    override fun ElementScope<*>.draw() = settingRow(
        { textField("Command", command) { command = it } },
        { choiceField("Target", { target }, Target.entries) { target = it } }
    )
}
