package quoi.utils.ui.screens

//~ ui_frame
import quoi.api.abobaui.AbobaUI
import quoi.api.events.GuiEvent
import quoi.api.events.RenderEvent
import quoi.api.events.core.on
import quoi.api.input.Mouse.mx
import quoi.api.input.Mouse.my
import quoi.utils.height
import quoi.utils.width
import quoi.utils.ui.rendering.NVGSpecialRenderer

class UIOverlay(ui: AbobaUI.Instance) : UIHandler(ui) {

    constructor(ui: AbobaUI) : this(AbobaUI.Instance(ui))

    override val events = listOf(

        on<RenderEvent.Overlay>(register = false) {
            resize(width, height)
            ui.ctx = ctx
            mouseMove(mx, my)
            NVGSpecialRenderer.draw(ctx, 0, 0, ctx.guiWidth(), ctx.guiHeight()) {
                ui.render(true)
            }

            ui.render(false)
        },

        on<GuiEvent.Click>(register = false) {
            if (state) mouseClick(button) else mouseRelease(button)
        },

        on<GuiEvent.Key.Press>(register = false) {
            keyTyped(key)
        }
    )
}
