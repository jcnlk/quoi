package quoi.api.events

import quoi.api.events.core.Event
import quoi.api.skyblock.SkyblockPlayer.InvincibilityType

// maybe this is a good idea, or it isn't, let's fine out!
abstract class SkyblockPlayerEvent { // TODO: add more sb player related events or whatever
    abstract class Invincibility {
        class Proc(val type: InvincibilityType) : Event()
    }
}
