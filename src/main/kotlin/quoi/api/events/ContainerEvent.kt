package quoi.api.events

import quoi.api.events.core.Event
import quoi.utils.skyblock.player.container.ContainerUtils

/**
 * Container events posted on the client thread after the menu or its items are updated
 */
sealed class ContainerEvent : Event() {
    class Open(val container: ContainerUtils.OpenContainer) : ContainerEvent()
    class Items(val container: ContainerUtils.OpenContainer) : ContainerEvent()
}
