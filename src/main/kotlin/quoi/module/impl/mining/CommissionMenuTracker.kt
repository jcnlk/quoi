package quoi.module.impl.mining

import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import net.minecraft.world.item.ItemStack
import quoi.utils.StringUtils.noControlCodes

/** Maintains a server-backed menu snapshot and coalesces all updates received during one client tick. */
internal class CommissionMenuTracker(private val title: String) {
    private var containerId: Int? = null
    private val items = mutableListOf<ItemStack>()
    private var pending = false
    private var fullUpdate = false

    fun handle(packet: Packet<*>) {
        when (packet) {
            is ClientboundOpenScreenPacket -> if (packet.title.string.noControlCodes.equals(title, ignoreCase = true)) {
                containerId = packet.containerId
                items.clear()
                pending = false
                fullUpdate = false
            }
            is ClientboundContainerSetContentPacket -> if (packet.containerId == containerId) {
                items.clear()
                items.addAll(packet.items)
                pending = true
                fullUpdate = true
            }
            is ClientboundContainerSetSlotPacket -> if (packet.containerId == containerId && packet.slot >= 0) {
                while (items.size <= packet.slot) items += ItemStack.EMPTY
                items[packet.slot] = packet.item
                pending = true
            }
            is ClientboundContainerClosePacket -> if (packet.containerId == containerId) containerId = null
        }
    }

    fun consume(): Snapshot? {
        if (!pending) return null
        pending = false
        return Snapshot(items.toList(), fullUpdate).also { fullUpdate = false }
    }

    fun reset() {
        containerId = null
        items.clear()
        pending = false
        fullUpdate = false
    }

    internal data class Snapshot(val items: List<ItemStack>, val fullUpdate: Boolean)
}
