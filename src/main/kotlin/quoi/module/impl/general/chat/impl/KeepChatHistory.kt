package quoi.module.impl.general.chat.impl

import quoi.module.impl.general.chat.Chat
import quoi.module.settings.group.ToggleableGroup

object KeepChatHistory : ToggleableGroup(Chat, "Keep history", desc = "Keeps chat history when vanilla tries to clear it on disconnect.") {
    @JvmStatic
    fun keepsChatHistory(): Boolean {
        return running
    }
}
