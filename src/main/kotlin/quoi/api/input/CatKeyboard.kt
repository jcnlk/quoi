package quoi.api.input

import com.mojang.blaze3d.platform.InputConstants

object CatKeyboard {
    val modifierCodes = intArrayOf(
        CatKeys.KEY_LEFT_CONTROL, CatKeys.KEY_RIGHT_CONTROL,
        CatKeys.KEY_LEFT_SHIFT, CatKeys.KEY_RIGHT_SHIFT,
        CatKeys.KEY_LEFT_ALT, CatKeys.KEY_RIGHT_ALT
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
        override val isLeftCtrlDown get() = isKeyDown(CatKeys.KEY_LEFT_CONTROL)
        override val isRightCtrlDown get() = isKeyDown(CatKeys.KEY_RIGHT_CONTROL)

        override val isLeftShiftDown get() = isKeyDown(CatKeys.KEY_LEFT_SHIFT)
        override val isRightShiftDown get() = isKeyDown(CatKeys.KEY_RIGHT_SHIFT)

        override val isLeftAltDown get() = isKeyDown(CatKeys.KEY_LEFT_ALT)
        override val isRightAltDown get() = isKeyDown(CatKeys.KEY_RIGHT_ALT)

        override val isCtrlDown get() = isLeftCtrlDown || isRightCtrlDown
        override val isShiftDown get() = isLeftShiftDown || isRightShiftDown
        override val isAltDown get() = isLeftAltDown || isRightAltDown
    }

    @JvmStatic
    fun getKeyName(key: Int): String? {
        if (key == CatKeys.KEY_NONE) return "None"

        return InputConstants.Type.KEYBOARD.getOrCreate(key).displayName.string.ifBlank { null } ?: when (key) {
            CatKeys.KEY_SPACE -> "Space"
            CatKeys.KEY_ENTER -> "Enter"
            CatKeys.KEY_TAB -> "Tab"
            CatKeys.KEY_BACKSPACE -> "Backspace"
            CatKeys.KEY_INSERT -> "Insert"
            CatKeys.KEY_DELETE -> "Delete"
            CatKeys.KEY_RIGHT -> "Arrow Right"
            CatKeys.KEY_LEFT -> "Arrow Left"
            CatKeys.KEY_DOWN -> "Arrow Down"
            CatKeys.KEY_UP -> "Arrow Up"
            CatKeys.KEY_LEFT_SHIFT -> "LShift"
            CatKeys.KEY_RIGHT_SHIFT -> "RShift"
            CatKeys.KEY_LEFT_CONTROL -> "LCtrl"
            CatKeys.KEY_RIGHT_CONTROL -> "RCtrl"
            CatKeys.KEY_LEFT_ALT -> "LAlt"
            CatKeys.KEY_RIGHT_ALT -> "RAlt"
            in CatKeys.KEY_F1..CatKeys.KEY_F12 ->
                "F${key - CatKeys.KEY_F1 + 1}"
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