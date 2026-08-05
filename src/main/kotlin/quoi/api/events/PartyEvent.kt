package quoi.api.events

import quoi.api.events.core.Event
import quoi.api.skyblock.dungeon.DungeonClass

abstract class PartyEvent {
    class Message(val sender: String, val content: String) : Event()

    abstract class Member {
        class Join(val name: String, val clazz: DungeonClass? = null) : Event()
        class Leave(val name: String) : Event()
    }

    abstract class Leader {
        class Change(val old: String?, val new: String?) : Event()
    }

    class Create : Event()

    class Disband : Event()
}
