package quoi.module.impl.dungeon.secrets.impl

import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket
import quoi.api.events.PacketEvent
import quoi.api.events.core.on
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.location.Island
import quoi.api.skyblock.location.invoke
import quoi.module.impl.dungeon.secrets.Secrets
import quoi.module.settings.group.ToggleableGroup

object AutoCloseChest : ToggleableGroup(
    Secrets,
    "Auto close chest",
    desc = "Automatically closes secret chests.",
    area = Island.Dungeon(inClear = true)
) {
    init {
        on<PacketEvent.Received, ClientboundOpenScreenPacket> {
            if (!Dungeon.isSecretChestMenu(packet)) return@on
            connection.send(ServerboundContainerClosePacket(packet.containerId))
            cancel()
        }
    }
}
