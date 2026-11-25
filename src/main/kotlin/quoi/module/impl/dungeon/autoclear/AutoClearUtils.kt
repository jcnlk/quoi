package quoi.module.impl.dungeon.autoclear

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import quoi.api.colour.Colour
import quoi.api.pathfinding.PathConfig
import quoi.api.pathfinding.TeleportPathNode
import quoi.api.pathfinding.impl.DungeonMapPathfinder
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.odonscanning.ScanUtils
import quoi.api.skyblock.dungeon.odonscanning.tiles.DoorType
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonDoor
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonRoom
import quoi.api.skyblock.dungeon.odonscanning.tiles.RoomTile
import quoi.api.skyblock.dungeon.odonscanning.tiles.Rotations
import quoi.module.impl.dungeon.autoclear.executor.ClearExecutor
import quoi.module.impl.dungeon.autoclear.executor.nodes.ClearEtherNode
import quoi.module.impl.dungeon.autoclear.impl.AutoRoutes.routeNodes
import quoi.module.impl.dungeon.autoclear.impl.InteractiveMap
import quoi.utils.ChatUtils.modMessage
import quoi.utils.StringUtils.containsOneOf
import quoi.utils.WorldUtils.etherwarpable
import quoi.utils.WorldUtils.firstNearbyBlock
import quoi.utils.addVec
import quoi.utils.distanceToSqr
import quoi.utils.equalsOneOf
import quoi.utils.floorPos
import quoi.utils.getDirection
import quoi.utils.getLook
import quoi.utils.player
import quoi.utils.rayCastVec
import quoi.utils.skyblock.player.PlayerUtils.getEyeHeight
import quoi.utils.skyblock.player.RotationUtils.rotate
import quoi.utils.ui.notifications.Notifications

const val HYPE_AOE = 6.0

private val roomOverrides = mapOf(
    "Creeper Beams" to BlockPos(15, 68, 5),
    "Three Weirdos" to BlockPos(15, 68, 22),
    "Water Board" to BlockPos(15, 58, 9),
    "Ice Path" to BlockPos(10, 67, 8),
    "Tic Tac Toe" to BlockPos(11, 68, 16),
    "Ice Fill" to BlockPos(15, 69, 7),
    "Quiz" to BlockPos(15, 68, 5),
    "Boulder" to BlockPos(15, 68, -2),
    "Teleport Maze" to BlockPos(15, 68, -2),
    "Old Trap" to BlockPos(15, 68, -2),
    "New Trap" to BlockPos(15, 68, -2),
    "Cages" to BlockPos(15, 64, 16)
)

private val coreOverrides = mapOf(
    "Gold" to mapOf(
        35550104 to BlockPos(5, 68, 15),
        992885012 to BlockPos(55, 68, 15)
    ),
    "Layers" to mapOf(
        161195688 to BlockPos(53, 68, 53)
    ),
    "Mage" to mapOf(
        925853313 to BlockPos(15, 75, 15)
    ),
    "Deathmite" to mapOf(
        706341009 to BlockPos(5, 68, 15)
    ),
    "Dragon" to mapOf(
        -1334473473 to BlockPos(15, 68, 18)
    )
)

private fun pathBlockedReason(): String? {
    if (!player.onGround()) return "Land on the ground before travelling."
    val room = Dungeon.currentRoom ?: return null
    if (room.name.containsOneOf("Maze", "Boulder") ||
        room.name.contains("Trap") && room.getRelativeCoords(player.blockPosition()).z >= 0
    ) return "Move out of ${room.name} before travelling."
    return null
}

/**
 * Searches for the nearest locked Wither or Blood door in the dungeon.
 *
 * @return The closest locked [OdonDoor] based on pathfinding distance, or null if none found.
 */
fun getLockedDoor(): OdonDoor? {
    val room = Dungeon.currentRoom ?: return null
    return ScanUtils.scannedDoors
        .filter { it.locked && it.type.equalsOneOf(DoorType.WITHER, DoorType.BLOOD) }
        .map { it to DungeonMapPathfinder.getDistToDoor(room, it) }
        .filter { it.second != Int.MAX_VALUE }
        .minWithOrNull(
            compareBy<Pair<OdonDoor, Int>> { it.second }
                .thenBy { player.position().distanceToSqr(Vec3(it.first.pos.x + 0.5, 69.0, it.first.pos.z + 0.5)) }
        )?.first
}

/**
 * Starts an etherwarp path towards the specified [door]
 *
 * @param door Destination door
 */
