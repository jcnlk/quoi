package quoi.api.customtriggers.triggers

import quoi.api.abobaui.elements.ElementScope
import quoi.api.customtriggers.*
import quoi.api.skyblock.location.Island
import quoi.config.TypeName

@TypeName("area_changed")
class AreaChangeTrigger(var island: Island? = null, var subarea: String = "") : Trigger {
    override val eventKind get() = TriggerContext.Kind.AREA
    override fun matches(ctx: TriggerContext): Boolean {
        if (ctx !is TriggerContext.Area || island != null && ctx.island != island) return false
        if (subarea.isNotBlank() && !ctx.subarea.orEmpty().equals(subarea.trim(), true)) return false
        ctx.data["%island%"] = ctx.island?.displayName.orEmpty()
        ctx.data["%subarea%"] = ctx.subarea.orEmpty()
        return true
    }
    override fun displayString() = "Area changed${island?.let { ": ${it.displayName}" }.orEmpty()}${if (subarea.isBlank()) "" else " / $subarea"}"
    override fun ElementScope<*>.draw() = settingRow(
        { choiceField("Island", { island }, listOf(null) + Island.entries, { it?.displayName ?: "Any" }) { island = it } },
        { textField("Subarea (optional)", subarea) { subarea = it } }
    )
}
