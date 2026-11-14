package quoi.module.impl.dungeon

import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.ExperienceOrb
import net.minecraft.world.entity.ambient.Bat
import net.minecraft.world.entity.boss.wither.WitherBoss
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.arrow.AbstractArrow
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.events.*
import quoi.api.events.core.Priority
import quoi.api.events.core.on
import quoi.api.skyblock.dungeon.Dungeon.dungeonTeammates
import quoi.api.skyblock.dungeon.Dungeon.inClear
import quoi.api.skyblock.dungeon.Dungeon.inDungeons
import quoi.api.skyblock.dungeon.Floor7.getPhaseAt
import quoi.api.skyblock.dungeon.Floor7.inF7Boss
import quoi.api.skyblock.dungeon.enums.Phase
import quoi.api.skyblock.dungeon.odonscanning.ScanUtils
import quoi.api.skyblock.location.Island
import quoi.module.Module
import quoi.module.settings.Setting.Companion.json
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.UIComponent.Companion.visibleIf
import quoi.module.settings.group.SettingGroup.Companion.childOf
import quoi.module.settings.group.SettingGroup.Companion.json
import quoi.utils.EntityUtils.getEntity
import quoi.utils.EntityUtils.interpolatedBox
import quoi.utils.StringUtils.formattedString
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.equalsOneOf

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

    private val starMobs = hashSetOf<Int>()
    private val bats = hashSetOf<Int>()
    private val checkedStands = hashSetOf<Int>()
    private var currentWither: WitherBoss? = null
    private val fakeMinibossRooms = setOf(
        "Duncan", "Balcony", "Wizard", "Pit", "Waterfall", "Atlas", "Chambers", "Layers", "Perch", "Golden Oasis",
    )

    init {
        on<WorldEvent.Change> { resetEntities() }
        on<AreaEvent.Main> { resetEntities() }

        on<EntityEvent.Despawn> {
            starMobs.remove(entity.id)
            bats.remove(entity.id)
            checkedStands.remove(entity.id)
            if (entity == currentWither) currentWither = null
        }

        on<PacketEvent.ReceivedPost, ClientboundSetEntityDataPacket> {
            if (bossEsp && inWitherBoss) {
                val wither = getEntity(packet.id) as? WitherBoss ?: return@on
                if (!wither.isInvisible && wither.invulnerableTicks != 800) currentWither = wither
                return@on
            }
            if (!starEsp || !inClear) return@on
            when (val entity = getEntity(packet.id)) {
                is ArmorStand -> handleStand(entity)
                is Bat -> bats.add(entity.id)
                is Player -> {
                    if (!entity.gameProfile.name.equalsOneOf("Shadow Assassin", "Lost Adventurer", "Diamond Guy", "King Midas")) return@on
                    val roomName = ScanUtils.getRoomFromPos(entity.x, entity.z)?.name ?: return@on
                    if (roomName in fakeMinibossRooms || roomName == "Stairs" && entity.y > 80) return@on
                    starMobs.add(entity.id)
                }
            }
        }

        on<RenderEvent.World> {
            if (teammateEsp && teammateHighlight.style != "Glow" && teammateHighlight.style != "Overlay") dungeonTeammates.forEach { teammate ->
                teammate.entity?.takeIf { it != player }?.let { entity ->
                    teammateHighlight.draw(
                        ctx,
                        entity.interpolatedBox.inflate(0.1),
                        colour = teammate.clazz.colour,
                        fillColour = teammate.clazz.colour,
                    )
                }
            }

            if (starEsp && inClear && starHighlight.style != "Glow" && starHighlight.style != "Overlay") {
                starMobs.forEach { id ->
                    val entity = getEntity(id) ?: return@forEach
                    if (entity.isAlive) starHighlight.draw(
                        ctx,
                        entity.interpolatedBox,
                        colour = if (entity.isShadowAssassin) colourSA else colourStar,
                        overrideFillColour = if (entity.isShadowAssassin) colourSAFill else colourStarFill,
                    )
                }
                bats.forEach { id ->
                    val bat = getEntity(id) as? Bat ?: return@forEach
                    if (bat.id !in starMobs && bat.isAlive && bat.isDungeonBat) starHighlight.draw(
                        ctx, bat.interpolatedBox, colour = colourBat, overrideFillColour = colourBatFill,
                    )
                }
            }

            if (bossEsp && inWitherBoss && bossHighlight.style != "Glow" && bossHighlight.style != "Overlay") currentWither?.let {
                bossHighlight.draw(ctx, it.interpolatedBox)
            }
        }

        on<EntityEvent.ForceGlow>(Priority.LOWEST) {
            if (entity is ArmorStand) return@on
            getDungeonPlayerColour(entity)?.let { colour ->
                if (entity != player) teammateHighlight.draw(this, colour = colour)
                if (teammateHighlight.style != "Glow" && teammateHideGlow) {
                    cancel()
                    return@on
                }
            }

            if (starEsp && inClear && entity.isAlive && !entity.isRemoved) {
                val colour = when {
                    entity.id in starMobs -> if (entity.isShadowAssassin) colourSA else colourStar
                    entity is Bat && entity.isDungeonBat -> colourBat
                    else -> null
                }
                colour?.let { starHighlight.draw(this, overrideColour = it) }
            }

            if (bossEsp && inWitherBoss && entity == currentWither) bossHighlight.draw(this)
        }
    }

    override fun onDisable() {
        resetEntities()
    }

    private fun handleStand(stand: ArmorStand) {
        val name = stand.customName?.formattedString ?: return
        if ("✯" !in name || !name.endsWith("§c❤")) return
        if (!checkedStands.add(stand.id)) return
        val realId = stand.id - if (name.noControlCodes.contains("withermancer", true)) 3 else 1

        val mob = getEntity(realId)
        if (mob != null && mob !is ArmorStand && realId !in starMobs) {
            starMobs.add(realId)
            return
        }

        stand.level().getEntities(stand, stand.boundingBox.move(0.0, -1.0, 0.0)) {
            it !is ArmorStand && it !is ExperienceOrb
        }.firstOrNull {
            it.id !in starMobs && when (it) {
                is Player -> !it.isInvisible && it.uuid.version() == 2 && it != player
                is WitherBoss, is AbstractArrow -> false
                else -> true
            }
        }?.let { starMobs.add(it.id) }
    }

    private fun resetEntities() {
        starMobs.clear()
        bats.clear()
        checkedStands.clear()
        currentWither = null
    }

    private val Bat.isDungeonBat
        get() = when (maxHealth) {
            100f, 200f, 400f, 800f -> true
            else -> false
        }

    private val Entity.isShadowAssassin
        get() = this is Player && gameProfile.name == "Shadow Assassin"

    private fun getDungeonPlayerColour(entity: Entity): Colour? {
        if (entity !is Player || !teammateEsp || !inDungeons) return null
        val name = entity.name.string
        return dungeonTeammates.find { it.name == name }?.clazz?.colour
    }

    private val inWitherBoss get() = inF7Boss && getPhaseAt() != Phase.P5
}
