//~ ui_frame
//~ gui_extraction

package quoi.utils.ui.screens

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import quoi.QuoiMod.mc
import quoi.api.abobaui.AbobaUI
import quoi.api.input.Keyboard.Modifier.isCtrlDown
import quoi.api.input.Keybinds
import quoi.utils.Scheduler.scheduleTask
import quoi.utils.sf
import quoi.utils.ui.rendering.NVGSpecialRenderer

class UIScreen(val instance: AbobaUI.Instance, val background: Boolean = true ) : Screen(Component.literal(instance.title)) {

    override fun init() {
        instance.init(mc.window.width, mc.window.height)
    }

    override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        //? if <26.2
        super.extractRenderState(ctx, mouseX, mouseY, deltaTicks)
        instance.ctx = ctx
        //? if >=26.4-snapshot-5 {
        /*val mouseX = ctx.mouseX()
        val mouseY = ctx.mouseY()
        *///?}
        instance.eventManager.onMouseMove(mouseX * sf.toFloat(), mouseY * sf.toFloat())
        instance.prepareFrame()
        NVGSpecialRenderer.draw(ctx, 0, 0, ctx.guiWidth(), ctx.guiHeight()) {
            instance.render(true)
        }
        instance.render(false)
        //? if >=26.2
        //super.extractRenderState(ctx, mouseX, mouseY, deltaTicks)
    }

    override fun mouseClicked(mouseButtonEvent: MouseButtonEvent, doubled: Boolean) =
        instance.eventManager.onMouseClick(mouseButtonEvent.button())

    override fun mouseReleased(mouseButtonEvent: MouseButtonEvent): Boolean {
        instance.eventManager.onMouseRelease(mouseButtonEvent.button())
        return super.mouseReleased(mouseButtonEvent)
    }

    override fun keyPressed(keyEvent: KeyEvent): Boolean {
        val a = instance.eventManager.onKeyTyped(keyEvent.key)

        val ctrlHotkeys = mapOf(
            Keybinds.KEY_V to 'v',
            Keybinds.KEY_C to 'c',
            Keybinds.KEY_W to 'w',
            Keybinds.KEY_X to 'x',
            Keybinds.KEY_A to 'a'
        )
        val shortcut = ctrlHotkeys[keyEvent.key]
        var b = false
        if (isCtrlDown && shortcut != null) {
            b = instance.eventManager.onKeyTyped(shortcut)
        }
        return a || b || super.keyPressed(keyEvent)
    }

    override fun keyReleased(keyEvent: KeyEvent) = instance.eventManager.onKeyReleased(keyEvent.key)

    override fun charTyped(characterEvent: CharacterEvent) =
        if (characterEvent.isAllowedChatCharacter) instance.eventManager.onKeyTyped(characterEvent.codepoint.toChar()) else false

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double) =
        instance.eventManager.onMouseScroll(verticalAmount.toFloat())

    override fun removed() {
        instance.close()
        super.removed()
    }

    override fun isPauseScreen() = false

    override fun extractBackground(guiGraphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        if (background) super.extractBackground(guiGraphics, mouseX, mouseY, deltaTicks)
    }

    companion object {
        fun open(ui: AbobaUI.Instance, background: Boolean = true) = scheduleTask { mc.setScreen(UIScreen(ui, background)) }
    }
}
