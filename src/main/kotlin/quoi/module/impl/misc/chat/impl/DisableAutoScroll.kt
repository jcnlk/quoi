package quoi.module.impl.misc.chat.impl

import quoi.module.impl.misc.chat.Chat
import quoi.module.settings.group.ToggleableGroup

object DisableAutoScroll : ToggleableGroup(Chat, "Disable auto scroll", desc = "Prevents chat from scrolling to the latest message when a new message arrives.") {
    @JvmStatic
    fun disablesAutoScroll(): Boolean {
        return running
    }
}
