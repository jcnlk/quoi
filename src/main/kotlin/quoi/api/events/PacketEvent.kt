package quoi.api.events

import net.minecraft.network.protocol.Packet
import quoi.api.events.core.CancellableEvent
import quoi.api.events.core.Event

interface PacketEvent {
    val packet: Packet<*>

    class Received(override val packet: Packet<*>) : CancellableEvent(), PacketEvent
    class Sent(override val packet: Packet<*>) : CancellableEvent(), PacketEvent
    class ReceivedPost(override val packet: Packet<*>) : Event(), PacketEvent
}
