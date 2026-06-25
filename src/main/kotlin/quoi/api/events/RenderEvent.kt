package quoi.api.events

import quoi.api.events.core.CancellableEvent
import quoi.api.events.core.Event
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.minecraft.client.DeltaTracker
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.world.entity.Entity as McEntity

abstract class RenderEvent {
    class World(val ctx: WorldRenderContext) : Event()
    class Overlay(val ctx: GuiGraphics, val tickCounter: DeltaTracker) : Event()
    class Entity(val entity: McEntity) : CancellableEvent()
}