fun pathToDoor(door: OdonDoor, faceOnArrival: Boolean = false, owner: Any? = null, mapFeedback: Boolean = false) {
    pathBlockedReason()?.let {
        if (mapFeedback) Notifications.warning(InteractiveMap.name, it)
        return
    }
    val room = Dungeon.currentRoom ?: run {
        if (mapFeedback) Notifications.warning(InteractiveMap.name, "Your current room hasn't been detected yet.")
        return
    }
    val goal = DungeonMapPathfinder.getDoorPos(room, door) ?: run {
        if (mapFeedback) Notifications.error(InteractiveMap.name, "Couldn't find a way to the selected door.")
        else modMessage("Could not find door coordinates")
        return
    }

    val onArrival = if (faceOnArrival) {
        {
            val doorCentre = Vec3(door.pos.x + 0.5, 71.0, door.pos.z + 0.5)
            player.rotate(getDirection(doorCentre))
        }
    } else null

    if (mapFeedback) pathToMapTarget(goal, "the selected door", onArrival = onArrival)
    else ClearExecutor.etherPath(to = goal, owner = owner, onComplete = onArrival)
}

/**
 * Navigates to a specific [room] based on target [type]
 *
 * @param room Destination room.
 * @param tile Specific room tile.
 * @param type Navigation type: 0 for room entry, 1 for an autoroute start, 2 for room entry followed by an autoroute start.
 */
fun pathToRoom(room: OdonRoom, tile: RoomTile? = null, type: Int) {
    if (type !in 0..2) return
    pathBlockedReason()?.let { return Notifications.warning(InteractiveMap.name, it) }
    val targetTile = tile ?: room.tiles.firstOrNull()
        ?: return Notifications.warning(InteractiveMap.name, "${room.name} hasn't been mapped yet.")

    val enterRoomFirst = type == 2 && Dungeon.currentRoom != room
    if (type == 0 || enterRoomFirst) {
        val overridePos = roomOverrides[room.name] ?: coreOverrides[room.name]?.get(targetTile.core)
        val goal = if (overridePos != null && room.rotation != Rotations.NONE) {
            room.getRealCoords(overridePos)
        } else {
            targetTile.blockPos.firstNearbyBlock(25f) { it.etherwarpable }
                ?: return Notifications.error(InteractiveMap.name, "Couldn't find an Etherwarp landing spot in ${room.name}.")
        }

        val nextLeg: (() -> Unit)? = if (enterRoomFirst) {
            {
                if (Dungeon.currentRoom == room) pathToRoom(room, targetTile, type = 1)
                else Notifications.error(InteractiveMap.name, "Couldn't reach ${room.name}. Travel to the route start was cancelled.")
            }
        } else null
        pathToMapTarget(goal, room.name, onArrival = nextLeg)
        return
    }

    if (!room.ready) return Notifications.warning(InteractiveMap.name, "Room data for ${room.name} is still loading. Enter the room first.")
    val rings = routeNodes[room.name].orEmpty()
    val starts = rings.filter { it.start == true }
    if (starts.isEmpty()) return Notifications.warning(InteractiveMap.name, "No route start is saved for ${room.name}.")

    val landingSpots = starts.map { room.getRealCoords(it.relative.floorPos) }.filter { it.etherwarpable }
    val goal = landingSpots.find { it.distanceToSqr(targetTile.blockPos) < 225 } ?: landingSpots.firstOrNull()
        ?: return Notifications.error(InteractiveMap.name, "No route start in ${room.name} can be reached with Etherwarp.")

    pathToMapTarget(goal, "the route start in ${room.name}")
}

private fun pathToMapTarget(goal: BlockPos, target: String, onArrival: (() -> Unit)? = null) {
    ClearExecutor.etherPath(
        to = goal,
        config = PathConfig(feedback = false),
        onAlreadyThere = { Notifications.info(InteractiveMap.name, "You're already at the destination.", duration = 2000L) },
        onFailure = { reason -> Notifications.error(InteractiveMap.name, "Couldn't travel to $target. $reason") },
        onComplete = onArrival,
    )
}

fun TeleportPathNode.toEther(): ClearEtherNode {
    val from = vec.addVec(y = getEyeHeight(true))
    val to = getLook(yaw, pitch).scale(60.0)
    val target = rayCastVec(from, to).vec
    return ClearEtherNode(vec, yaw, pitch, Colour.ORANGE, listOf(from, target))
}
