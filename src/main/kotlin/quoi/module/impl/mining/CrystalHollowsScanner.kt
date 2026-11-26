package quoi.module.impl.mining

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.events.RenderEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.EventListener
import quoi.api.events.core.asyncScope
import quoi.api.events.core.on
import quoi.api.skyblock.location.Island
import quoi.module.Module
import quoi.module.impl.mining.CrystalHollowsMap.X_MAX
import quoi.module.impl.mining.CrystalHollowsMap.X_MIN
import quoi.module.impl.mining.CrystalHollowsMap.Z_MAX
import quoi.module.impl.mining.CrystalHollowsMap.Z_MIN
import quoi.module.impl.mining.CrystalHollowsMap.isDirty
import quoi.module.impl.mining.enums.Structure
import quoi.module.settings.UIComponent.Companion.visibleIf
import quoi.utils.ChatUtils
import quoi.utils.ChatUtils.literal
import quoi.utils.EntityUtils.renderX
import quoi.utils.EntityUtils.renderY
import quoi.utils.EntityUtils.renderZ
import quoi.utils.aabb
import quoi.utils.render.drawFilledBox
import quoi.utils.render.drawStyledBox
import quoi.utils.render.drawText
import quoi.utils.vec3
import kotlin.math.absoluteValue
import kotlin.math.pow
import kotlin.math.sqrt

// https://github.com/RoseGoldIsntGay/GumTuneClient/blob/main/src/main/java/rosegold/gumtuneclient/modules/world/WorldScanner.java
object CrystalHollowsScanner : Module(
    "Crystal Hollows Scanner",
    area = Island.CrystalHollows
) {
    val structureScanner by switch("Structure scanner")
    val routeScanner by switch("Route scanner")
    private val style by selector(
        "Style",
        "Box",
        arrayListOf("Box", "Filled box"),
        desc = "Esp render style to be used."
    ).visibleIf { routeScanner }
    private val distCols by switch("Distance colours").visibleIf { routeScanner }
    val colour by colourPicker("Colour", Colour.WHITE, allowAlpha = true).visibleIf { routeScanner && !distCols }
    private val fillDistCols by switch("Fill distance colours").visibleIf { style.selected == "Filled box" && routeScanner }
    private val fillColour by colourPicker(
        "Fill colour",
        Colour.WHITE.withAlpha(0.33f),
        allowAlpha = true
    ).visibleIf { style.selected == "Filled box" && routeScanner && !fillDistCols }
    private val thickness by slider("Thickness", 4f, 1f, 8f, 1f).visibleIf { routeScanner }

    val scannedChunks = HashSet<Long>()
    val foundStructures = mutableMapOf<Structure, MutableList<BlockPos>>()
    val foundRouteBlocks = mutableListOf<BlockPos>()
    private var worldGeneration = 0L
    private val lifecycle = object : EventListener {}

    init {
        on<WorldEvent.Chunk.Load> {
            val scanStructures = structureScanner
            val scanRoutes = routeScanner
            if (!scanStructures && !scanRoutes) return@on
            val chunkPos = chunk.pos
            if (chunkPos.minBlockX !in X_MIN..X_MAX || chunkPos.minBlockZ !in Z_MIN..Z_MAX) return@on
            if (!scannedChunks.add(chunkPos.pack())) return@on
            isDirty = true

            val world = chunk.level
            val generation = worldGeneration
            val stateAt = snapshot(chunk, scanRoutes)
            lifecycle.asyncScope.launch {
                val result = withContext(Dispatchers.Default) {
                    scanCrystalHollowsChunk(chunkPos, scanStructures, scanRoutes, stateAt)
                }
                if (worldGeneration != generation || mc.level !== world) return@launch

                result.structures.forEach { (structure, positions) ->
                    positions.forEach position@{ realPos ->
                        val pos = realPos.offset(-structure.xOffset, -structure.yOffset, -structure.zOffset)
                        val existing = foundStructures.getOrPut(structure) { mutableListOf() }
                        if (existing.isNotEmpty() && (!structure.canBeMultiple || existing.any {
                                it.isWithinChunks(
                                    pos,
                                    4
                                )
                            })) return@position
                        existing.add(realPos)
                        ChatUtils.modMessage("Found ${structure.displayName} at ${pos.x}, ${pos.y}, ${pos.z}")
                    }
                }
                foundRouteBlocks.addAll(result.routeBlocks.filterNot { it in foundRouteBlocks })
            }
        }

        on<RenderEvent.World> {
            if (structureScanner) foundStructures.forEach { (structure, positions) ->
                positions.forEach { blockPos ->
                    val pos = blockPos.vec3
                    val dist = pos.distanceToSqr(player.renderX, player.renderY, player.renderZ)
                    val scale = (0.5 + dist.pow(0.5) / 10.0).toFloat()
                    ctx.drawFilledBox(pos.aabb, Colour.WHITE)
                    ctx.drawText(literal(structure.displayName).withColor(structure.colour.rgb), pos, scale = scale)
                }
            }

            if (routeScanner) foundRouteBlocks.forEach { blockPos ->
                var currentDistCol: Colour? = null
                if (distCols || fillDistCols) {
                    val dist = sqrt(player.distanceToSqr(blockPos.x + 0.5, blockPos.y + 0.5, blockPos.z + 0.5))
                    val hue = (1f - (dist / 64f).coerceIn(0.0, 1.0)).toFloat() * 0.33f
                    currentDistCol = Colour.HSB(hue, 1f, 1f)
                }

                val c = if (distCols && currentDistCol != null) currentDistCol else colour
                val fc =
                    if (fillDistCols && currentDistCol != null) currentDistCol.withAlpha(fillColour.alpha) else fillColour

                ctx.drawStyledBox(style.selected, blockPos.aabb, c, fc, thickness, false)
            }
        }

        lifecycle.on<WorldEvent.Change> {
            worldGeneration++
            scannedChunks.clear()
            foundStructures.clear()
            foundRouteBlocks.clear()
            isDirty = true
        }
    }
}

