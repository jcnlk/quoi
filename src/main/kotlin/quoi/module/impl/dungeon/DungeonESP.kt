//~ enderman_type

package quoi.module.impl.dungeon

import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ambient.Bat
import net.minecraft.world.entity.boss.wither.WitherBoss
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.monster.EnderMan
import net.minecraft.world.entity.player.Player
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.events.EntityEvent
import quoi.api.events.PacketEvent
import quoi.api.events.RenderEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.Priority
import quoi.api.events.core.on
import quoi.api.skyblock.dungeon.Dungeon.dungeonTeammates
import quoi.api.skyblock.dungeon.Dungeon.inClear
import quoi.api.skyblock.dungeon.Dungeon.inDungeons
import quoi.api.skyblock.dungeon.Floor7.inF7Boss
import quoi.api.skyblock.location.Island
import quoi.module.Module
import quoi.module.settings.Setting.Companion.json
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.UIComponent.Companion.visibleIf
import quoi.module.settings.group.SettingGroup.Companion.childOf
import quoi.module.settings.group.SettingGroup.Companion.json
import quoi.utils.EntityUtils.getEntities
import quoi.utils.EntityUtils.getEntity
import quoi.utils.EntityUtils.interpolatedBox
import quoi.utils.Scheduler.scheduleLoop
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.equalsOneOf
import quoi.utils.removeIf

/**
 * TODO:
 *  - better default colors or whatever
 */

