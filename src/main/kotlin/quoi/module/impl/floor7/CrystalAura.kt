package quoi.module.impl.floor7

import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import net.minecraft.world.phys.Vec3
import quoi.api.events.TickEvent
import quoi.api.events.core.on
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.enums.Phase
import quoi.api.skyblock.location.Island
import quoi.api.skyblock.location.invoke
import quoi.module.Module
import quoi.utils.EntityUtils.getEntities
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.skyblock.player.interact.AuraAction
import quoi.utils.skyblock.player.interact.AuraManager
import kotlin.math.abs

// https://github.com/jcnlk/MeowClient/blob/main/module/modules/CrystalAuraModule.js
object CrystalAura : Module(
    "Crystal Aura",
    desc = "Automatically picks up Energy Crystals.",
    area = Island.Dungeon(7, inBoss = true, phase = Phase.P1)
) {
    private val range by slider("Range", 3.0, 1.0, 5.0, 0.1, desc = "Maximum distance to a crystal target.")
    private val delay by slider("Delay", 7, 1, 20, 1, unit = "ticks", desc = "Delay between crystal interactions.")
    private val inContainer by switch("In container", desc = "Also triggers while any menu is open.")

    private val CRYSTAL_POSITIONS = arrayOf(Vec3(64.5, 238.375, 50.5), Vec3(82.5, 238.375, 50.5))

    private var cooldown = 0

    init {
        on<TickEvent.End> {
            if (Dungeon.isDead || (mc.screen != null && !inContainer)) return@on

            if (cooldown > 0) {
                cooldown--
                return@on
            }

            val target = findTarget() ?: return@on
            AuraManager.interactEntity(target, AuraAction.INTERACT_AT)
            cooldown = delay - 1
        }
    }

    private fun findTarget(): Entity? {
        if (player.inventory.getItem(8).displayName.string.noControlCodes == "Energy Crystal") return null

        val eyePosition = player.eyePosition
        val maxDistanceSquared = range * range

        val entities: Sequence<Entity> = getEntities<EndCrystal>().filter { crystal ->
            CRYSTAL_POSITIONS.any { pos ->
                abs(crystal.x - pos.x) < 0.1 &&
                        abs(crystal.y - pos.y) < 0.1 &&
                        abs(crystal.z - pos.z) < 0.1
            }
        }

        var best: Entity? = null
        var bestDistanceSquared = maxDistanceSquared

        for (entity in entities) {
            if (entity.isRemoved || !entity.isAlive) continue
            if (eyePosition.distanceToSqr(entity.position()) > 25.0) continue

            val hitPosition = closestHitPosition(eyePosition, entity) ?: continue
            val distanceSquared = eyePosition.distanceToSqr(hitPosition)
            if (distanceSquared > bestDistanceSquared) continue

            bestDistanceSquared = distanceSquared
            best = entity
        }

        return best
    }

    private fun closestHitPosition(eyePosition: Vec3, entity: Entity): Vec3? {
        val box = entity.boundingBox.inflate(entity.pickRadius.toDouble())
        val clipped = box.clip(eyePosition, box.center)
        if (clipped.isPresent) return clipped.get()
        return box.center.takeIf { box.contains(eyePosition) }
    }
}
