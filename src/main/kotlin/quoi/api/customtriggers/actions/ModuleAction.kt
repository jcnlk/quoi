package quoi.api.customtriggers.actions

import quoi.api.abobaui.elements.ElementScope
import quoi.api.customtriggers.*
import quoi.config.TypeName
import quoi.module.ModuleManager

@TypeName("module")
class ModuleAction(var name: String = "", var state: State = State.TOGGLE) : TriggerAction {
    enum class State(val label: String) { TOGGLE("Toggle"), ENABLE("Enable"), DISABLE("Disable") }

    override fun validationError() = if (name.isBlank()) "Select a module." else null

    override fun execute(ctx: TriggerContext) {
        val moduleName = ctx.expand(name).trim()
        val module = checkNotNull(ModuleManager.getModuleByName(moduleName)) { "Unknown module '$moduleName'" }
        check(!module.alwaysActive) { "${module.name} is always active" }
        val enabled = when (state) {
            State.TOGGLE -> !module.enabled
            State.ENABLE -> true
            State.DISABLE -> false
        }
        if (module.enabled != enabled) module.toggle()
    }

    override fun displayString() = "${state.label} module: $name"

    override fun ElementScope<*>.draw() = settingRow(
        {
            choiceField("Module", { name }, ModuleManager.modules.filterNot { it.alwaysActive }.map { it.name }.sorted()) {
                name = it
            }
        },
        { choiceField("State", { state }, State.entries, { it.label }) { state = it } },
    )
}
