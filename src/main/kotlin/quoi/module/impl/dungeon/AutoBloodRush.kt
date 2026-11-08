package quoi.module.impl.dungeon

import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket
import net.minecraft.world.phys.Vec3
import quoi.api.events.PacketEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.Event
import quoi.api.events.core.on
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonDoor
import quoi.api.skyblock.location.Island
import quoi.module.Module
import quoi.module.impl.dungeon.autoclear.executor.ClearExecutor
import quoi.module.impl.dungeon.autoclear.getLockedDoor
import quoi.module.impl.dungeon.autoclear.impl.InteractiveMap
import quoi.module.impl.dungeon.autoclear.pathToDoor
import quoi.utils.skyblock.player.interact.AuraManager

object AutoBloodRush : Module(
    "Auto Blood Rush",
    desc = "Travels to locked Wither and Blood doors and opens them automatically.", // TODO: change this
    area = Island.Dungeon
) {
    private val mode by selector("Mode", "Doorcamp", listOf("Doorcamp"), desc = "Doorcamp travels to the closest locked door and auras it until it opens.") // TODO: make this enums
    private val auraRange by slider("Aura range", 5.0, 3.0, 6.0, 0.1, desc = "Maximum distance for opening the targeted door.")
    private val retryDelay by slider("Retry delay", 500, 100, 2000, 50, unit = "ms", desc = "Delay between attempts to open the targeted door.")

    private var targetDoor: OdonDoor? = null
    private var lastPath = 0L
    private var lastClick = 0L

    init {
        on<TickEvent.End> {
            if (!Dungeon.hasStarted || !Dungeon.inClear || Dungeon.isDead || mc.gui.screen() != null && !InteractiveMap.mapOpen || mode.selected != "Doorcamp") {
                reset()
                return@on
            }

            if (targetDoor?.locked == false) reset()
            if (ClearExecutor.routing) return@on

            val door = targetDoor ?: getLockedDoor()?.also { targetDoor = it } ?: return@on
            val doorPos = BlockPos(door.pos.x, 69, door.pos.z)
            val now = System.currentTimeMillis()

            if (player.eyePosition.distanceToSqr(Vec3.atCenterOf(doorPos)) <= auraRange * auraRange) {
                if (AutoDoorOpener.enabled || now - lastClick < retryDelay) return@on
                AuraManager.interactBlock(doorPos)
                lastClick = now
            } else if (now - lastPath >= 1000) {
                pathToDoor(door, owner = this@AutoBloodRush)
                lastPath = now
            }
        }

        on<PacketEvent.Received, ClientboundForgetLevelChunkPacket> {
            if (Dungeon.hasStarted && Dungeon.inClear && !Dungeon.isDead) cancel()
        }

        on<WorldEvent.Change> { reset() }
    }

    override fun onDisable() {
        reset()
    }

    override fun shouldHandle(event: Event): Boolean = event is WorldEvent.Change || super.shouldHandle(event)

    private fun reset() {
        if (ClearExecutor.owner === this) ClearExecutor.cancel()
        targetDoor = null
        lastPath = 0L
        lastClick = 0L
    }
}
