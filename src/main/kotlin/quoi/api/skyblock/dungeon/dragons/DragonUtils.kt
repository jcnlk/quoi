package quoi.api.skyblock.dungeon.dragons

import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import net.minecraft.world.level.block.Blocks
import quoi.annotations.Init
import quoi.api.events.*
import quoi.api.events.core.EventDispatcher
import quoi.api.events.core.EventListener
import quoi.api.events.core.on
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.Floor7
import quoi.api.skyblock.dungeon.enums.DungeonClass
import quoi.api.skyblock.dungeon.enums.Phase
import quoi.utils.DualTime
import quoi.utils.ScoreboardUtils
import quoi.utils.StringUtils.noControlCodes

/**
 * modified NoammAddons (CC0-1.0)
 * original: https://github.com/Noamm9/NoammAddons/blob/b631da0640f9ca0ae7b35f4d37dc3fc801a36e97/src/main/kotlin/com/github/noamm9/features/impl/floor7/dragons/DragonCheck.kt
 *           https://github.com/Noamm9/NoammAddons/blob/b631da0640f9ca0ae7b35f4d37dc3fc801a36e97/src/main/kotlin/com/github/noamm9/features/impl/floor7/dragons/WitherDragons.kt
 */
@Init
object DragonUtils : EventListener {
    private val active: Boolean
        get() = Floor7.inPhaseAt(Phase.P5)

    val dragonSpawnCount: Int get() = WitherDragon.entries.sumOf { it.timesSpawned }

    var priorityEnabled = false
    var soloPriorityClass = DungeonClass.Healer
    var firstDragonOnly = true

    var priorityDragon: WitherDragon? = null
        internal set(value) {
            if (field == value) return
            val old = field
            field = value
            DungeonEvent.Dragon.Priority(old, value).post()
        }

    private val healthRegex = Regex("""\d+(?:\.\d+)?[bBmMkK]""")
    private val colourRegex = Regex("§.")

    init {
        on<WorldEvent.Change> { reset() }

        on<PacketEvent.Received> {
            if (!active) return@on
            when (val packet = packet) {
                is ClientboundLevelParticlesPacket -> spawning(packet)
                is ClientboundAddEntityPacket -> spawned(packet)
                is ClientboundSetEntityDataPacket -> update(packet)
            }
        }

        on<EntityEvent.Despawn> {
            if (!active || entity !is EnderDragon) return@on
            fromEntityId(entity.id)?.apply {
                if (state != WitherDragonState.Alive) return@apply
                this.entity = null
                offScoreboardTicks = 0
            }
        }

        on<BlockEvent.Update> {
            if (active && updated.`is`(Blocks.AIR) && old.`is`(Blocks.COBBLESTONE_SLAB)) statueDestroyed(pos, now())
        }

        on<TickEvent.Server> { tick() }
    }

    fun fromEntityId(id: Int): WitherDragon? = WitherDragon.entries.find { it.entityId == id }

    private fun spawning(packet: ClientboundLevelParticlesPacket) {
        if (packet.particle.type != ParticleTypes.FLAME || packet.count != 20 || packet.y != 19.0) return
        if (packet.x % 1 != 0.0 || packet.z % 1 != 0.0) return
        if (packet.xDist != 2f || packet.yDist != 3f || packet.zDist != 2f) return
        //? if >=26.3 {
        /*if (!packet.overrideLimiter || packet.xMaxSpeed != 0f || packet.yMaxSpeed != 0f || packet.zMaxSpeed != 0f) return
        *///?} else {
        if (!packet.isOverrideLimiter || packet.maxSpeed != 0f) return
        //?}
        var best: WitherDragon? = null
        var worse: WitherDragon? = null
        var announced: WitherDragon? = null

        for (dragon in WitherDragon.entries) {
            if (dragon.state != WitherDragonState.Spawning) {
                if (packet.x !in dragon.xRange || packet.z !in dragon.zRange) continue
                dragon.spawning()
                announced = dragon
            }
            if (best == null || dragon.timeToSpawn < best.timeToSpawn) best = dragon
            if (worse == null || dragon.timeToSpawn > worse.timeToSpawn) worse = dragon
        }

        val selected = when {
            !priorityEnabled || firstDragonOnly && dragonSpawnCount >= 2 -> best
            Dungeon.currentDungeonPlayer.clazz == soloPriorityClass && worse != null -> worse
            else -> best
        }
        if (selected != null) priorityDragon = selected
        announced?.let { DungeonEvent.Dragon.Spawning(it).post() }
    }

    private fun spawned(packet: ClientboundAddEntityPacket) {
        if (packet.type != EntityTypes.ENDER_DRAGON) return
        WitherDragon.entries.find {
            it.state == WitherDragonState.Spawning &&
                packet.x in it.skipBox.minX..it.skipBox.maxX && packet.z in it.skipBox.minZ..it.skipBox.maxZ
        }?.spawned(packet.id, now()) ?: fromEntityId(packet.id)?.takeIf {
            it.state == WitherDragonState.Alive && it.entity == null
        }?.updateEntity(packet.id, hard = true)
    }

    private fun update(packet: ClientboundSetEntityDataPacket) {
        val dragon = fromEntityId(packet.id) ?: return
        if (dragon.entity?.isAlive != true) dragon.updateEntity(packet.id)
        val health = packet.packedItems.find { it.id == 9 }?.value as? Float ?: return
        dragon.updateHealth(health, now())
    }

    private fun tick() {
        val time = now()
        WitherDragon.entries.forEach { dragon ->
            if (dragon.state == WitherDragonState.Spawning) {
                dragon.timeToSpawn--
                if (dragon.timeToSpawn <= -20) dragon.died(DungeonEvent.Dragon.Death.Reason.SpawnTimeout, time)
            }

            if (dragon.state == WitherDragonState.Alive && dragon.entity == null) {
                if (isAliveOnScoreboard(dragon)) dragon.offScoreboardTicks = 0
                else if (dragon.offScoreboardTicks < 40) dragon.offScoreboardTicks++
                else dragon.died(DungeonEvent.Dragon.Death.Reason.Scoreboard, time)
            }
        }
    }

    private fun isAliveOnScoreboard(dragon: WitherDragon): Boolean = ScoreboardUtils.getLines().any {
        val line = it.string.noControlCodes.replace(colourRegex, "")
        line.contains(dragon.displayName, ignoreCase = true) && healthRegex.find(line)?.value != "0"
    }

    private fun statueDestroyed(pos: BlockPos, time: DualTime) {
        WitherDragon.entries.find { it.bottomChin.x == pos.x && it.bottomChin.z == pos.z }
            ?.died(DungeonEvent.Dragon.Death.Reason.Statue, time)
    }

    private fun reset() {
        priorityDragon = null
        WitherDragon.entries.forEach { it.reset() }
    }

    private fun now() = DualTime(EventDispatcher.totalTicks.toLong())
}
