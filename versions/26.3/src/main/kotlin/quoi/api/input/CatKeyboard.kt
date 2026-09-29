package quoi.api.input

import com.mojang.blaze3d.platform.InputConstants

object CatKeyboard {
    val modifierCodes = intArrayOf(
        Keybinds.KEY_LEFT_CONTROL, Keybinds.KEY_RIGHT_CONTROL,
        Keybinds.KEY_LEFT_SHIFT, Keybinds.KEY_RIGHT_SHIFT,
        Keybinds.KEY_LEFT_ALT, Keybinds.KEY_RIGHT_ALT
    )

    interface ModState {
        val isCtrlDown: Boolean
        val isShiftDown: Boolean
        val isAltDown: Boolean

        val isLeftCtrlDown: Boolean
        val isLeftShiftDown: Boolean
        val isLeftAltDown: Boolean

        val isRightCtrlDown: Boolean
        val isRightShiftDown: Boolean
        val isRightAltDown: Boolean
    }

    object Modifier : ModState {
        override val isLeftCtrlDown get() = isKeyDown(Keybinds.KEY_LEFT_CONTROL)
        override val isRightCtrlDown get() = isKeyDown(Keybinds.KEY_RIGHT_CONTROL)

        override val isLeftShiftDown get() = isKeyDown(Keybinds.KEY_LEFT_SHIFT)
        override val isRightShiftDown get() = isKeyDown(Keybinds.KEY_RIGHT_SHIFT)

        override val isLeftAltDown get() = isKeyDown(Keybinds.KEY_LEFT_ALT)
        override val isRightAltDown get() = isKeyDown(Keybinds.KEY_RIGHT_ALT)

        override val isCtrlDown get() = isLeftCtrlDown || isRightCtrlDown
        override val isShiftDown get() = isLeftShiftDown || isRightShiftDown
        override val isAltDown get() = isLeftAltDown || isRightAltDown
    }

    @JvmStatic
    fun getKeyName(key: Int): String? {
        if (key == Keybinds.KEY_NONE) return "None"

        return InputConstants.Type.KEYBOARD.getOrCreate(key).displayName.string.ifBlank { null } ?: when (key) {
            Keybinds.KEY_SPACE -> "Space"
            Keybinds.KEY_ENTER -> "Enter"
            Keybinds.KEY_TAB -> "Tab"
            Keybinds.KEY_BACKSPACE -> "Backspace"
            Keybinds.KEY_INSERT -> "Insert"
            Keybinds.KEY_DELETE -> "Delete"
            Keybinds.KEY_RIGHT -> "Arrow Right"
            Keybinds.KEY_LEFT -> "Arrow Left"
            Keybinds.KEY_DOWN -> "Arrow Down"
            Keybinds.KEY_UP -> "Arrow Up"
            Keybinds.KEY_LEFT_SHIFT -> "LShift"
            Keybinds.KEY_RIGHT_SHIFT -> "RShift"
            Keybinds.KEY_LEFT_CONTROL -> "LCtrl"
            Keybinds.KEY_RIGHT_CONTROL -> "RCtrl"
            Keybinds.KEY_LEFT_ALT -> "LAlt"
            Keybinds.KEY_RIGHT_ALT -> "RAlt"
            in Keybinds.KEY_F1..Keybinds.KEY_F12 ->
                "F${key - Keybinds.KEY_F1 + 1}"
            in InputConstants.KEY_F13..InputConstants.KEY_F24 ->
                "F${key - InputConstants.KEY_F13 + 13}"
            else -> null
        }
    }

    @JvmStatic
    fun isKeyDown(key: Int): Boolean {
        return InputConstants.isKeyDown(key)
    }
}