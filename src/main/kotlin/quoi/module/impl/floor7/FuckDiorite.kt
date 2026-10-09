package quoi.module.impl.floor7

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import quoi.api.events.BlockEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.on
import quoi.api.skyblock.dungeon.Floor7
import quoi.api.skyblock.dungeon.enums.Phase
import quoi.api.skyblock.location.Island
import quoi.api.skyblock.location.invoke
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.visibleIf
import quoi.utils.WorldUtils.state
import quoi.utils.equalsOneOf

// Kyleen

/**
 * modified Odin (BSD 3-Clause)
 * copyright (c) 2026 odtheking
 * original: https://github.com/odtheking/Odin/blob/main/odinclient/src/main/kotlin/me/odinclient/features/impl/floor7/FuckDiorite.kt
 */
object FuckDiorite : Module(
    "Fuck Diorite",
    desc = "Replaces the pillars in the storm fight with glass.",
    area = Island.Dungeon(7, inBoss = true)
) {
    private val GLASS_STATE = Blocks.GLASS.defaultBlockState()

    private val STAINED_GLASS_BLOCKS = arrayOf(
        Blocks.WHITE_STAINED_GLASS,
        Blocks.ORANGE_STAINED_GLASS,
        Blocks.MAGENTA_STAINED_GLASS,
        Blocks.LIGHT_BLUE_STAINED_GLASS,
        Blocks.YELLOW_STAINED_GLASS,
        Blocks.LIME_STAINED_GLASS,
        Blocks.PINK_STAINED_GLASS,
        Blocks.GRAY_STAINED_GLASS,
        Blocks.LIGHT_GRAY_STAINED_GLASS,
        Blocks.CYAN_STAINED_GLASS,
        Blocks.PURPLE_STAINED_GLASS,
        Blocks.BLUE_STAINED_GLASS,
        Blocks.BROWN_STAINED_GLASS,
        Blocks.GREEN_STAINED_GLASS,
        Blocks.RED_STAINED_GLASS,
        Blocks.BLACK_STAINED_GLASS
    )

    val COLS = listOf(
        "NONE",
        "WHITE",
        "ORANGE",
        "MAGENTA",
        "LIGHT_BLUE",
        "YELLOW",
        "LIME",
        "PINK",
        "GRAY",
        "LIGHT_GRAY",
        "CYAN",
        "PURPLE",
        "BLUE",
        "BROWN",
        "GREEN",
        "RED",
        "BLACK"
    )

    private val oneColour by switch("One colour", desc = "Swaps the diorite to one colour rather than pillar based colour.")
    private val colour by selector("Colour", "None", COLS).visibleIf { oneColour }

    private val pillars = arrayOf(
        BlockPos(46, 169, 41),
        BlockPos(46, 169, 65),
        BlockPos(100, 169, 65),
        BlockPos(100, 169, 41)
    )
    private val pillarColors = intArrayOf(5, 4, 10, 14)

    private val dirtyPillars = BooleanArray(4) { true }
    private var wasP2 = false

    init {
        on<TickEvent.End> {
            val inP2 = Floor7.inPhaseAt(Phase.P2)
            if (inP2) {
                if (!wasP2) dirtyPillars.fill(true)
                replaceDiorite()
            }
            wasP2 = inP2
        }

        on<BlockEvent.Update> {
            if (updated.block != Blocks.DIORITE && updated.block != Blocks.POLISHED_DIORITE) return@on
            pillars.forEachIndexed { index, pillar ->
                if (pos.x in pillar.x - 3..pillar.x + 3 && pos.y in pillar.y..pillar.y + 37 && pos.z in pillar.z - 3..pillar.z + 3) {
                    dirtyPillars[index] = true
                }
            }
        }

        on<WorldEvent.Chunk.Load> {
            pillars.forEachIndexed { index, pillar ->
                if (chunk.pos.x in ((pillar.x - 3) shr 4)..((pillar.x + 3) shr 4) &&
                    chunk.pos.z in ((pillar.z - 3) shr 4)..((pillar.z + 3) shr 4)) {
                    dirtyPillars[index] = true
                }
            }
        }

        on<WorldEvent.Change> {
            wasP2 = false
        }
    }

    private fun replaceDiorite() {
        for ((index, pillar) in pillars.withIndex()) {
            if (!dirtyPillars[index]) continue
            dirtyPillars[index] = false
            for (pos in BlockPos.betweenClosed(pillar.x - 3, pillar.y, pillar.z - 3, pillar.x + 3, pillar.y + 37, pillar.z + 3)) {
                if (pos.state.block.equalsOneOf(Blocks.DIORITE, Blocks.POLISHED_DIORITE)) {
                    setGlass(pos.immutable(), index)
                }
            }
        }
    }

    override fun onDisable() {
        wasP2 = false
    }

    private fun setGlass(pos: BlockPos, pillarIndex: Int) {
        val newState = when {
            !oneColour -> STAINED_GLASS_BLOCKS[pillarColors[pillarIndex]].defaultBlockState()
            colour.index != 0 -> STAINED_GLASS_BLOCKS[colour.index - 1].defaultBlockState()
            else -> GLASS_STATE
        }

        level.setBlock(pos, newState, 3)
    }
}
