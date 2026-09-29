package quoi.module.impl.misc.slayers.enderman

import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.monster.Enderman
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import quoi.api.events.PacketEvent
import quoi.api.events.RenderEvent
import quoi.api.events.SlayerEvent
import quoi.api.events.core.on
import quoi.module.impl.misc.slayers.QuestState
import quoi.module.impl.misc.slayers.Slayers
import quoi.module.settings.group.ToggleableGroup
import quoi.utils.EntityUtils.getEntities
import quoi.utils.EntityUtils.interpolatedBox

object BeaconESP : ToggleableGroup(
    EndermanSlayer,
    "Beacon ESP",
    desc = "Highlights the Voidgloom Yang Glyph and draws a tracer to it."
) {
    private val highlight get() = Slayers.beaconHighlight
    private val tracer get() = Slayers.beaconTracer

    private val placedBeacons = mutableMapOf<BlockPos, Long>()

    init {
        on<PacketEvent.Received> {
            if (Slayers.questState != QuestState.KILLING) return@on
            when (val packet = packet) {
                is ClientboundBlockUpdatePacket -> updateBeacon(packet.pos, packet.blockState.block == Blocks.BEACON)
                is ClientboundSectionBlocksUpdatePacket -> packet.runUpdates { pos, state ->
                    updateBeacon(pos, state.block == Blocks.BEACON)
                }
            }
        }

        on<SlayerEvent.State> {
            if (new != QuestState.KILLING) placedBeacons.clear()
        }

        on<RenderEvent.World> {
            if (Slayers.questState != QuestState.KILLING) return@on

            val now = System.currentTimeMillis()
            placedBeacons.entries.removeIf { now - it.value > 7_000L }

            getEntities<Enderman>()
                .filter { it.carriedBlock?.block == Blocks.BEACON }
                .forEach { entity ->
                    highlight.draw(ctx, entity.interpolatedBox)
                    tracer.draw(ctx, entity)
                }

            getEntities<ArmorStand>()
                .filter { it.getItemBySlot(EquipmentSlot.HEAD).item == Items.BEACON }
                .forEach { entity ->
                    highlight.draw(ctx, entity.interpolatedBox)
                    tracer.draw(ctx, entity)
                }

            placedBeacons.keys.forEach { pos ->
                highlight.draw(ctx, AABB(pos))
                tracer.draw(ctx, Vec3.atCenterOf(pos))
            }
        }
    }

    private fun updateBeacon(pos: BlockPos, isBeacon: Boolean) {
        val immutable = pos.immutable()
        if (isBeacon) placedBeacons[immutable] = System.currentTimeMillis()
        else placedBeacons.remove(immutable)
    }

    override fun onDisable() {
        placedBeacons.clear()
        super.onDisable()
    }
}
