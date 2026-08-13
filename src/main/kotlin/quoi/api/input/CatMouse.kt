package quoi.api.input

import org.lwjgl.sdl.SDLMouse
import quoi.QuoiMod.mc

object CatMouse {

    /**
     * Converts SDL's 1-based button order (left, middle, right) to the
     * legacy order used throughout quoi (left, right, middle, ...).
     */
    @JvmStatic
    fun normalizeButton(code: Int): Int = when (code) {
        SDLMouse.SDL_BUTTON_LEFT -> 0
        SDLMouse.SDL_BUTTON_RIGHT -> 1
        SDLMouse.SDL_BUTTON_MIDDLE -> 2
        else -> code - 1
    }

    fun toSDLButton(code: Int): Int = when (code) {
        0 -> SDLMouse.SDL_BUTTON_LEFT
        1 -> SDLMouse.SDL_BUTTON_RIGHT
        2 -> SDLMouse.SDL_BUTTON_MIDDLE
        else -> code + 1
    }

    fun getButtonName(code: Int): String {
        return when (code) {
            0 -> "Mouse Left"
            1 -> "Mouse Right"
            2 -> "Mouse Middle"
            3 -> "Mouse 4"
            4 -> "Mouse 5"
            5 -> "Mouse 6"
            6 -> "Mouse 7"
            7 -> "Mouse 8"
            else -> "Unknown"
        }
    }

    fun isButtonDown(code: Int): Boolean {
        if (code !in 0..7) return false
        return isSDLButtonDown(toSDLButton(code))
    }

    fun isSDLButtonDown(code: Int): Boolean {
        if (code !in 1..Int.SIZE_BITS) return false
        val state = SDLMouse.SDL_GetMouseState(null, null)
        return state and (1 shl (code - 1)) != 0
    }

    val mx: Float get() = mc.mouseHandler.xpos().toFloat()

    val my: Float get() = mc.mouseHandler.ypos().toFloat()

    fun setCursor(cursor: Long) {
        SDLMouse.SDL_SetCursor(if (cursor == 0L) SDLMouse.SDL_GetDefaultCursor() else cursor)
    }
}