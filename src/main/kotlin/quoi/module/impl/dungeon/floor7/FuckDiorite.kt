package quoi.module.impl.dungeon.floor7

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import quoi.api.events.TickEvent
import quoi.api.events.core.on
import quoi.api.skyblock.Island
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.M7Phases
import quoi.api.skyblock.invoke
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
        Blocks.STAINED_GLASS.white(),
        Blocks.STAINED_GLASS.orange(),
        Blocks.STAINED_GLASS.magenta(),
        Blocks.STAINED_GLASS.lightBlue(),
        Blocks.STAINED_GLASS.yellow(),
        Blocks.STAINED_GLASS.lime(),
        Blocks.STAINED_GLASS.pink(),
        Blocks.STAINED_GLASS.gray(),
        Blocks.STAINED_GLASS.lightGray(),
        Blocks.STAINED_GLASS.cyan(),
        Blocks.STAINED_GLASS.purple(),
        Blocks.STAINED_GLASS.blue(),
        Blocks.STAINED_GLASS.brown(),
        Blocks.STAINED_GLASS.green(),
        Blocks.STAINED_GLASS.red(),
        Blocks.STAINED_GLASS.black()
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
    private val colour by selector("Colour", "None", COLS).visibleIf { oneColour }//.childOf(::oneColour)

    private val pillars = arrayOf(
        BlockPos(46, 169, 41),
        BlockPos(46, 169, 65),
        BlockPos(100, 169, 65),
        BlockPos(100, 169, 41)
    )
    private val pillarColors = intArrayOf(5, 4, 10, 14)

    private val coordinates: Array<Set<BlockPos>> = Array(4) { pillarIndex ->
        val pillar = pillars[pillarIndex]
        buildSet {
            for (dx in (pillar.x - 3)..(pillar.x + 3))
                for (dy in pillar.y..(pillar.y + 37))
                    for (dz in (pillar.z - 3)..(pillar.z + 3))
                        add(BlockPos(dx, dy, dz))
        }
    }

    init {
        on<TickEvent.End> {
            if (Dungeon.getF7Phase() == M7Phases.P2) replaceDiorite()
        }
    }

    private fun replaceDiorite() {
        for ((index, coordinateSet) in coordinates.withIndex()) {
            for (pos in coordinateSet) {
                if (pos.state.block.equalsOneOf(Blocks.DIORITE, Blocks.POLISHED_DIORITE)) {
                    setGlass(pos, index)
                }
            }
        }
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