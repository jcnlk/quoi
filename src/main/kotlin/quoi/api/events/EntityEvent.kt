package quoi.api.events

import quoi.api.colour.Colour
import quoi.api.events.core.CancellableEvent
import quoi.api.events.core.Event
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.decoration.ArmorStand

abstract class EntityEvent {
    class Attack(val entity: Entity) : CancellableEvent()
    class Pick(val entity: Entity) : CancellableEvent()
    class Spawn(val entity: Entity) : Event()
    class ArmorStandHeadEquipmentUpdate(val entity: ArmorStand) : Event()
    class ForceGlow(val entity: Entity) : CancellableEvent() {
        var overlayColour: Colour? = null
        var overlayDepth: Boolean = false
        var isGlowing: Boolean = false
        var glowColour: Colour = Colour.WHITE
            set(value) {
                isGlowing = true
                field = value
            }
    }
}
