package quoi.module.impl.misc.chat.impl

import quoi.api.input.CatKeyboard
import quoi.api.input.CatKeys
import quoi.module.impl.misc.chat.Chat
import quoi.module.settings.group.ToggleableGroup

object ChatPeek : ToggleableGroup(Chat, "Chat peek", desc = "Peeks chat on a button press.") {
    private val peekKey by keybind("Peek key", CatKeys.KEY_Z)
        .onRelease {
            if (running) scroll(-Int.MAX_VALUE)
        }

    @JvmStatic
    fun isDown(): Boolean {
        return running && this.peekKey.isDown()
    }

    @JvmStatic
    fun scroll(amount: Int) {
        mc.gui.hud.chat.scrollChat(if (CatKeyboard.Modifier.isShiftDown) amount else amount * 7)
    }
}
