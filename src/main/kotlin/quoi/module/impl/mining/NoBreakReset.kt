package quoi.module.impl.mining

import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket
import quoi.api.events.PacketEvent
import quoi.api.events.core.on
import quoi.mixins.accessors.ItemInHandRendererAccessor
import quoi.mixins.accessors.MultiPlayerGameModeAccessor
import quoi.module.Module

object NoBreakReset : Module(
    "No Break Reset",
    desc = "Prevents held item updates from resetting block breaking progress and the equip animation."
) {
    init {
        on<PacketEvent.ReceivedPost, ClientboundContainerSetSlotPacket> {
            if (!active || mc.screen != null || packet.containerId != 0) return@on
            if (packet.slot != 36 + player.inventory.selectedSlot) return@on

            // ReceivedPost runs after the packet has updated the inventory. Use that exact
            // stack instance so ItemInHandRenderer's identity check also remains stable.
            val stack = player.mainHandItem
            (gameMode as MultiPlayerGameModeAccessor).setDestroyingItem(stack)
            (mc.entityRenderDispatcher.itemInHandRenderer as ItemInHandRendererAccessor).setMainHandItem(stack)
        }
    }
}
