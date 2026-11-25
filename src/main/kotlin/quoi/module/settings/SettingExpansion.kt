package quoi.module.settings

import quoi.api.abobaui.constraints.Constraint
import quoi.api.abobaui.constraints.impl.measurements.Animatable
import quoi.api.abobaui.constraints.impl.size.Bounding
import quoi.api.abobaui.constraints.impl.size.Copying
import quoi.api.abobaui.constraints.impl.size.Fill
import quoi.api.abobaui.dsl.*
import quoi.api.abobaui.elements.ElementScope
import quoi.api.abobaui.elements.Layout.Companion.divider
import quoi.api.abobaui.elements.impl.layout.Column
import quoi.api.abobaui.transforms.impl.Alpha
import quoi.api.animations.Animation
import quoi.api.input.CursorShape
import quoi.utils.ThemeManager.theme
import quoi.utils.ui.cursor
import quoi.utils.ui.watch

internal const val SETTING_CHEVRON_WIDTH = 28
internal const val SETTING_EXPANSION_GAP = 8

internal fun ElementScope<*>.settingChevron(
    y: Constraint.Position = 0.px,
    expanded: () -> Boolean,
    toggleExpansion: () -> Boolean,
) = image(
    image = theme.chevronImage,
    colour = theme.onSurfaceVariant,
    constraints = constrain(x = 5.px.alignOpposite, y = y, w = 16.px, h = 16.px),
) {
    cursor(CursorShape.HAND)

    val (from, to) = if (expanded()) 90f to 180f else 180f to 90f
    val rotation = rotation(from = from, to = to)
    watch(expanded) {
        rotation.animate(0.25.seconds, Animation.Style.EaseInOutQuint)
        redraw()
    }

    onClick { toggleExpansion() }
}

internal fun ElementScope<*>.indentedSettings(
    gap: Constraint.Size? = null,
    content: ElementScope<Column>.() -> Unit,
) = row(size(w = Copying, h = Bounding)) {
    block(size(w = 2.5.px, h = Copying), colour = theme.primary)
    divider(6.px)
    column(size(w = Fill), gap = gap, block = content)
}

internal fun ElementScope<*>.animateSettingVisibility(isVisible: () -> Boolean) {
    val alpha = Alpha.Animated(from = 1f, to = 0f)
    element.addTransform(alpha)

    if (element.constraints.height !is Animatable) {
        val visible = isVisible()
        element.constraints.height = Animatable(from = element.constraints.height, to = 0.px, swapIf = !visible)
        if (!visible) alpha.swap()
    }

    watch(isVisible) {
        (element.constraints.height as Animatable).animate(0.2.seconds, Animation.Style.EaseInOutQuint)
        alpha.animate(0.2.seconds, Animation.Style.EaseInOutQuint)
        element.parent?.redraw = true
    }
}
