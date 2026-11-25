package quoi.module.settings.impl

import quoi.api.abobaui.constraints.impl.measurements.Animatable
import quoi.api.abobaui.constraints.impl.positions.Centre
import quoi.api.abobaui.constraints.impl.size.Copying
import quoi.api.abobaui.dsl.*
import quoi.api.abobaui.elements.ElementScope
import quoi.api.abobaui.elements.Layout.Companion.divider
import quoi.api.abobaui.elements.impl.Group
import quoi.api.abobaui.elements.impl.Text.Companion.maxWidth
import quoi.api.abobaui.elements.impl.layout.Column
import quoi.api.animations.Animation
import quoi.module.settings.SETTING_CHEVRON_WIDTH
import quoi.module.settings.SETTING_EXPANSION_GAP
import quoi.module.settings.animateSettingVisibility
import quoi.module.settings.indentedSettings
import quoi.module.settings.settingChevron
import quoi.utils.ThemeManager.theme
import quoi.utils.ui.watch

internal class CollapsibleEditor(
    private val name: String,
    private val collapsible: Boolean,
) {
    var expanded = !collapsible
        private set

    fun draw(
        scope: ElementScope<*>,
        header: ElementScope<Group>.() -> Unit = {
            text(name, size = theme.textSize, colour = theme.onSurfaceVariant, pos = at(x = 0.px, y = Centre)) {
                maxWidth(Copying)
            }
        },
        content: ElementScope<Column>.() -> Unit,
    ): ElementScope<*> = scope.column(size(w = Copying)) {
        group(size(w = Copying, h = 18.px)) {
            val headerWidth = if (collapsible) Copying - SETTING_CHEVRON_WIDTH.px else Copying
            group(constrain(x = 0.px, y = Centre, w = headerWidth, h = Copying), header)
            if (collapsible) {
                settingChevron(expanded = { expanded }) {
                    expanded = !expanded
                    true
                }
            }
        }

        if (!collapsible) {
            divider(4.px)
            column(size(w = Copying), gap = 4.px, block = content)
            return@column
        }

        val gap = Animatable(from = SETTING_EXPANSION_GAP.px, to = 0.px, swapIf = !expanded)
        divider(gap)
        watch(::expanded) { gap.animate(0.2.seconds, Animation.Style.EaseInOutQuint) }

        indentedSettings(gap = 4.px) {
            content()
            element.scissors = true
            animateSettingVisibility { expanded }
        }
    }
}
