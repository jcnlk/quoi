package quoi.api.customtriggers.ui

import quoi.api.abobaui.constraints.Constraint
import quoi.api.abobaui.dsl.*
import quoi.api.abobaui.elements.Element
import quoi.api.abobaui.elements.ElementScope
import quoi.utils.ui.elements.selector

/**
 * Opens a selector below this control, or above it when there is not enough space.
 */
fun <T> ElementScope<*>.triggerDropdown(
    entries: List<T>,
    displayString: (T) -> String = { it.toString() },
    onSelect: (T) -> Unit
) = selector(
    entries,
    displayString = displayString,
    pos = at(MenuPosition(element, horizontal = true), MenuPosition(element, horizontal = false)),
    size = size(element.width.coerceAtLeast(220f).coerceAtMost(ui.main.width - 24f).px, 32.px),
    onSelect = onSelect
)

private class MenuPosition(private val anchor: Element, private val horizontal: Boolean) : Constraint.Position {
    override fun calculatePos(element: Element, horizontal: Boolean): Float {
        val root = element.ui.main
        if (this.horizontal) return anchor.x.coerceIn(12f, (root.width - element.width - 12f).coerceAtLeast(12f))
        val below = anchor.y + anchor.height + 6f
        val above = anchor.y - element.height - 6f
        return (if (below + element.height <= root.height - 12f) below else above)
            .coerceIn(12f, (root.height - element.height - 12f).coerceAtLeast(12f))
    }
}
