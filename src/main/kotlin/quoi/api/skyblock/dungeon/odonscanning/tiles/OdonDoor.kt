package quoi.api.skyblock.dungeon.odonscanning.tiles

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import quoi.QuoiMod.mc
import quoi.api.colour.Colour
import quoi.api.colour.multiply
import quoi.api.skyblock.dungeon.odonscanning.ScanUtils
import quoi.module.impl.dungeon.DungeonMap
import quoi.api.vec.Vec2i
import quoi.utils.equalsOneOf

class OdonDoor(val pos: Vec2i, var type: DoorType) {

    var state: RoomState = RoomState.UNDISCOVERED
    var mapLocked = type.equalsOneOf(DoorType.WITHER, DoorType.BLOOD)

    val locked: Boolean
        get() {
            if (!type.equalsOneOf(DoorType.WITHER, DoorType.BLOOD)) return false
            val chunk = mc.level?.chunkSource?.getChunk(pos.x shr 4, pos.z shr 4, false)
                ?: return mapLocked && (type != DoorType.BLOOD || ScanUtils.scannedRooms.none {
                    it.data.type == RoomType.BLOOD && it.data.state.ordinal < RoomState.UNOPENED.ordinal
                })
            val block = chunk.getBlockState(BlockPos(pos.x, 69, pos.z))
            return !block.isAir && !block.`is`(Blocks.BARRIER)
        }

    val size: Vec2i get() = Vec2i(4, 4)

    val placement: Vec2i
        get() {
            val x = (pos.x + 185) shr 4
            val z = (pos.z + 185) shr 4
            val xEven = x % 2
            val zEven = z % 2
            val thicknessOffset = (16 - 4) / 2
            val xPos = (x shr 1) * 20 + xEven * 16 + (xEven xor 1) * thicknessOffset
            val zPos = (z shr 1) * 20 + zEven * 16 + (zEven xor 1) * thicknessOffset
            return Vec2i(xPos, zPos)
        }

    val colour: Colour
        get() {
            val col = when (type) {
                DoorType.BLOOD -> DungeonMap.bloodDoor
                DoorType.WITHER if (mapLocked) -> DungeonMap.witherDoor
                DoorType.ENTRANCE -> DungeonMap.entranceDoor
                else -> DungeonMap.normalDoor
            }

            return if (state == RoomState.UNDISCOVERED) {
                Colour.RGB(col.rgb.multiply(1f - DungeonMap.darkenMultiplier))
            } else col
        }

    fun updateState(col: Int) {
        if (col == 0) return

        state = when (col) {
            85, 119 -> RoomState.UNOPENED
            else -> RoomState.DISCOVERED
        }

        mapLocked = type == DoorType.BLOOD || type == DoorType.WITHER && state == RoomState.UNOPENED
    }
}
