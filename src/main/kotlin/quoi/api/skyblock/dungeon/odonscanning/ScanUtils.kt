package quoi.api.skyblock.dungeon.odonscanning

import com.google.common.reflect.TypeToken
import com.google.gson.GsonBuilder
import kotlinx.io.files.FileNotFoundException
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.chunk.LevelChunk
import quoi.QuoiMod.logger
import quoi.api.events.DungeonEvent
import quoi.api.events.PacketEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.EventListener
import quoi.api.events.core.on
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.odonscanning.tiles.*
import quoi.api.skyblock.location.Island
import quoi.api.skyblock.location.Location
import quoi.api.vec.Vec2i
import quoi.utils.Shortcuts
import quoi.utils.WorldUtils.getBlockEntityList
import quoi.utils.WorldUtils.state
import quoi.utils.equalsOneOf
import kotlin.math.floor

/**
 * modified Odin (BSD 3-Clause)
 * copyright (c) 2025 odtheking
 * original: https://github.com/odtheking/Odin/blob/main/src/main/kotlin/com/odtheking/odin/features/impl/dungeon/map/WorldScan.kt
 *           https://github.com/odtheking/Odin/blob/main/src/main/kotlin/com/odtheking/odin/features/impl/dungeon/map/tile/DungeonRoom.kt
 *
 * modified NoammAddons (CC0-1.0)
 * original: https://github.com/Noamm9/NoammAddons/blob/master/src/main/kotlin/com/github/noamm9/utils/dungeons/map/handlers/DungeonScanner.kt
 *           https://github.com/Noamm9/NoammAddons/blob/master/src/main/kotlin/com/github/noamm9/utils/dungeons/map/core/UniqueRoom.kt
 */
object ScanUtils : EventListener, Shortcuts {
    const val START = -185

    private val roomList: Set<RoomData> = loadRoomData()
    val coreToRoomData: Map<Int, RoomData> =
        roomList.flatMap { room -> room.cores.map { core -> core to room } }.toMap()

    private val mutableBlockPos = BlockPos.MutableBlockPos()
    private val horizontals = Direction.entries.filter { it.axis.isHorizontal }
    private var lastScanTime = 0L

    val grid = Array<Any?>(121) { null }
    private val uniqueRooms = mutableMapOf<String, OdonRoom>()

    var currentRoom: OdonRoom? = null
        private set
    val passedRooms: MutableSet<OdonRoom> = mutableSetOf()
    val scannedRooms: MutableSet<OdonRoom> = mutableSetOf()
    val scannedDoors: MutableSet<OdonDoor> = mutableSetOf()

    var mimicRoom: OdonRoom? = null
        private set

    private fun loadRoomData(): Set<RoomData> {
        return try {
            GsonBuilder()
                .registerTypeAdapter(RoomData::class.java, RoomDataDeserializer())
                .create().fromJson(
                    (ScanUtils::class.java.getResourceAsStream("/assets/quoi/odon_rooms.json")
                        ?: throw FileNotFoundException()).bufferedReader(),
                    object : TypeToken<Set<RoomData>>() {}.type
                )
        } catch (e: Exception) {
            logger.error("Error reading room data", e)
            setOf()
        }
    }

    fun init() {
        on<TickEvent.End> {
            if ((!Dungeon.inDungeons && !Location.currentArea.isArea(Island.SinglePlayer)) || Dungeon.inBoss) {
                currentRoom?.let { DungeonEvent.Room.Enter(null).post() }
                return@on
            } // We want the current room to register as null if we are not in a dungeon

            scanDungeon()

            scannedRooms.filter { it.identified && it.rotation == Rotations.NONE }.forEach { it.updateRotation() }

            val room = getRoomFromPos(player.blockX, player.blockZ)?.takeIf { it.ready }
            if (room !== currentRoom) DungeonEvent.Room.Enter(room).post()
        }

        on<DungeonEvent.Room.Enter> {
            currentRoom = room
            room?.let { passedRooms.add(it) }
        }

        on<DungeonEvent.Room.Scan> {
            MapRenderer.refresh()
        }

        on<DungeonEvent.Room.State> {
            MapRenderer.refresh()
        }

        on<DungeonEvent.Enter> { MapScan.initFloor(floor.floorNumber) }

        on<PacketEvent.ReceivedPost, ClientboundMapItemDataPacket> {
            if (Dungeon.inClear) MapScan.update(packet)
        }

        on<WorldEvent.Chunk.Load> {
            MapScan.initFloor()
            if (Dungeon.inBoss || !Dungeon.inDungeons && !Location.currentArea.isArea(Island.SinglePlayer)) return@on
            val x = chunk.pos.x + 12
            val z = chunk.pos.z + 12
            if (x in 0..10 && z in 0..10) scanGridTile(x, z, chunk)
        }

        on<WorldEvent.Change> {
            passedRooms.clear()
            scannedRooms.clear()
            scannedDoors.clear()
            uniqueRooms.clear()
            grid.fill(null)
            currentRoom = null
            mimicRoom = null
            lastScanTime = 0L
            MapScan.reset()
            MapRenderer.refresh()
        }
    }

