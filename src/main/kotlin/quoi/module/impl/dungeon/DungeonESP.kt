package quoi.module.impl.dungeon

import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ambient.Bat
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.monster.EnderMan
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.chunk.status.ChunkStatus
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.events.BlockUpdateEvent
import quoi.api.events.EntityEvent
import quoi.api.events.RenderEvent
import quoi.api.events.WorldEvent
import quoi.api.skyblock.Island
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.odonscanning.ScanUtils
import quoi.api.skyblock.invoke
import quoi.module.Module
import quoi.module.settings.Setting.Companion.json
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.UIComponent.Companion.visibleIf
import quoi.utils.EntityUtils
import quoi.utils.EntityUtils.interpolatedBox
import quoi.utils.EntityUtils.isVisibleToPlayer
import quoi.utils.Scheduler.scheduleLoop
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.aabb
import quoi.utils.equalsOneOf
import quoi.utils.render.drawStyledBox

object DungeonESP : Module(
    "Dungeon ESP",
    desc = "Highlights various dungeon entities.",
    area = Island.Dungeon(inClear = true)
) { // todo recode
    private val teammateClassGlow by switch("Teammate class glow", true, desc = "Highlights dungeon teammates based on their class colour.")
    private val starEsp by switch("Starred mobs")

    private val depth by switch("Depth check").childOf(::starEsp)
    private val style by selector("Style", "Box", arrayListOf("Box", "Filled box", "Glow"/*, "2D"*/), desc = "Esp render style to be used.").childOf(::starEsp)
    private val thickness by slider("Thickness", 4, 1, 8, 1).childOf(::style) { it.selected.equalsOneOf("Box", "Filled box") }
    private val sizeOffset by slider("Size offset", 0.0, -1.0, 1.0, 0.05, desc = "Changes box size offset.").childOf(::style) { it.selected.equalsOneOf("Box", "Filled box") }

    private val colourDropdown by text("Colours").childOf(::starEsp)
    private val colourStar by colourPicker("Star", Colour.RED, true, "ESP color for star mobs.").childOf(::colourDropdown)
    private val colourSA by colourPicker("Shadow assassin", Colour.RED, true, "ESP color for shadow assassins.").childOf(::colourDropdown)
    private val colourBat by colourPicker("Bat", Colour.RED, true, "ESP color for bats.").childOf(::colourDropdown)

    private val fillDropdown by text("Fill colours").childOf(::starEsp).visibleIf { style.selected == "Filled box" }
    private val colourStarFill by colourPicker("Star", Colour.RED.withAlpha(60), true, "ESP color for star mobs.").json("Star fill").childOf(::fillDropdown)
    private val colourSAFill by colourPicker("Shadow assassin", Colour.RED.withAlpha(60), true, "ESP color for shadow assassins.").json("Shadow assassin fill").childOf(::fillDropdown)
    private val colourBatFill by colourPicker("Bat", Colour.RED.withAlpha(60), true, "ESP color for bats.").json("Bat fill").childOf(::fillDropdown)

    private val mimicHighlight by switch("Mimic highlight", desc = "Highlights mimic trapped chests.").onValueChanged { _, enabled ->
        if (enabled && this.enabled) scanLoadedMimicChests() else if (!enabled) mimicChests.clear()
    }
    private val mimicDepth by switch("Mimic depth check", desc = "Depth check for mimic chest highlights.").childOf(::mimicHighlight)
    private val mimicStyle by selector("Mimic style", "Box", arrayListOf("Box", "Filled box"), desc = "ESP render style to be used for mimic chests.").childOf(::mimicHighlight)
    private val mimicThickness by slider("Mimic thickness", 2f, 0.1f, 10f, 0.1f, desc = "Line width for mimic chest highlights.").childOf(::mimicHighlight)
    private val mimicColour by colourPicker("Mimic colour", Colour.RED, true, "ESP color for mimic chests.").childOf(::mimicHighlight)
    private val mimicFillColour by colourPicker("Mimic fill colour", Colour.RED.withAlpha(60), true, "Fill color for mimic chests.").childOf(::mimicHighlight).visibleIf { mimicStyle.selected == "Filled box" }

    private var currentEntities = mutableSetOf<EspMob>()
    private val mimicChests = mutableSetOf<BlockPos>()

    override fun onEnable() {
        super.onEnable()
        scanLoadedMimicChests()
    }

    override fun onDisable() {
        super.onDisable()
        mimicChests.clear()
    }

    init {
        scheduleLoop(10) {
            if (!enabled || !starEsp || !Dungeon.inClear) return@scheduleLoop
            updateEntities()
        }

        on<WorldEvent.Change> {
            currentEntities.clear()
            mimicChests.clear()
        }

        on<WorldEvent.Chunk.Load> {
            if (mimicHighlight) scanMimicChests(chunk)
        }

        on<BlockUpdateEvent> {
            if (!mimicHighlight) return@on
            if (updated.block == Blocks.TRAPPED_CHEST) mimicChests.add(pos.immutable())
            else if (old.block == Blocks.TRAPPED_CHEST) mimicChests.remove(pos)
        }

        on<RenderEvent.World> {
            if (starEsp) {
                currentEntities.removeIf { (entity, colour, fillColour) ->
                    if (entity.isDeadOrDying || entity.isRemoved) return@removeIf true
                    if (style.selected != "Glow") {
                        val aabb = entity.interpolatedBox.inflate(sizeOffset, 0.0, sizeOffset)
                        ctx.drawStyledBox(style.selected, aabb, colour, fillColour, thickness.toFloat(), depth)
                    }
                    false
                }
            }

            if (mimicHighlight) {
                mimicChests.removeIf { pos ->
                    if (!level.hasChunk(pos.x shr 4, pos.z shr 4) || level.getBlockState(pos).block != Blocks.TRAPPED_CHEST) {
                        return@removeIf true
                    }

                    val roomCenter = ScanUtils.getRoomCenter(pos.x, pos.z)
                    if (ScanUtils.scannedRooms.any { room ->
                            room.data.trappedChests > 0 && room.roomComponents.any { it.vec2 == roomCenter }
                        }
                    ) return@removeIf false

                    ctx.drawStyledBox(mimicStyle.selected, pos.aabb, mimicColour, mimicFillColour, mimicThickness, mimicDepth)
                    false
                }
            }
        }

        on<EntityEvent.ForceGlow> {
            if (depth && !entity.isVisibleToPlayer()) return@on
            getTeammateColour(entity)?.let { glowColour = it }
            if (!starEsp|| style.selected != "Glow") return@on

            getColour(entity)?.let {
                glowColour = it.first
                return@on
            }

            if (currentEntities.any { it.entity == entity }) glowColour = colourStar
        }
    }

    private fun handleStand(stand: ArmorStand) {
        val name = stand.customName?.string ?: return
        if ("✯" !in name && !name.endsWith("§c❤")) return

        val offset = if (name.noControlCodes.contains("withermancer", true)) 3 else 1
        val realId = stand.id - offset

        stand.level().getEntity(realId)?.takeIf { it is LivingEntity && it !is ArmorStand }?.let {
            currentEntities.add(EspMob(it as LivingEntity, colourStar, colourStarFill))
            return
        }


        stand.level().getEntities(stand, stand.boundingBox.move(0.0, -1.0, 0.0)) {
            it !is ArmorStand && it is LivingEntity && it != player
        }.firstOrNull()?.let {
            currentEntities.add(EspMob(it as LivingEntity, colourStar, colourStarFill))
        }
    }

    private fun getColour(entity: Entity) = when (entity) {
        is Bat if (entity.maxHealth.equalsOneOf(100f, 200f, 400f, 800f)) -> colourBat to colourBatFill
        is EnderMan if (entity.name.string == "Dinnerbone") -> {
            colourStar to colourStarFill
        }
        is ArmorStand -> {
            handleStand(entity)
            null
        }
        is Player -> with(entity.name.string) {
            if (contains("Shadow Assassin")) colourSA to colourSAFill
            else if (equalsOneOf("Diamond Guy", "Lost Adventurer")) colourStar to colourStarFill
            else null
        }
        else -> null
    }

    private fun updateEntities() {
        EntityUtils.getEntities<LivingEntity>().forEach { entity ->
            if (currentEntities.any { it.entity == entity }) return@forEach
            getColour(entity)?.let { (colour, fillColour) ->
                currentEntities.add(EspMob(entity, colour, fillColour))
            }
        }
    }

    private fun getTeammateColour(entity: Entity): Colour? {
        if (!teammateClassGlow || !Dungeon.inDungeons || entity !is Player) return null
        return Dungeon.dungeonTeammates.find { it.name == entity.name?.string }?.clazz?.colour
    }

    private fun scanLoadedMimicChests() {
        if (!mimicHighlight) return
        val level = mc.level ?: return
        val player = mc.player ?: return
        val center = player.blockPosition()
        val radius = mc.options.effectiveRenderDistance

        mimicChests.clear()
        for (chunkX in ((center.x shr 4) - radius)..((center.x shr 4) + radius)) {
            for (chunkZ in ((center.z shr 4) - radius)..((center.z shr 4) + radius)) {
                val chunk = level.chunkSource.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) ?: continue
                scanMimicChests(chunk)
            }
        }
    }

    private fun scanMimicChests(chunk: LevelChunk) {
        chunk.blockEntities.keys.forEach { pos ->
            if (chunk.getBlockState(pos).block == Blocks.TRAPPED_CHEST) {
                mimicChests.add(pos.immutable())
            }
        }
    }

    private data class EspMob(val entity: LivingEntity, val colour: Colour, val fillColour: Colour)
}