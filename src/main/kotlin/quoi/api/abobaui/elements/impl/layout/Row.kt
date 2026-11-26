package quoi.api.abobaui.elements.impl.layout

import quoi.api.abobaui.constraints.Constraint
import quoi.api.abobaui.constraints.Constraints
import quoi.api.abobaui.elements.Layout

class Row(
    constraints: Constraints,
    gap: Constraint.Size? = null
) : Layout(constraints, gap) {

    override fun prePosition() {
        val gap = gap?.calculateSize(this, horizontal = true) ?: 0f
        var increment = 0f
        children?.forEach {
            if (it.constraints.x.undefined() && it.enabled) {
                it.internalX = increment
                increment += it.width + if (it is Divider) 0f else gap
            }
        }
    }
}
