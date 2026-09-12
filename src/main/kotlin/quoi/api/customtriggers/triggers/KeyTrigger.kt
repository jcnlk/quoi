package quoi.api.customtriggers.triggers

import quoi.api.abobaui.constraints.impl.size.Copying
import quoi.api.abobaui.dsl.*
import quoi.api.abobaui.elements.ElementScope
import quoi.api.customtriggers.TriggerContext
import quoi.api.abobaui.elements.impl.Text.Companion.textSupplied
import quoi.api.input.CatMouse
import quoi.api.input.CursorShape
import quoi.utils.ui.cursor
import quoi.api.input.CatKeyboard
import quoi.api.input.Keybinds
import quoi.config.TypeName
import quoi.utils.ThemeManager.theme

@TypeName("key_pressed")
class KeyTrigger(var key: Int = Keybinds.KEY_NONE) : Trigger {
    override val eventKind get() = TriggerContext.Kind.KEY

    override fun validationError() = if (key == Keybinds.KEY_NONE) "Choose a key or mouse button." else null

    override fun matches(ctx: TriggerContext): Boolean {
        return ctx is TriggerContext.Key && ctx.key == key
    }

    private fun keyName() = when {
        key == Keybinds.KEY_NONE -> "None"
        key < -1 -> CatMouse.getButtonName(key + 100)
        else -> CatKeyboard.getKeyName(key) ?: "Unknown"
    }

    override fun displayString() = "Key [${keyName()}] pressed"

    override fun ElementScope<*>.draw() = column(size(w = Copying), gap = 8.px) {
        block(size(280.px.coerceAtMost(Copying), 32.px), colour = theme.surfaceContainerHighest, radius = 5.radius()) {
            textSupplied(supplier = { if (ui.eventManager.focused == element) "Press a key or mouse button..." else keyName() },
                size = theme.textSize, colour = theme.onSurface)
            cursor(CursorShape.HAND)
            onClick(nonSpecific = true) { (button) ->
                if (ui.eventManager.focused == element) {
                    key = button - 100
                    ui.unfocus()
                } else ui.focus(element)
                true
            }
            onKeyPressed { (pressed, _) ->
                key = if (pressed == Keybinds.KEY_ESCAPE) Keybinds.KEY_NONE else pressed
                ui.unfocus()
                true
            }
        }
    }
}