object DungeonESP : Module(
    "Dungeon ESP",
    desc = "Highlights various dungeon entities.",
    area = Island.Dungeon
) {
    private val teammateEsp by switch("Teammate ESP", true, desc = "Highlights dungeon teammates based on their class colour.")
        .json("Teammate class glow")
    private val teammateHighlight = highlight(
        customColour = true,
        customFillColour = true,
        aabbOffset = true,
    ).json("Teammate style").childOf(::teammateEsp)
    private val teammateHideGlow by switch("Hide glow", true, desc = "Hides all glow effects on highlighted teammates.")
        .json("Teammate hide glow")
        .childOf(teammateHighlight.component) { teammateHighlight.style != "Glow" }

    private val starEsp by switch("Starred mobs")

    private val starHighlight = highlight(colour = null, fillColour = null, aabbOffset = true).childOf(::starEsp)

    private val colourDropdown by text("Colours").childOf(::starEsp).visibleIf { starHighlight.style != "Filled" }
    private val colourStar by colourPicker("Star", Colour.RED, true, "ESP color for star mobs.").childOf(::colourDropdown)
    private val colourSA by colourPicker("Shadow assassin", Colour.RED, true, "ESP color for shadow assassins.").childOf(::colourDropdown)
    private val colourBat by colourPicker("Bat", Colour.RED, true, "ESP color for bats.").childOf(::colourDropdown)

    private val fillDropdown by text("Fill colours").childOf(::starEsp).visibleIf { starHighlight.style.equalsOneOf("Filled box", "Filled") }
    private val colourStarFill by colourPicker("Star", Colour.RED.withAlpha(60), true, "ESP color for star mobs.").json("Star fill").childOf(::fillDropdown)
    private val colourSAFill by colourPicker("Shadow assassin", Colour.RED.withAlpha(60), true, "ESP color for shadow assassins.").json("Shadow assassin fill").childOf(::fillDropdown)
    private val colourBatFill by colourPicker("Bat", Colour.RED.withAlpha(60), true, "ESP color for bats.").json("Bat fill").childOf(::fillDropdown)

    private val bossEsp by switch("Wither boss")
    private val bossHighlight = highlight("Style", aabbOffset = true).json("Boss style").childOf(::bossEsp)

    private val currentEntities = mutableMapOf<Int, EspMob>()

    init {
        scheduleLoop(10) {
            if (!enabled || !starEsp || !inClear) return@scheduleLoop
            updateEntities()
        }

        on<WorldEvent.Change> { currentEntities.clear() }

        on<EntityEvent.Spawn> {
            if (starEsp && inClear) updateEntity(entity)
        }

        on<PacketEvent.ReceivedPost, ClientboundSetEntityDataPacket> {
            if (!starEsp || !inClear) return@on
            getEntity(packet.id)?.let(::updateEntity)
        }

        on<RenderEvent.World> {
            if (teammateEsp && teammateHighlight.style != "Glow" && teammateHighlight.style != "Overlay") getEntities<Player>().forEach { entity ->
                getTeammateColour(entity)?.let { colour ->
                    teammateHighlight.draw(
                        ctx,
                        entity.interpolatedBox.inflate(0.1),
                        colour = colour,
                        fillColour = colour,
                    )
                }
            }

            val drawStarBoxes = starHighlight.style != "Glow" && starHighlight.style != "Overlay"
            currentEntities.removeIf { _, mob ->
                val entity = mob.entity

                if (!starEsp || !inClear || entity.isDeadOrDying || entity.isRemoved) return@removeIf true

                if (drawStarBoxes) starHighlight.draw(
                    ctx,
                    entity.interpolatedBox,
                    colour = mob.colour,
                    overrideFillColour = mob.fillColour,
                )
                false
            }

            if (bossEsp && inF7Boss && bossHighlight.style != "Glow" && bossHighlight.style != "Overlay") getEntities<WitherBoss>()
                .filter { it.isWitherBoss }
                .forEach { bossHighlight.draw(ctx, it.interpolatedBox) }
        }

        on<EntityEvent.ForceGlow>(Priority.LOWEST) {
            if (entity !is LivingEntity || entity is ArmorStand) return@on
            getDungeonPlayerColour(entity)?.let { colour ->
                if (entity != player) teammateHighlight.draw(this, colour = colour)
                if (teammateHighlight.style != "Glow" && teammateHideGlow) {
                    cancel()
                    return@on
                }
            }

            if (starEsp && inClear) {
                val colour = if (entity is Bat && entity.isDungeonBat) colourBat else currentEntities[entity.id]?.colour
                colour?.let { starHighlight.draw(this, overrideColour = it) }
            }

            if (bossEsp && inF7Boss && entity.isWitherBoss) bossHighlight.draw(this)
        }
    }

    override fun onEnable() {
        if (starEsp && inClear) updateEntities()
    }

    override fun onDisable() {
        currentEntities.clear()
    }

    private fun handleStand(stand: ArmorStand) {
        val name = stand.customName?.string ?: return
        if ("✯" !in name && !name.endsWith("§c❤")) return

        val offset = if (name.noControlCodes.contains("withermancer", true)) 3 else 1
        val realId = stand.id - offset

        if (realId in currentEntities) return

        stand.level().getEntity(realId)?.takeIf { it is LivingEntity && it !is ArmorStand }?.let {
            addMob(it as LivingEntity, MobType.STAR)
            return
        }

        stand.level().getEntities(stand, stand.boundingBox.move(0.0, -1.0, 0.0)) {
            it !is ArmorStand && it is LivingEntity && it != player
        }.firstOrNull()?.let {
            addMob(it as LivingEntity, MobType.STAR)
        }
    }

    private fun getMobType(entity: Entity): MobType? = when (entity) {
        is Bat if entity.isDungeonBat -> MobType.BAT
        is EnderMan -> {
            val stand = getEntity(entity.id + 1) as? ArmorStand

            if (stand?.customName?.string?.contains("✯") == true) MobType.STAR
            else null
        }
        is ArmorStand -> {
            handleStand(entity)
            null
        }
        is Player -> with(entity.name.string) {
            if (contains("Shadow Assassin")) MobType.SHADOW_ASSASSIN
            else if (equalsOneOf("Diamond Guy", "Lost Adventurer")) MobType.STAR
            else null
        }
        else -> null
    }

    private fun updateEntities() {
        getEntities<LivingEntity>().forEach { entity ->
            if (entity.id !in currentEntities) updateEntity(entity)
        }
    }

    private fun updateEntity(entity: Entity) {
        if (entity !is LivingEntity || (entity !is Player && entity.id in currentEntities)) return
        getMobType(entity)?.let { type ->
            addMob(entity, type)
        }
    }

    private fun addMob(entity: LivingEntity, type: MobType) {
        // keep the shadow assassin colour when found through a name stand
        val mobType = if (entity is Player) getMobType(entity) ?: type else type
        currentEntities[entity.id] = EspMob(entity, mobType)
    }

    private val Bat.isDungeonBat
        get() = when (maxHealth) {
            100f, 200f, 400f, 800f -> true
            else -> false
        }

    private fun getTeammateColour(entity: Entity): Colour? {
        if (entity == player) return null
        return getDungeonPlayerColour(entity)
    }

    private fun getDungeonPlayerColour(entity: Entity): Colour? {
        if (entity !is Player || !teammateEsp || !inDungeons) return null
        val name = entity.name.string
        return dungeonTeammates.find { it.name == name }?.clazz?.colour
    }

    private enum class MobType { STAR, SHADOW_ASSASSIN, BAT }

    private class EspMob(val entity: LivingEntity, val type: MobType) {
        val colour: Colour
            get() = when (type) {
                MobType.STAR -> colourStar
                MobType.SHADOW_ASSASSIN -> colourSA
                MobType.BAT -> colourBat
            }

        val fillColour: Colour
            get() = when (type) {
                MobType.STAR -> colourStarFill
                MobType.SHADOW_ASSASSIN -> colourSAFill
                MobType.BAT -> colourBatFill
            }
    }

    private val Entity.isWitherBoss get() = this is WitherBoss && !this.isInvisible && this.invulnerableTicks != 800
}
