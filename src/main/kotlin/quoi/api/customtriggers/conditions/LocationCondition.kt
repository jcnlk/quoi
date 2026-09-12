package quoi.api.customtriggers.conditions

import quoi.api.abobaui.constraints.impl.size.Copying
import quoi.api.abobaui.dsl.*
import quoi.api.abobaui.elements.ElementScope
import quoi.api.customtriggers.*
import quoi.config.TypeName
import quoi.api.skyblock.location.Island
import quoi.api.skyblock.location.Location

@TypeName("location")
class LocationCondition(var island: Island = Island.Hub, var subarea: String = "") : TriggerCondition {
    override fun matches(ctx: TriggerContext) = Location.currentArea == island &&
        (subarea.isBlank() || Location.subarea?.trim()?.equals(subarea.trim(), true) == true)
    override fun displayString() = "In ${island.displayName}${if (subarea.isBlank()) "" else ": $subarea"}"
    override fun ElementScope<*>.draw() = settingRow(
        { choiceField("Island", { island }, Island.entries, { it.displayName }) { island = it } },
        { textField("Subarea (optional)", subarea) { subarea = it } }
    )
}
