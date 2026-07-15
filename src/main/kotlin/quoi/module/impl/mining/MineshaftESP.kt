package quoi.module.impl.mining

import quoi.api.events.core.on
import net.minecraft.core.BlockPos
import net.minecraft.client.renderer.blockentity.BeaconRenderer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.events.RenderEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.skyblock.location.Island
import quoi.module.Module
import quoi.module.settings.Setting.Companion.json
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.UIComponent.Companion.visibleIf
import quoi.utils.ChatUtils.literal
import quoi.utils.EntityUtils.getEntities
import quoi.utils.EntityUtils.interpolatedBox
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.aabb
import quoi.utils.render.drawStyledBox
import quoi.utils.render.drawText
import quoi.utils.skyblock.item.ItemUtils.extraAttributes
import quoi.utils.skyblock.item.ItemUtils.skyblockId
import quoi.utils.vec3
import kotlin.math.sqrt

object MineshaftESP : Module(
    "Mineshaft ESP",
    area = Island.Mineshaft,
    desc = "Highlights corpses and mobs in Glacite Mineshafts."
) {
    private val corpseEsp by switch("Corpse ESP", desc = "Highlights detected corpse spots.")
    private val names by switch("Show names",  desc = "Shows a label above detected spots.").childOf(::corpseEsp).asParent()
    private val style by selector("Style", "Box", arrayListOf("Filled", "Filled box", "Box"), desc = "Render style for detected spots.").childOf(::corpseEsp)
    private val beaconBeam by switch("Beacon beam",  desc = "Renders a vertical beacon beam at detected spots.").childOf(::corpseEsp).asParent()

    private val corpseColours by text("Colours").childOf(::corpseEsp)
    private val lapisColour by colourPicker("Lapis", Colour.RGB(85, 85, 255), true, "ESP color for Lapis corpses.").json("Lapis colour").childOf(::corpseColours)
    private val umberColour by colourPicker("Umber", Colour.RGB(255, 170, 0), true, "ESP color for Umber corpses.").json("Umber colour").childOf(::corpseColours)
    private val tungstenColour by colourPicker("Tungsten", Colour.RGB(170, 170, 170), true, "ESP color for Tungsten corpses.").json("Tungsten colour").childOf(::corpseColours)
    private val vanguardColour by colourPicker("Vanguard", Colour.RGB(85, 255, 255), true, "ESP color for Vanguard corpses.").json("Vanguard colour").childOf(::corpseColours)

    private val corpseFillColours by text("Fill colours").childOf(::corpseEsp).visibleIf { style.selected != "Box" }
    private val lapisFillColour by colourPicker("Lapis", Colour.RGB(85, 85, 255).withAlpha(0.24f), true, "Fill color for Lapis corpses.").json("Lapis fill colour").childOf(::corpseFillColours)
    private val umberFillColour by colourPicker("Umber", Colour.RGB(255, 170, 0).withAlpha(0.24f), true, "Fill color for Umber corpses.").json("Umber fill colour").childOf(::corpseFillColours)
    private val tungstenFillColour by colourPicker("Tungsten", Colour.RGB(170, 170, 170).withAlpha(0.24f), true, "Fill color for Tungsten corpses.").json("Tungsten fill colour").childOf(::corpseFillColours)
    private val vanguardFillColour by colourPicker("Vanguard", Colour.RGB(85, 255, 255).withAlpha(0.24f), true, "Fill color for Vanguard corpses.").json("Vanguard fill colour").childOf(::corpseFillColours)

    private val mobEsp by switch("Mob ESP", desc = "Highlights Glacite Mineshaft mobs.").json("Entity ESP")
    private val mobStyle by selector("Style", "Box", arrayListOf("Filled", "Filled box", "Box"), desc = "Render style for highlighted mobs.").json("Entity ESP style").childOf(::mobEsp)

    private val mobColours by text("Colours").childOf(::mobEsp)
    private val bowmanColour by colourPicker("Glacite Bowman", Colour.CYAN, true, "ESP color for Glacite Bowmen.").json("Glacite Bowman colour").childOf(::mobColours)
    private val caverColour by colourPicker("Glacite Caver", Colour.CYAN, true, "ESP color for Glacite Cavers.").json("Glacite Caver colour").childOf(::mobColours)
    private val mageColour by colourPicker("Glacite Mage", Colour.CYAN, true, "ESP color for Glacite Mages.").json("Glacite Mage colour").childOf(::mobColours)
    private val littlefootColour by colourPicker("Littlefoot", Colour.CYAN, true, "ESP color for Littlefoot.").json("Littlefoot colour").childOf(::mobColours)
    private val muttColour by colourPicker("Glacite Mutt", Colour.CYAN, true, "ESP color for Glacite Mutts.").json("Glacite Mutt colour").childOf(::mobColours)

    private val mobFillColors by text("Fill colours").childOf(::mobEsp).visibleIf { mobStyle.selected != "Box" }
    private val bowmanFillColour by colourPicker("Glacite Bowman", Colour.CYAN.withAlpha(0.24f), true, "Fill color for Glacite Bowmen.").json("Glacite Bowman fill colour").childOf(::mobFillColors)
    private val caverFillColour by colourPicker("Glacite Caver", Colour.CYAN.withAlpha(0.24f), true, "Fill color for Glacite Cavers.").json("Glacite Caver fill colour").childOf(::mobFillColors)
    private val mageFillColour by colourPicker("Glacite Mage", Colour.CYAN.withAlpha(0.24f), true, "Fill color for Glacite Mages.").json("Glacite Mage fill colour").childOf(::mobFillColors)
    private val littlefootFillColour by colourPicker("Littlefoot", Colour.CYAN.withAlpha(0.24f), true, "Fill color for Littlefoot.").json("Littlefoot fill colour").childOf(::mobFillColors)
    private val muttFillColour by colourPicker("Glacite Mutt", Colour.CYAN.withAlpha(0.24f), true, "Fill color for Glacite Mutts.").json("Glacite Mutt fill colour").childOf(::mobFillColors)

    private val waypoints = linkedMapOf<BlockPos, MineshaftType>()

    init {
        on<WorldEvent.Change> { waypoints.clear() }

        on<TickEvent.End> {
            if (player.tickCount % 20 != 0) return@on
            if (!corpseEsp) return@on

            val found = linkedMapOf<BlockPos, MineshaftType>()

            getEntities<ArmorStand>().forEach { stand ->
                val type = stand.getMineshaftType() ?: return@forEach
                found.putIfAbsent(stand.blockPosition(), type)
            }

            waypoints.clear()
            waypoints.putAll(found)
        }

        on<RenderEvent.World> {
            if (corpseEsp) {
                waypoints.forEach { (pos, type) ->
                    val waypointBox = pos.aabb
                    val colour = type.colour()
                    val fillColour = type.fillColour()

                    ctx.drawStyledBox(style.selected, waypointBox, colour, fillColour)

                    if (beaconBeam) {
                        val pose = com.mojang.blaze3d.vertex.PoseStack()
                        val cameraPos = mc.gameRenderer.mainCamera.position()
                        val time = (level.gameTime + mc.deltaTracker.getGameTimeDeltaPartialTick(true)).toFloat()

                        pose.pushPose()
                        pose.translate(pos.x.toDouble() - cameraPos.x, pos.y.toDouble() - cameraPos.y, pos.z.toDouble() - cameraPos.z)
                        BeaconRenderer.submitBeaconBeam(pose, ctx.commandQueue(), BeaconRenderer.BEAM_LOCATION, 1.0f, time, colour.rgb, 0, 160, 0.2f, 0.25f)
                        pose.popPose()
                    }

                    val textPos = pos.vec3.add(0.5, 2.5, 0.5)
                    if (names) {
                        val scale = (0.5 + sqrt(player.distanceToSqr(textPos.x, textPos.y, textPos.z)) / 10.0).toFloat()
                        ctx.drawText(literal(type.label), textPos, scale = scale, depth = false)
                    }
                }
            }

            if (mobEsp) {
                getEntities().forEach { entity ->
                    val type = entity.espType ?: return@forEach
                    ctx.drawStyledBox(mobStyle.selected, entity.interpolatedBox, type.colour(), type.fillColour())
                }
            }
        }
    }

    private val Entity.espType: EntityEspType?
        get() = when {
            !isAlive -> null
            type == EntityType.WOLF -> EntityEspType.GLACITE_MUTT
            this is Player -> EntityEspType.fromName(cleanName)
            else -> null
        }

    private val Entity.cleanName: String
        get() = (customName ?: displayName).string.noControlCodes.trim()

    private fun ArmorStand.getMineshaftType(): MineshaftType? {
        val helmet = getItemBySlot(EquipmentSlot.HEAD).takeUnless { it.isEmpty } ?: return null
        val id = helmet.skyblockId ?: helmet.extraAttributes?.toString()
        return MineshaftType.entries.firstOrNull { type ->
            id?.contains(type.skyblockId, true) == true
        }
    }

    private enum class MineshaftType(val skyblockId: String, val label: String) {
        LAPIS("LAPIS_ARMOR_HELMET", "&9&lLapis"),
        UMBER("ARMOR_OF_YOG_HELMET", "&6&lUmber"),
        TUNGSTEN("MINERAL_HELMET", "&7&lTungsten"),
        VANGUARD("VANGUARD_HELMET", "&b&lVanguard")
    }

    private fun MineshaftType.colour() = when (this) {
        MineshaftType.LAPIS -> lapisColour
        MineshaftType.UMBER -> umberColour
        MineshaftType.TUNGSTEN -> tungstenColour
        MineshaftType.VANGUARD -> vanguardColour
    }

    private fun MineshaftType.fillColour() = when (this) {
        MineshaftType.LAPIS -> lapisFillColour
        MineshaftType.UMBER -> umberFillColour
        MineshaftType.TUNGSTEN -> tungstenFillColour
        MineshaftType.VANGUARD -> vanguardFillColour
    }

    private enum class EntityEspType {
        GLACITE_BOWMAN,
        GLACITE_CAVER,
        GLACITE_MAGE,
        LITTLEFOOT,
        GLACITE_MUTT;

        companion object {
            fun fromName(name: String) = entries.firstOrNull { it.name.replace('_', ' ').equals(name, ignoreCase = true) }
        }
    }

    private fun EntityEspType.colour() = when (this) {
        EntityEspType.GLACITE_BOWMAN -> bowmanColour
        EntityEspType.GLACITE_CAVER -> caverColour
        EntityEspType.GLACITE_MAGE -> mageColour
        EntityEspType.LITTLEFOOT -> littlefootColour
        EntityEspType.GLACITE_MUTT -> muttColour
    }

    private fun EntityEspType.fillColour() = when (this) {
        EntityEspType.GLACITE_BOWMAN -> bowmanFillColour
        EntityEspType.GLACITE_CAVER -> caverFillColour
        EntityEspType.GLACITE_MAGE -> mageFillColour
        EntityEspType.LITTLEFOOT -> littlefootFillColour
        EntityEspType.GLACITE_MUTT -> muttFillColour
    }
}
