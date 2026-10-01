package quoi.api.input

import quoi.QuoiMod.mc
//$ mouse_backend_import {
import org.lwjgl.glfw.GLFW
//$}

object CatMouse {
    //$ sdl_mouse_buttons {
    // Empty.
    //$}

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

    //$ mouse_button_state {
    fun isButtonDown(code: Int): Boolean {
        val state = GLFW.glfwGetMouseButton(mc.window.handle(), code)
        return state == GLFW.GLFW_PRESS || state == GLFW.GLFW_REPEAT
    }
    //$}

    val mx: Float get() = mc.mouseHandler.xpos().toFloat()

    val my: Float get() = mc.mouseHandler.ypos().toFloat()

    fun setCursor(cursor: Long) {
        //$ set_cursor {
        GLFW.glfwSetCursor(mc.window.handle(), cursor)
        //$}
    }
}
