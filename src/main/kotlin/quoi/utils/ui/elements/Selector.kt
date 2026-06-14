package quoi.utils.ui.elements

import quoi.api.abobaui.constraints.Constraint
import quoi.api.abobaui.constraints.Positions
import quoi.api.abobaui.constraints.Sizes
import quoi.api.abobaui.constraints.impl.size.Bounding
import quoi.api.abobaui.dsl.*
import quoi.api.abobaui.elements.ElementScope
import quoi.api.abobaui.elements.impl.Block.Companion.outline
import quoi.api.abobaui.elements.impl.Scrollable.Companion.scroll
import quoi.api.abobaui.elements.impl.popup
import quoi.api.colour.Colour
import quoi.api.colour.colour
import quoi.api.input.CursorShape
import quoi.utils.ThemeManager.theme
import quoi.utils.ui.cursor

inline fun <T> ElementScope<*>.selector(
    entries: List<T>,
    selected: Int? = null,
    noinline selectedIndices: () -> Set<Int> = { selected?.let(::setOf) ?: emptySet() },
    displayString: (T) -> String = { it.toString() },
    colour: Colour = theme.surfaceContainerHighest,
    outline: Colour = theme.outline,
    thickness: Constraint.Measurement = 2.px,
    pos: Positions = at(),
    size: Sizes = size(130.px, 30.px),
    closeOnSelect: Boolean = true,
    crossinline onSelect: (T) -> Unit,
) = popup(copies(), smooth = false) {

    onClick {
        closePopup()
    }

    block(
        constrain(
            pos.x, pos.y,
            w = Bounding + thickness - 0.5.px,
            h = Bounding + thickness - 0.5.px
        ),
        colour = colour,
        5.radius()
    ) {
        outline(outline, thickness)
        cursor(CursorShape.HAND)
        onClick { true }

        val height = size.height.calculateSize(element, false)

        val h = (height * entries.size.coerceAtMost(5) + if (entries.size > 5) height * 0.4f else 0f).px

        val scrollable = scrollable(size(w = size.width, h = h)) {
            column {
                entries.forEachIndexed { i, comp ->
                    block(
                        size,
                        colour = colour { if (i in selectedIndices()) theme.primaryContainer.rgb else colour.rgb },
                        3.5.radius()
                    ) {
//                        hoverEffect(1.1f)
                        tonalHover()

                        text(
                            string = displayString(comp),
                            colour = colour { if (i in selectedIndices()) theme.onPrimaryContainer.rgb else theme.onSurface.rgb }
                        )

                        onClick {
                            onSelect(comp)
                            if (closeOnSelect) closePopup()
                            else element.parent?.redraw = true
                            true
                        }
                    }
                }
            }
        }

        onScroll { (amount) ->
            scrollable.scroll(amount * -(height * 2f))
        }
    }
}