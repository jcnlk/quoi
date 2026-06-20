package quoi.module.impl.dungeon

import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import quoi.api.events.TickEvent
import quoi.api.skyblock.Island
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.odonscanning.ScanUtils
import quoi.api.skyblock.dungeon.odonscanning.tiles.DoorType
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonDoor
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.visibleIf
import quoi.utils.WorldUtils.state
import quoi.utils.skyblock.player.interact.AuraManager
import kotlin.math.abs

object AutoDoorOpener : Module(
    "Auto Door Opener",
    desc = "Automatically opens nearby Wither and Blood doors.",
    area = Island.Dungeon
) {
    private val mode by selector(
        "Mode",
        "Triggerbot",
        listOf("Aura", "Triggerbot"),
        desc = "Aura opens nearby doors automatically. Triggerbot only opens the door you are looking at."
    )
    private val auraRange by slider("Range", 5.0, 2.0, 6.0, 0.1, desc = "Maximum distance for opening a door.")
        .visibleIf { mode.selected == "Aura" }
    private val retryDelay by slider("Retry delay", 500, 100, 2000, 50, unit = "ms", desc = "Delay between attempts to open a door.")
    private val swing by switch("Swing hand", true, desc = "Swings the hand when opening a door.")

    private val doorTypes = setOf(DoorType.WITHER, DoorType.BLOOD)
    private var lastClick = 0L

    init {
        on<TickEvent.End> {
            if (!Dungeon.inClear || Dungeon.isDead || mc.screen != null) return@on

            val now = System.currentTimeMillis()
            if (now - lastClick < retryDelay) return@on

            val lockedDoors = ScanUtils.scannedDoors.filter { it.type in doorTypes && it.locked }
            val doorPos = when (mode.selected) {
                "Aura" -> findClosestDoor(lockedDoors)
                "Triggerbot" -> findLookedAtDoor(lockedDoors)
                else -> null
            }
            if (doorPos == null) return@on

            AuraManager.interactBlock(doorPos)
            if (swing) player.swing(InteractionHand.MAIN_HAND)
            lastClick = now
        }
    }

    private fun findClosestDoor(doors: Collection<OdonDoor>): BlockPos? {
        val eyePosition = player.eyePosition
        val rangeSq = auraRange * auraRange

        return doors.asSequence()
            .map { BlockPos(it.pos.x, 69, it.pos.z) }
            .filter { eyePosition.distanceToSqr(it.center) <= rangeSq }
            .minByOrNull { eyePosition.distanceToSqr(it.center) }
    }

    private fun findLookedAtDoor(doors: Collection<OdonDoor>): BlockPos? {
        val hitResult = mc.hitResult as? BlockHitResult ?: return null
        if (hitResult.type != HitResult.Type.BLOCK) return null

        val hitPos = hitResult.blockPos

        return hitPos.takeIf {
            doors.any { door ->
                abs(hitPos.x - door.pos.x) <= 2 &&
                    abs(hitPos.z - door.pos.z) <= 2 &&
                    hitPos.y in 69..73 &&
                    when (door.type) {
                        DoorType.WITHER -> hitPos.state.block == Blocks.COAL_BLOCK
                        DoorType.BLOOD -> hitPos.state.block == Blocks.RED_TERRACOTTA
                        else -> false
                    }
            }
        }
    }
}
