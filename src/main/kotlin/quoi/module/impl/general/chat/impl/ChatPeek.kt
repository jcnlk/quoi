package quoi.module.impl.general.chat.impl

import quoi.api.input.Keybinds
import quoi.api.input.CatKeys
import quoi.module.impl.general.chat.Chat
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
        mc.gui.hud.chat.scrollChat(if (Keybinds.Modifier.isShiftDown) amount else amount * 7)
    }
}
