package quoi.api.input

//$ mouse_backend_import {
import org.lwjgl.glfw.GLFW
//$}

object CursorShape {
    //$ system_cursor ARROW ARROW DEFAULT
    val ARROW by lazy { GLFW.glfwCreateStandardCursor(GLFW.GLFW_ARROW_CURSOR) }
    //$ system_cursor HAND HAND POINTER
    val HAND by lazy { GLFW.glfwCreateStandardCursor(GLFW.GLFW_HAND_CURSOR) }
    //$ system_cursor IBEAM IBEAM TEXT
    val IBEAM by lazy { GLFW.glfwCreateStandardCursor(GLFW.GLFW_IBEAM_CURSOR) }
    //$ system_cursor CROSSHAIR CROSSHAIR CROSSHAIR
    val CROSSHAIR by lazy { GLFW.glfwCreateStandardCursor(GLFW.GLFW_CROSSHAIR_CURSOR) }
    //$ system_cursor HRESIZE HRESIZE EW_RESIZE
    val HRESIZE by lazy { GLFW.glfwCreateStandardCursor(GLFW.GLFW_HRESIZE_CURSOR) }
    //$ system_cursor VRESIZE VRESIZE NS_RESIZE
    val VRESIZE by lazy { GLFW.glfwCreateStandardCursor(GLFW.GLFW_VRESIZE_CURSOR) }
    const val NORMAL = 0L
}
