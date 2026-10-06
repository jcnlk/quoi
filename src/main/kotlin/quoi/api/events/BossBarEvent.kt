package quoi.api.events

import net.minecraft.network.chat.Component
import quoi.api.events.core.Event

abstract class BossBarEvent {
    class Update(
        val message: String,
        val unformatted: String,
        val text: Component,
        val progress: Float,
    ) : Event()
}