    private fun scanDungeon() {
        if (System.currentTimeMillis() - lastScanTime < 250) return
        lastScanTime = System.currentTimeMillis()

        for (x in 0..10) {
            for (z in 0..10) {
                val wx = START + x * 16
                val wz = START + z * 16
                val chunk = level.chunkSource.getChunk(wx shr 4, wz shr 4, false) ?: continue
                scanGridTile(x, z, chunk)
            }
        }
        if (mimicRoom == null && !Dungeon.mimicKilled && (Dungeon.floor?.floorNumber ?: -1) > 5) scanMimic()
    }

    private fun scanGridTile(x: Int, z: Int, chunk: LevelChunk) {
        val index = z * 11 + x
        val wx = START + x * 16
        val wz = START + z * 16
        val existing = grid[index]
        if (existing != null) {
            if (x % 2 != 0 || z % 2 != 0 || existing !is OdonRoom) return
            if (existing.tiles.any { it.x == wx && it.z == wz && it.core != 0 }) return
        }
        val height = getTopLayerOfRoom(Vec2i(wx, wz), chunk)
        if (height <= 0) return
        scanTile(wx, wz, x, z, height, chunk)?.let { grid[index] = it }
    }

    private fun scanTile(x: Int, z: Int, col: Int, row: Int, height: Int, chunk: LevelChunk): Any? {
        val rowEven = row % 2 == 0
        val colEven = col % 2 == 0

        return when {
            rowEven && colEven -> { // rooms
                if (height <= 0) return null
                val position = Vec2i(x, z)
                val legacyCore = getRoomCore(position, chunk, height)
                val core = legacyCore.takeIf { it in coreToRoomData } ?: getRoomCore(position, chunk)
                val data = coreToRoomData[core] ?: return null
                addWorldRoom(x, z, data, core, height)
            }

            !rowEven && !colEven -> { // 2x2 centres
                val tile = grid[(row - 1) * 11 + col - 1] as? OdonRoom
                tile?.takeIf { it.data.shape == "2x2" }
            }

            height in intArrayOf(73, 74, 81, 82) -> { // dors
                val block = chunk.level.getBlockState(mutableBlockPos.set(x, 69, z)).block
                val type = when (block) {
                    Blocks.COAL_BLOCK -> DoorType.WITHER
                    Blocks.RED_TERRACOTTA -> DoorType.BLOOD
                    Blocks.INFESTED_CHISELED_STONE_BRICKS -> DoorType.ENTRANCE
                    else -> DoorType.NORMAL
                }
                val door = OdonDoor(Vec2i(x, z), type)
                scannedDoors.add(door)
                MapRenderer.refresh()
                door
            }

            else -> { // connection between big rooms
                val i = if (rowEven) row * 11 + (col - 1) else (row - 1) * 11 + col
                if (i in 0..120) {
                    val tile = grid[i]
                    if (tile is OdonRoom) {
                        if (tile.data.type == RoomType.ENTRANCE) {
                            val door = OdonDoor(Vec2i(x, z), DoorType.NORMAL)
                            scannedDoors.add(door)
                            MapRenderer.refresh()
                            door
                        } else {
                            tile
                        }
                    } else null
                } else null
            }
        }
    }

