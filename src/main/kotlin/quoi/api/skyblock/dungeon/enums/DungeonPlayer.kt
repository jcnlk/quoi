package quoi.api.skyblock.dungeon.enums

import net.minecraft.util.Mth
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.player.PlayerSkin
import quoi.api.colour.Colour
import quoi.api.skyblock.dungeon.odonscanning.MapScan
import quoi.api.vec.Vec2i
import quoi.utils.EntityUtils.playerEntities

/**
 * from OdinFabric (BSD 3-Clause)
 * copyright (c) 2025-2026 odtheking
 * original: https://github.com/odtheking/OdinFabric/blob/main/src/main/kotlin/com/odtheking/odin/utils/skyblock/dungeon/DungeonEnums.kt
 */

/**
 * Data class representing a player in a dungeon, including their name, class, skin location, and associated player entity.
 *
 * @property name The name of the player.
 * @property clazz The player's class, defined by the [DungeonClass] enum.
 * @property playerSkin The resource location of the player's skin.
 * @property isDead The player's death status. Defaults to `false`.
 */
data class DungeonPlayer(
    val name: String,
    val clazz: DungeonClass,
    val clazzLvl: Int,
    val playerSkin: PlayerSkin?,
    var isDead: Boolean = false,
    var deaths: Int = 0,
    val colour: Colour = Colour.WHITE,
    val p3Stats: P3Stats = P3Stats(),
    var mapPos: Vec2i? = null,
    var yaw: Float = 0f,
) {
    private var cachedEntity: Player? = null

    fun position(partialTick: Float): Pair<Float, Float>? =
        entity?.let {
            val pos = it.getPosition(partialTick)
            MapScan.worldPosition(pos.x, pos.z)
        } ?: mapPos?.let(MapScan::mapPosition)

    fun yaw(partialTick: Float): Float = entity?.let { Mth.rotLerp(partialTick, it.yRotO, it.yRot) } ?: yaw

    val entity: Player?
        get() {
            val curr = cachedEntity
            if (curr != null && !curr.isRemoved) {
                return curr
            }

            cachedEntity = playerEntities.firstOrNull { it.name.string == name }
            return cachedEntity
        }

    companion object {
        val EMPTY = DungeonPlayer("Empty", DungeonClass.Unknown, 0, null)
    }
}

data class P3Stats(
    var terminals: Int = 0,
    var levers: Int = 0,
    var devices: Int = 0
) {
    fun reset() {
        terminals = 0
        levers = 0
        devices = 0
    }
}
