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
            if (!active || mc.gui.screen() != null) return@on
            val player = mc.player ?: return@on
            val gameMode = mc.gameMode ?: return@on

            val slot = packet.slot
            if (slot !in 36..44 || player.inventory.selectedSlot != slot - 36) return@on

            (gameMode as MultiPlayerGameModeAccessor).setDestroyingItem(packet.item)
            (player.firstPersonHandsAndItems() as ItemInHandRendererAccessor).setMainHandItem(packet.item)
        }
    }
}