    internal fun addWorldRoom(x: Int, z: Int, data: RoomData, core: Int, height: Int): OdonRoom {
        val index = ((z - START) / 16) * 11 + (x - START) / 16
        val existing = grid[index] as? OdonRoom
        val room = uniqueRooms.getOrPut(data.name) {
            existing?.takeUnless { it.identified } ?: OdonRoom(data = data.copy(), tiles = mutableSetOf()).also { scannedRooms.add(it) }
        }
        if (existing != null && existing !== room) mergeRooms(room, existing)
        if (!room.identified) room.data = data.copy(state = room.data.state)
        room.highestBlock = height

        val added = room.tiles.none { it.x == x && it.z == z }
        room.tiles.removeAll { it.x == x && it.z == z }
        room.tiles.add(RoomTile(x, z, core))
        if (added) room.rotation = Rotations.NONE
        grid[index] = room
        if (room.rotation == Rotations.NONE) updateRotation(room, height)
        DungeonEvent.Room.Scan(room).post()
        return room
    }

    internal fun mergeRooms(room: OdonRoom, other: OdonRoom) {
        for (tile in other.tiles) {
            val existing = room.tiles.find { it.x == tile.x && it.z == tile.z }
            if (existing == null || existing.core == 0 && tile.core != 0) {
                room.tiles.remove(existing)
                room.tiles.add(tile)
                room.rotation = Rotations.NONE
            }
        }
        if (other.data.state.ordinal < room.data.state.ordinal) room.data.state = other.data.state
        room.secretsFound = listOfNotNull(room.secretsFound, other.secretsFound).maxOrNull()
        if (room.highestBlock == null) room.highestBlock = other.highestBlock
        grid.indices.filter { grid[it] === other }.forEach { grid[it] = room }
        uniqueRooms.entries.filter { it.value === other }.forEach { it.setValue(room) }
        scannedRooms.remove(other)
        if (passedRooms.remove(other)) passedRooms.add(room)
        if (mimicRoom === other) mimicRoom = room
        if (currentRoom === other) DungeonEvent.Room.Enter(room.takeIf { it.ready }).post()
    }

    internal fun addMapRoom(indices: List<Int>, type: RoomType): OdonRoom {
        var changed = false
        val existing = indices.mapNotNull { grid[it] as? OdonRoom }.distinct()
        val room = existing.firstOrNull { it.identified } ?: existing.firstOrNull()
        ?: OdonRoom(data = RoomData("Unknown", type, emptyList(), 0, 0, 0), tiles = mutableSetOf()).also {
            scannedRooms.add(it)
            changed = true
        }
        existing.filter { it !== room }.forEach {
            mergeRooms(room, it)
            changed = true
        }
        if (!room.identified && room.data.type != type) {
            room.data = room.data.copy(type = type)
        }
        for (index in indices) {
            val x = START + index % 11 * 16
            val z = START + index / 11 * 16
            if (room.tiles.none { it.x == x && it.z == z }) {
                room.tiles.add(RoomTile(x, z))
                room.rotation = Rotations.NONE
                changed = true
            }
            grid[index] = room
        }
        if (room.rotation == Rotations.NONE) room.highestBlock?.let { updateRotation(room, it) }
        if (changed) MapRenderer.refresh()
        return room
    }

    internal fun addMapDoor(index: Int, colour: Int) {
        val type = when (colour) {
            119 -> DoorType.WITHER
            18 -> DoorType.BLOOD
            30 -> DoorType.ENTRANCE
            82 -> DoorType.FAIRY
            else -> DoorType.NORMAL
        }
        val door = (grid[index] as? OdonDoor) ?: OdonDoor(Vec2i(START + index % 11 * 16, START + index / 11 * 16), type).also {
            grid[index] = it
            scannedDoors.add(it)
            MapRenderer.refresh()
        }
        door.type = type
        door.updateState(colour)
    }

    fun scanMimic() {
        val chests = mutableMapOf<OdonRoom, Int>()
        for (pos in getBlockEntityList()) {
            if (!pos.state.`is`(Blocks.TRAPPED_CHEST)) continue
            val room = getRoomFromPos(pos.x, pos.z) ?: continue
            if (!room.identified || room.data.type == RoomType.TRAP) continue
            val count = chests.getOrDefault(room, 0) + 1
            chests[room] = count
            if (count > room.data.trappedChests) {
                mimicRoom = room
                return
            }
        }
    }

