package quoi.api.customtriggers.actions

import quoi.api.abobaui.elements.ElementScope
import quoi.api.customtriggers.TriggerContext
import quoi.api.customtriggers.choiceField
import quoi.api.customtriggers.settingRow
import quoi.config.TypeName
import quoi.module.ModuleManager

@TypeName("module")
class ModuleAction(var name: String = "", var state: State = State.TOGGLE) : TriggerAction {
    enum class State(val label: String) { TOGGLE("Toggle"), ENABLE("Enable"), DISABLE("Disable") }

    override fun validationError() = if (name.isBlank()) "Select a module." else null

    override fun execute(ctx: TriggerContext) {
        val moduleName = ctx.expand(name).trim()
        val module = checkNotNull(ModuleManager.getModuleByName(moduleName)) { "Unknown module '$moduleName'" }
        val enabled = when (state) {
            State.TOGGLE -> !module.enabled
            State.ENABLE -> true
            State.DISABLE -> false
        }
        if (module.enabled != enabled) module.toggle()
    }

    override fun displayString() = "${state.label} module: $name"

    override fun ElementScope<*>.draw() = settingRow(
        { choiceField("Module", { name }, ModuleManager.modules.map { it.name }.sorted()) { name = it } },
        { choiceField("State", { state }, State.entries, { it.label }) { state = it } },
    )
}
