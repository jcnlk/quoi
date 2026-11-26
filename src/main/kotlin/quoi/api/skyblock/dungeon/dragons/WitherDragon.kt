package quoi.api.skyblock.dungeon.dragons

import net.minecraft.core.BlockPos
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import quoi.api.colour.Colour
import quoi.api.events.DungeonEvent
import quoi.utils.DualTime
import quoi.utils.EntityUtils.getEntity
import quoi.utils.Vec3

/**
 * modified NoammAddons (CC0-1.0)
 * original: https://github.com/Noamm9/NoammAddons/blob/b631da0640f9ca0ae7b35f4d37dc3fc801a36e97/src/main/kotlin/com/github/noamm9/features/impl/floor7/dragons/WitherDragonEnum.kt
 */
enum class WitherDragon(
    val spawnPos: Vec3,
    val skipBox: AABB,
    val colourCode: Char,
    val colour: Colour,
    val displayName: String,
    val xRange: ClosedRange<Double>,
    val zRange: ClosedRange<Double>,
    val skipKillTime: Int,
    val bottomChin: BlockPos,
) {
    Red(
        Vec3(27, 14, 59),
        AABB(14.5, 13.0, 45.5, 39.5, 28.0, 70.5),
        'c',
        Colour.MINECRAFT_RED,
        "Power Dragon",
        24.0..30.0,
        56.0..62.0,
        50,
        BlockPos(32, 19, 59)
    ),
    Orange(
        Vec3(85, 14, 56),
        AABB(72.0, 8.0, 47.0, 102.0, 28.0, 77.0),
        '6',
        Colour.MINECRAFT_GOLD,
        "Flame Dragon",
        82.0..88.0,
        53.0..59.0,
        62,
        BlockPos(80, 19, 56)
    ),
    Green(
        Vec3(27, 14, 94),
        AABB(7.0, 8.0, 80.0, 37.0, 28.0, 110.0),
        'a',
        Colour.MINECRAFT_GREEN,
        "Apex Dragon",
        23.0..29.0,
        91.0..97.0,
        52,
        BlockPos(32, 18, 94)
    ),
    Blue(
        Vec3(84, 14, 94),
        AABB(71.5, 16.0, 82.5, 96.5, 26.0, 107.5),
        'b',
        Colour.MINECRAFT_AQUA,
        "Ice Dragon",
        82.0..88.0,
        91.0..97.0,
        47,
        BlockPos(79, 19, 94)
    ),
    Purple(
        Vec3(56, 14, 125),
        AABB(45.5, 13.0, 113.5, 68.5, 23.0, 136.5),
        '5',
        Colour.MINECRAFT_DARK_PURPLE,
        "Soul Dragon",
        53.0..59.0,
        122.0..128.0,
        38,
        BlockPos(56, 18, 128)
    );

    var state = WitherDragonState.Dead
        private set
    var timeToSpawn = 100
        internal set
    var timesSpawned = 0
        private set
    var entityId: Int? = null
        private set
    var entity: EnderDragon? = null
        internal set
    var health = 1_000_000_000f
        private set
    var spawnedTime: DualTime? = null
        private set

    internal var offScoreboardTicks = 0

    internal fun spawning() {
        state = WitherDragonState.Spawning
    }

    internal fun spawned(id: Int, time: DualTime) {
        state = WitherDragonState.Alive
        timeToSpawn = 100
        timesSpawned++
        entityId = id
        spawnedTime = time
        offScoreboardTicks = 0
        DungeonEvent.Dragon.Spawn(this).post()
    }

    internal fun updateEntity(id: Int, hard: Boolean = false) {
        if (hard) {
            entityId = id
            state = WitherDragonState.Alive
        } else {
            entity = getEntity(id) as? EnderDragon ?: return
        }
        offScoreboardTicks = 0
    }

    internal fun updateHealth(value: Float, time: DualTime) {
        health = value
        if (value <= 0) died(DungeonEvent.Dragon.Death.Reason.Health, time)
    }

    internal fun died(reason: DungeonEvent.Dragon.Death.Reason, time: DualTime) {
        if (state == WitherDragonState.Dead) return
        val timeAlive = spawnedTime?.let { time - it }
        state = WitherDragonState.Dead
        timeToSpawn = 100
        entityId = null
        entity = null
        if (DragonUtils.priorityDragon == this) DragonUtils.priorityDragon = null
        DungeonEvent.Dragon.Death(this, reason, timeAlive).post()
    }

    internal fun reset() {
        state = WitherDragonState.Dead
        timesSpawned = 0
        timeToSpawn = 100
        entityId = null
        entity = null
        health = 1_000_000_000f
        spawnedTime = null
        offScoreboardTicks = 0
    }
}