    fun getRoomFromPos(x: Number, z: Number): OdonRoom? {
        val gx = floor((x.toDouble() + 201) / 32).toInt()
        val gz = floor((z.toDouble() + 201) / 32).toInt()
        if (gx !in 0..5 || gz !in 0..5) return null
        return grid[gz * 22 + gx * 2] as? OdonRoom
    }

    fun updateRotation(room: OdonRoom, roomHeight: Int) {
        if (!room.identified) return
        val tileCount = when (room.data.shape) {
            "1x2" -> 2
            "1x3", "L" -> 3
            "1x4", "2x2" -> 4
            else -> 1
        }
        if (room.tiles.size != tileCount) return
        val topLeft = room.tiles.minBy { it.x * 1000 + it.z }

        if (room.data.name == "Fairy") {
            room.clayPos = BlockPos(topLeft.x - 15, roomHeight, topLeft.z - 15)
            room.rotation = Rotations.SOUTH
            return
        }
        val minX = room.tiles.minOf { it.x } - 15
        val minZ = room.tiles.minOf { it.z } - 15
        val maxX = room.tiles.maxOf { it.x } + 15
        val maxZ = room.tiles.maxOf { it.z } + 15
        for (rotation in Rotations.entries.dropLast(1)) {
            val candidates = if (room.data.shape == "L") {
                room.tiles.map { BlockPos(it.x + rotation.x, roomHeight, it.z + rotation.z) }
            } else {
                listOf(BlockPos(if (rotation.x < 0) minX else maxX, roomHeight, if (rotation.z < 0) minZ else maxZ))
            }
            for (pos in candidates) {
                val chunk = level.chunkSource.getChunk(pos.x shr 4, pos.z shr 4, false) ?: continue
                if (!chunk.getBlockState(pos).`is`(Blocks.BLUE_TERRACOTTA)) continue
                if (room.tiles.size > 1 && horizontals.any {
                        !chunk.getBlockState(pos.relative(it)).block.equalsOneOf(Blocks.AIR, Blocks.BLUE_TERRACOTTA)
                    }) continue
                room.clayPos = pos
                room.rotation = rotation
                return
            }
        }
    }

    fun OdonRoom.updateRotation() {
        highestBlock?.let { updateRotation(this, it) }
        if (rotation != Rotations.NONE) return
        for (tile in tiles) {
            if (tile.core == 0) continue
            val chunk = level.chunkSource.getChunk(tile.x shr 4, tile.z shr 4, false) ?: continue
            val height = getTopLayerOfRoom(Vec2i(tile.x, tile.z), chunk)
            if (height <= 0 || height == highestBlock) continue
            updateRotation(this, height)
            if (rotation != Rotations.NONE) {
                highestBlock = height
                return
            }
        }
    }

    private fun getRoomCore(vec2: Vec2i, chunk: LevelChunk, roomHeight: Int? = null): Int {
        val sb = StringBuilder(1024)
        val top = roomHeight?.coerceIn(11..140) ?: 140
        repeat(140 - top) { sb.append('0') }
        var foundHighest = roomHeight != null
        var bedrock = 0
        for (y in top downTo 12) {
            val state = chunk.getBlockState(mutableBlockPos.set(vec2.x, y, vec2.z))
            val block = state.block
            if (!foundHighest) {
                if (!state.isAir && block != Blocks.GOLD_BLOCK) foundHighest = true
                else {
                    sb.append('0')
                    continue
                }
            }
            if (block == Blocks.AIR && bedrock >= 2 && y < 69) {
                repeat(y - 11) { sb.append('0') }
                break
            }
            if (block == Blocks.BEDROCK) bedrock++
            else {
                bedrock = 0
                if (block.equalsOneOf(Blocks.OAK_PLANKS, Blocks.TRAPPED_CHEST, Blocks.CHEST)) continue
            }
            sb.append(block)
        }
        return sb.toString().hashCode()
    }

    fun getTopLayerOfRoom(vec2: Vec2i, chunk: LevelChunk): Int {
        for (y in 160 downTo 12) {
            mutableBlockPos.set(vec2.x, y, vec2.z)
            val blockState = chunk.getBlockState(mutableBlockPos)
            if (!blockState.isAir) return if (blockState.block == Blocks.GOLD_BLOCK) y - 1 else y
        }
        return 0
    }
}
