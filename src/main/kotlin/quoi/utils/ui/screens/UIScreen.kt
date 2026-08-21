package quoi.utils.ui.screens

import quoi.QuoiMod.mc
import quoi.api.abobaui.AbobaUI
import quoi.api.input.CatKeyboard.Modifier.isCtrlDown
import quoi.api.input.Keybinds
import quoi.api.input.CatMouse
import quoi.utils.Scheduler.scheduleTask
import quoi.utils.sf
import quoi.utils.ui.rendering.UIRenderer
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

class UIScreen(val instance: AbobaUI.Instance, val background: Boolean = true ) : Screen(Component.literal(instance.title)) {

    override fun init() {
        instance.init(mc.window.width, mc.window.height)
    }

    override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        instance.ctx = ctx
        instance.eventManager.onMouseMove(mouseX * sf.toFloat(), mouseY * sf.toFloat())
        UIRenderer.frame(ctx) {
            instance.render(true)
        }
        instance.render(false)
        super.extractRenderState(ctx, mouseX, mouseY, deltaTicks)
    }

    override fun mouseClicked(mouseButtonEvent: MouseButtonEvent, doubled: Boolean) =
        instance.eventManager.onMouseClick(CatMouse.normalizeButton(mouseButtonEvent.button()))

    override fun mouseReleased(mouseButtonEvent: MouseButtonEvent): Boolean {
        instance.eventManager.onMouseRelease(CatMouse.normalizeButton(mouseButtonEvent.button()))
        return super.mouseReleased(mouseButtonEvent)
    }

    override fun keyPressed(keyEvent: KeyEvent): Boolean {
        val a = instance.eventManager.onKeyTyped(keyEvent.key)

        val ctrlHotkeys = setOf(
            Keybinds.KEY_V,
            Keybinds.KEY_C,
            Keybinds.KEY_W,
            Keybinds.KEY_X,
            Keybinds.KEY_A
        )
        var b = false
        if (isCtrlDown && keyEvent.key in ctrlHotkeys) {
            b = instance.eventManager.onKeyTyped(keyEvent.key.toChar())
        }
        return a || b || super.keyPressed(keyEvent)
    }

    override fun keyReleased(keyEvent: KeyEvent) = instance.eventManager.onKeyReleased(keyEvent.key)

    override fun charTyped(characterEvent: CharacterEvent) =
        if (characterEvent.isAllowedChatCharacter) instance.eventManager.onKeyTyped(characterEvent.codepoint.toChar()) else false

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double) =
        instance.eventManager.onMouseScroll(verticalAmount.toFloat())

    override fun onClose() {
        instance.close()
        super.onClose()
    }

    override fun isPauseScreen() = false

    override fun extractBackground(guiGraphics: GuiGraphicsExtractor, mouseY: Int, j: Int, deltaTicks: Float) {
        if (background) super.extractBackground(guiGraphics, mouseY, j, deltaTicks)
    }

    companion object {
        fun open(ui: AbobaUI.Instance, background: Boolean = true) = scheduleTask { mc.gui.setScreen(UIScreen(ui, background)) }
    }
}
