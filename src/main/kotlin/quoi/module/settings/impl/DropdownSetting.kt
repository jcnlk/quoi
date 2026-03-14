package quoi.module.settings.impl

import quoi.api.abobaui.constraints.impl.measurements.Animatable
import quoi.api.abobaui.constraints.impl.positions.Centre
import quoi.api.abobaui.constraints.impl.size.Bounding
import quoi.api.abobaui.constraints.impl.size.Copying
import quoi.api.abobaui.constraints.impl.size.Fill
import quoi.api.abobaui.dsl.*
import quoi.api.abobaui.elements.ElementScope
import quoi.api.abobaui.elements.Layout.Companion.divider
import quoi.api.animations.Animation
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.module.impl.render.ClickGui.description
import quoi.utils.ThemeManager.theme
import quoi.module.settings.UISetting

class DropdownSetting(
    name: String,
    desc: String = "",
) : UISetting<DropdownSetting>(name, desc) {

    override val default = this
    override var value: DropdownSetting
        get() = default
        set(value) { collapsed = value.collapsed }

    val children: MutableList<UISetting<*>> = mutableListOf()

    var collapsed = false
        private set
    var collapsible = false
        private set

    // makes this dropdown collapsible
    fun collapsible(collapse: Boolean = true): DropdownSetting {
        collapsible = true
        collapsed = collapse
        return default
    }

    override fun hashCode() = collapsed.hashCode()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DropdownSetting) return false
        return collapsed == other.collapsed
    }


    override fun ElementScope<*>.draw(asSub: Boolean): ElementScope<*> = column(constrain(x = (-4.5).px, w = Copying)) {
        val gap = if (collapsed) Animatable(from = 0.px, to = 5.px) else Animatable(from = 5.px, to = 0.px)
        row(constrain(x = 4.5.px, w = Copying)) {
            text(
                string = name,
                size = theme.textSize,
                colour = theme.textSecondary,
                pos = at(y = Centre)
            )

            image(
                image = theme.chevronImage,
                constrain(5.px.alignOpposite, w = 16.px, h = 16.px)
            ) {
                val (from, to) = if (collapsed) 180f to 90f else 90f to 180f
                val rotation = rotation(from = from, to = to)

                onClick {
                    if (collapsible) {
                        value.collapsed = !value.collapsed
                        gap.animate(0.2.seconds, Animation.Style.EaseInOutQuint)
                    }
                }

                onValueChanged {
                    rotation.animate(0.25.seconds, Animation.Style.EaseInOutQuint)
                }
            }
        }

        divider(gap)
        group(size(w = Copying, h = Bounding)) {
            block(
                constrain(x = 0.px, y = (-21).px, w = 2.5.px, h = Copying + 21.px),
                colour = theme.panel.withAlpha(0.7f),
                2.radius()
            )
            column(constrain(x = 6.px, w = Copying - 1.5.px), gap = 5.px) {
                children.forEach { child ->
                    child.render(this).description(child.description, xOff = 3, yOff = -2)
                }
            }
        }
    }
}