internal data class CrystalHollowsChunkScan(
    val structures: Map<Structure, List<BlockPos>>,
    val routeBlocks: List<BlockPos>,
)

private fun snapshot(chunk: LevelChunk, scanRoutes: Boolean): (BlockPos) -> BlockState {
    val sections = chunk.sections.map { it.states.copy() }
    val minY = chunk.minY
    val minX = chunk.pos.minBlockX
    val minZ = chunk.pos.minBlockZ
    val borders = mutableMapOf<BlockPos, BlockState>()
    if (scanRoutes) {
        for (y in 0..70) for (i in 0..15) {
            for (pos in listOf(
                BlockPos(minX - 1, y, minZ + i), BlockPos(minX + 16, y, minZ + i),
                BlockPos(minX + i, y, minZ - 1), BlockPos(minX + i, y, minZ + 16),
            )) borders[pos] = chunk.level.getBlockState(pos)
        }
    }
    return { pos ->
        if (pos.x in minX..minX + 15 && pos.z in minZ..minZ + 15) {
            sections[(pos.y - minY) shr 4].get(pos.x and 15, pos.y and 15, pos.z and 15)
        } else borders.getValue(pos)
    }
}

internal fun scanCrystalHollowsChunk(
    chunkPos: ChunkPos,
    scanStructures: Boolean,
    scanRoutes: Boolean,
    stateAt: (BlockPos) -> BlockState
): CrystalHollowsChunkScan {
    val structures = mutableMapOf<Structure, MutableList<BlockPos>>()
    val routeBlocks = mutableListOf<BlockPos>()
    val fromY = if (scanStructures && !scanRoutes) 30 else 0
    val toY = if (scanRoutes && !scanStructures) 70 else 180
    val scanPos = BlockPos.MutableBlockPos()
    for (x in 0..15) {
        for (z in 0..15) {
            for (y in fromY..toY) {
                val pos = scanPos.set(chunkPos.minBlockX + x, y, chunkPos.minBlockZ + z)
                val firstBlock = stateAt(pos).block

                if (scanStructures) Structure.candidates(firstBlock).forEach { structure ->
                    if (!structure.quarter.test(pos)) return@forEach

                    if (structure.canBeMultiple) {
                        val existing = structures[structure].orEmpty()
                        if (existing.any { it.isWithinChunks(pos, 4) }) return@forEach
                    } else {
                        if (structures.containsKey(structure)) return@forEach
                    }

                    if (scanStructure(structure, pos, stateAt)) {
                        val realPos = pos.immutable().offset(structure.xOffset, structure.yOffset, structure.zOffset)
                        structures.getOrPut(structure) { mutableListOf() }.add(realPos)
                    }
                }

                if (scanRoutes) {
                    if (firstBlock != Blocks.COBBLESTONE || y > 70) continue
                    val valid = routeDirections.all {
                        val state = stateAt(pos.relative(it))
                        state.isAir || state.block in glassBlocks
                    }

                    if (valid) routeBlocks.add(pos.immutable())
                }
            }
        }
    }
    return CrystalHollowsChunkScan(structures, routeBlocks)
}

private fun scanStructure(structure: Structure, start: BlockPos, stateAt: (BlockPos) -> BlockState): Boolean {
    if (start.y + structure.blocks.size >= 180) return false

    val pos = BlockPos.MutableBlockPos()
    for (i in 1 until structure.blocks.size) {
        val block = structure.blocks[i] ?: continue

        val state = stateAt(pos.set(start.x, start.y + i, start.z))

        if (!state.block.matchesStructureBlock(block)) return false
    }
    return true
}

private fun Block.matchesStructureBlock(expected: Block): Boolean {
    if (expected != Blocks.SKELETON_SKULL) return this == expected
    return this in skullBlocks
}

private fun BlockPos.isWithinChunks(other: BlockPos, chunks: Int): Boolean {
    val dx = (this.x shr 4) - (other.x shr 4)
    val dz = (this.z shr 4) - (other.z shr 4)
    return dx.absoluteValue <= chunks && dz.absoluteValue <= chunks
}

private val skullBlocks = setOf(
    Blocks.SKELETON_SKULL,
    Blocks.SKELETON_WALL_SKULL,
    Blocks.WITHER_SKELETON_SKULL,
    Blocks.WITHER_SKELETON_WALL_SKULL,
    Blocks.ZOMBIE_HEAD,
    Blocks.ZOMBIE_WALL_HEAD,
    Blocks.PLAYER_HEAD,
    Blocks.PLAYER_WALL_HEAD,
    Blocks.CREEPER_HEAD,
    Blocks.CREEPER_WALL_HEAD,
    Blocks.DRAGON_HEAD,
    Blocks.DRAGON_WALL_HEAD,
    Blocks.PIGLIN_HEAD,
    Blocks.PIGLIN_WALL_HEAD
)

private val glassBlocks by lazy {
    BuiltInRegistries.BLOCK.filter { BuiltInRegistries.BLOCK.getKey(it).toString().contains("glass") }.toSet()
}

private val routeDirections = Direction.entries.filter { it != Direction.DOWN }
