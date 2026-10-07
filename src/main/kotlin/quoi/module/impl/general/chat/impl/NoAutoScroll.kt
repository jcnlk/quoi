package quoi.module.impl.general.chat.impl

import quoi.module.impl.general.chat.Chat
import quoi.module.settings.group.ToggleableGroup

object NoAutoScroll : ToggleableGroup(
    Chat,
    "Disable auto scroll",
    desc = "Prevents chat from scrolling to the latest message when a new message arrives.",
) {
    @JvmStatic
    fun disablesAutoScroll(): Boolean = running
}
