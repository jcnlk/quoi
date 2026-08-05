package quoi.module.impl.general.chat.impl

import quoi.module.impl.general.chat.Chat
import quoi.module.settings.group.ToggleableGroup

/**
 * TODO:
 *  allow to set non-infinite limits
 */

object InfiniteChatLimit : ToggleableGroup(Chat, "Infinite chat limit", desc = "Keeps all chat messages instead of trimming chat history at 100 messages.") {
    @JvmStatic
    fun keepsAllChatMessages(): Boolean {
        return running
    }
}
