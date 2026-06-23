package quoi.module.impl.misc.chat.impl

import quoi.module.impl.misc.chat.Chat
import quoi.module.settings.group.ToggleableGroup

object InfiniteChatLimit : ToggleableGroup(Chat, "Infinite chat limit", desc = "Keeps all chat messages instead of trimming chat history at 100 messages.") {
    @JvmStatic
    fun keepsAllChatMessages(): Boolean {
        return running
    }
}
