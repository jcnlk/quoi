package quoi.module.impl.mining

import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.events.*
import quoi.api.events.core.on
import quoi.api.skyblock.location.Island
import quoi.module.Module
import quoi.module.settings.Setting.Companion.json
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.UIComponent.Companion.visibleIf
import quoi.module.settings.group.SettingGroup.Companion.childOf
import quoi.module.settings.group.SettingGroup.Companion.json
import quoi.utils.ChatUtils.literal
import quoi.utils.EntityUtils.getEntities
import quoi.utils.EntityUtils.interpolatedBox
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.aabb
import quoi.utils.render.drawText
import quoi.utils.skyblock.item.ItemUtils.extraAttributes
import quoi.utils.skyblock.item.ItemUtils.skyblockId
import quoi.utils.vec3
import kotlin.math.sqrt

object MineshaftESP : Module(
    "Mineshaft ESP",
    area = Island.Mineshaft,
    desc = "Highlights corpses, fossils, and mobs in Glacite Mineshafts."
) {
    private val corpseEsp by switch("Corpse ESP", desc = "Highlights detected corpse spots.")
    private val corpseHighlight = highlight(desc = "Render style for detected spots.", colour = null, fillColour = null, glow = false).childOf(::corpseEsp)
    private val hideLootedCorpses by switch("Hide looted corpses", desc = "Hides corpse highlights after successfully looting them.").childOf(::corpseEsp).asParent()

    private val corpseColours by text("Colours").childOf(::corpseEsp)
    private val lapisColour by colourPicker("Lapis", Colour.RGB(85, 85, 255), true, "ESP color for Lapis corpses.").json("Lapis colour").childOf(::corpseColours)
    private val umberColour by colourPicker("Umber", Colour.RGB(255, 170, 0), true, "ESP color for Umber corpses.").json("Umber colour").childOf(::corpseColours)
    private val tungstenColour by colourPicker("Tungsten", Colour.RGB(170, 170, 170), true, "ESP color for Tungsten corpses.").json("Tungsten colour").childOf(::corpseColours)
    private val vanguardColour by colourPicker("Vanguard", Colour.RGB(85, 255, 255), true, "ESP color for Vanguard corpses.").json("Vanguard colour").childOf(::corpseColours)

    private val corpseFillColours by text("Fill colours").childOf(::corpseEsp).visibleIf { corpseHighlight.style != "Box" }
    private val lapisFillColour by colourPicker("Lapis", Colour.RGB(85, 85, 255).withAlpha(0.24f), true, "Fill color for Lapis corpses.").json("Lapis fill colour").childOf(::corpseFillColours)
    private val umberFillColour by colourPicker("Umber", Colour.RGB(255, 170, 0).withAlpha(0.24f), true, "Fill color for Umber corpses.").json("Umber fill colour").childOf(::corpseFillColours)
    private val tungstenFillColour by colourPicker("Tungsten", Colour.RGB(170, 170, 170).withAlpha(0.24f), true, "Fill color for Tungsten corpses.").json("Tungsten fill colour").childOf(::corpseFillColours)
    private val vanguardFillColour by colourPicker("Vanguard", Colour.RGB(85, 255, 255).withAlpha(0.24f), true, "Fill color for Vanguard corpses.").json("Vanguard fill colour").childOf(::corpseFillColours)

    private val fossilEsp by switch("Fossil ESP", desc = "Highlights Fossil Blocks in Glacite Mineshafts.")
    private val fossilHighlight = highlight(desc = "Render style for Fossil Blocks.", colour = Colour.RGB(255, 170, 0), fillColour = Colour.RGB(255, 170, 0).withAlpha(0.24f), glow = false).json("Fossil ESP style").childOf(::fossilEsp)

    private val mobEsp by switch("Mob ESP", desc = "Highlights Glacite Mineshaft mobs.").json("Entity ESP")
    private val mobHighlight = highlight(desc = "Render style for highlighted mobs.", colour = null, fillColour = null, glow = false,).json("Entity ESP style").childOf(::mobEsp)

    private val mobColours by text("Colours").childOf(::mobEsp)
    private val bowmanColour by colourPicker("Glacite Bowman", Colour.CYAN, true, "ESP color for Glacite Bowmen.").json("Glacite Bowman colour").childOf(::mobColours)
    private val caverColour by colourPicker("Glacite Caver", Colour.CYAN, true, "ESP color for Glacite Cavers.").json("Glacite Caver colour").childOf(::mobColours)
    private val mageColour by colourPicker("Glacite Mage", Colour.CYAN, true, "ESP color for Glacite Mages.").json("Glacite Mage colour").childOf(::mobColours)
    private val littlefootColour by colourPicker("Littlefoot", Colour.CYAN, true, "ESP color for Littlefoot.").json("Littlefoot colour").childOf(::mobColours)
    private val muttColour by colourPicker("Glacite Mutt", Colour.CYAN, true, "ESP color for Glacite Mutts.").json("Glacite Mutt colour").childOf(::mobColours)

    private val mobFillColors by text("Fill colours").childOf(::mobEsp).visibleIf { mobHighlight.style != "Box" }
    private val bowmanFillColour by colourPicker("Glacite Bowman", Colour.CYAN.withAlpha(0.24f), true, "Fill color for Glacite Bowmen.").json("Glacite Bowman fill colour").childOf(::mobFillColors)
    private val caverFillColour by colourPicker("Glacite Caver", Colour.CYAN.withAlpha(0.24f), true, "Fill color for Glacite Cavers.").json("Glacite Caver fill colour").childOf(::mobFillColors)
    private val mageFillColour by colourPicker("Glacite Mage", Colour.CYAN.withAlpha(0.24f), true, "Fill color for Glacite Mages.").json("Glacite Mage fill colour").childOf(::mobFillColors)
    private val littlefootFillColour by colourPicker("Littlefoot", Colour.CYAN.withAlpha(0.24f), true, "Fill color for Littlefoot.").json("Littlefoot fill colour").childOf(::mobFillColors)
    private val muttFillColour by colourPicker("Glacite Mutt", Colour.CYAN.withAlpha(0.24f), true, "Fill color for Glacite Mutts.").json("Glacite Mutt fill colour").childOf(::mobFillColors)

    private val waypoints = hashMapOf<BlockPos, CorpseType>()
    private val fossilBlocks = hashSetOf<BlockPos>()
    private val scannedFossilChunks = LongOpenHashSet()

    init {
        on<WorldEvent.Change> { reset() }

        on<WorldEvent.Chunk.Load> {
            if (!fossilEsp) return@on

            val chunkKey = chunk.pos.pack()
            if (!scannedFossilChunks.add(chunkKey)) return@on

            chunk.findBlocks(::isFossilBlock) { pos, _ -> fossilBlocks += pos.immutable() }
        }

        on<BlockEvent.Update> {
            if (!fossilEsp || pos !in fossilBlocks || isFossilBlock(updated)) return@on
            fossilBlocks -= pos
        }

        on<EntityEvent.ArmorStandHeadEquipmentUpdate> {
            val type = entity.getMineshaftType() ?: return@on
            waypoints.putIfAbsent(entity.blockPosition(), type)
        }

        on<ChatEvent.Packet> {
            if (!hideLootedCorpses) return@on

            val type = CorpseType.entries.firstOrNull { unformatted == " ${it.name} CORPSE LOOT!" } ?: return@on
            val looted = waypoints.asSequence()
                .filter { (pos, corpseType) -> corpseType == type && player.distanceToSqr(pos.vec3.add(0.5, 0.5, 0.5)) <= 25.0 }
                .minByOrNull { (pos, _) -> player.distanceToSqr(pos.vec3.add(0.5, 0.5, 0.5)) }
                ?.key
                ?: return@on

            waypoints -= looted
        }

        on<RenderEvent.World> {
            if (corpseEsp) {
                waypoints.forEach { (pos, type) ->
                    val waypointBox = pos.aabb
                    val colour = type.colour()
                    val fillColour = type.fillColour()

                    corpseHighlight.draw(ctx, waypointBox, overrideColour = colour, overrideFillColour = fillColour)

                    val textPos = pos.vec3.add(0.5, 2.5, 0.5)
                    val scale = (0.5 + sqrt(player.distanceToSqr(textPos.x, textPos.y, textPos.z)) / 10.0).toFloat()
                    ctx.drawText(type.component, textPos, scale = scale, depth = false)
                }
            }

            if (fossilEsp) {
                fossilBlocks.forEach { pos ->
                    fossilHighlight.draw(ctx, pos.aabb)
                }
            }

            if (mobEsp) {
                getEntities().forEach { entity ->
                    val type = entity.espType ?: return@forEach
                    mobHighlight.draw(ctx, entity.interpolatedBox, overrideColour = type.colour(), overrideFillColour = type.fillColour())
                }
            }
        }
    }

    override fun onDisable() { reset() }

    private fun isFossilBlock(state: BlockState) = when (state.block) {
        Blocks.QUARTZ_BLOCK, Blocks.QUARTZ_STAIRS, Blocks.QUARTZ_SLAB -> true
        else -> false
    }

    private fun reset() {
        waypoints.clear()
        fossilBlocks.clear()
        scannedFossilChunks.clear()
    }

    private val Entity.espType: MineshaftMob?
        get() = when {
            !isAlive -> null
            EntityType.getKey(type).path == "wolf" -> MineshaftMob.GLACITE_MUTT
            this is Player -> MineshaftMob.fromName(cleanName)
            else -> null
        }

    private val Entity.cleanName: String
        get() = (customName ?: displayName).string.noControlCodes.trim()

    private fun ArmorStand.getMineshaftType(): CorpseType? {
        val helmet = getItemBySlot(EquipmentSlot.HEAD).takeUnless { it.isEmpty } ?: return null
        val id = helmet.skyblockId ?: helmet.extraAttributes?.toString()
        return CorpseType.entries.firstOrNull { type ->
            id?.contains(type.skyblockId, true) == true
        }
    }

    private enum class CorpseType(val skyblockId: String, label: String) {
        LAPIS("LAPIS_ARMOR_HELMET", "&9&lLapis"),
        UMBER("ARMOR_OF_YOG_HELMET", "&6&lUmber"),
        TUNGSTEN("MINERAL_HELMET", "&7&lTungsten"),
        VANGUARD("VANGUARD_HELMET", "&b&lVanguard");

        val component = literal(label)
    }

    private fun CorpseType.colour() = when (this) {
        CorpseType.LAPIS -> lapisColour
        CorpseType.UMBER -> umberColour
        CorpseType.TUNGSTEN -> tungstenColour
        CorpseType.VANGUARD -> vanguardColour
    }

    private fun CorpseType.fillColour() = when (this) {
        CorpseType.LAPIS -> lapisFillColour
        CorpseType.UMBER -> umberFillColour
        CorpseType.TUNGSTEN -> tungstenFillColour
        CorpseType.VANGUARD -> vanguardFillColour
    }

    private enum class MineshaftMob {
        GLACITE_BOWMAN,
        GLACITE_CAVER,
        GLACITE_MAGE,
        LITTLEFOOT,
        GLACITE_MUTT;

        val displayName = name.replace('_', ' ')

        companion object {
            fun fromName(name: String) = entries.firstOrNull { it.displayName.equals(name, ignoreCase = true) }
        }
    }

    private fun MineshaftMob.colour() = when (this) {
        MineshaftMob.GLACITE_BOWMAN -> bowmanColour
        MineshaftMob.GLACITE_CAVER -> caverColour
        MineshaftMob.GLACITE_MAGE -> mageColour
        MineshaftMob.LITTLEFOOT -> littlefootColour
        MineshaftMob.GLACITE_MUTT -> muttColour
    }

    private fun MineshaftMob.fillColour() = when (this) {
        MineshaftMob.GLACITE_BOWMAN -> bowmanFillColour
        MineshaftMob.GLACITE_CAVER -> caverFillColour
        MineshaftMob.GLACITE_MAGE -> mageFillColour
        MineshaftMob.LITTLEFOOT -> littlefootFillColour
        MineshaftMob.GLACITE_MUTT -> muttFillColour
    }
}
