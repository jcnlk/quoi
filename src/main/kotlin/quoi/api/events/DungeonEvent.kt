package quoi.api.events

import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.level.block.state.BlockState
import quoi.api.events.core.Event
import quoi.api.skyblock.dungeon.Phase
import quoi.api.skyblock.dungeon.Stage
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonRoom
import quoi.api.skyblock.dungeon.odonscanning.tiles.RoomState

abstract class DungeonEvent {
    class Start : Event()

    abstract class Secret {
        class Interact(val blockPos: BlockPos, val blockState: BlockState) : Event()
        class Item(val entity: ItemEntity) : Event()
        class Bat(val packet: ClientboundSoundPacket) : Event()
    }

    abstract class Room {
        class Enter(val room: OdonRoom?) : Event()
        class Scan(val room: OdonRoom) : Event()
        class State(val room: OdonRoom, val old: RoomState, val new: RoomState, val current: Boolean) : Event()
    }

    // TODO: rename or smth idk
    class SectionComplete(val section: Stage) : Event() {
        class Full(val section: Stage) : Event()
    }

    // TODO: maybe remove old/new and only return current stage
    class PhaseChanged(val old: Phase, val new: Phase) : Event()

    // TODO: maybe remove old/new and only return current stage
    class StageChanged(val old: Stage, val new: Stage) : Event()
}