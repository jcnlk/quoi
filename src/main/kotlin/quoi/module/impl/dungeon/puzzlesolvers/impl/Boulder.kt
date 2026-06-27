package quoi.module.impl.dungeon.puzzlesolvers.impl

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.world.phys.AABB
import quoi.QuoiMod.logger
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.events.DungeonEvent
import quoi.api.events.PacketEvent
import quoi.api.events.RenderEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.Event
import quoi.api.events.core.on
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonRoom
import quoi.module.impl.dungeon.puzzlesolvers.PuzzleSolvers
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.group.SettingGroup
import quoi.utils.WorldUtils.state
import quoi.utils.render.drawStyledBox
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 * modified OdinFabric (BSD 3-Clause)
 * copyright (c) 2025-2026 odtheking
 * original: https://github.com/odtheking/Odin/blob/main/src/main/kotlin/com/odtheking/odin/features/impl/dungeon/puzzlesolvers/BoulderSolver.kt
 */
object Boulder : SettingGroup(PuzzleSolvers, "Boulder") {
    private val solver by switch("Solver", desc = "Shows the solution for the boulder puzzle.")
    private val showAll by switch("Show all clicks", desc = "Shows all clicks instead of only the next click.").childOf(::solver)
    private val style by selector("Style", "Box", arrayListOf("Box", "Filled box", "Filled"), desc = "Render style to be used.").childOf(::solver)
    private val colour by colourPicker("Colour", Colour.MINECRAFT_GREEN.withAlpha(0.5f), true, desc = "Colour for the boulder solver.").childOf(::solver)

    private data class BoxPosition(val render: AABB, val click: BlockPos)

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val isr = this::class.java.getResourceAsStream("/assets/quoi/puzzles/boulderSolutions.json")?.let { InputStreamReader(it, StandardCharsets.UTF_8) }
    private var solutions: Map<String, List<List<Int>>>
    private var currentPositions = mutableListOf<BoxPosition>()

    init {
        try {
            val text = isr?.readText()
            solutions = gson.fromJson(text, object : TypeToken<Map<String, List<List<Int>>>>() {}.type)
            isr?.close()
        } catch (e: Exception) {
            logger.error("Error loading boulder solutions", e)
            solutions = emptyMap()
        }

        on<DungeonEvent.Room.Enter> {
            onRoomEnter(room)
        }

        on<RenderEvent.World> {
            if (solver) onRenderWorld(ctx, showAll, style.selected, colour)
        }

        on<PacketEvent.Sent, ServerboundUseItemOnPacket> {
            if (solver) onInteract(packet)
        }

        on<WorldEvent.Change> {
            reset()
        }
    }

    override fun shouldHandle(event: Event): Boolean {
        if (!super.shouldHandle(event)) return false
        if (event is DungeonEvent.Room.Enter || event is WorldEvent.Change) return true
        return Dungeon.currentRoom?.name == "Boulder"
    }

    fun onRoomEnter(room: OdonRoom?) = with(room) {
        if (this?.name != "Boulder") return@with reset()

        var identifier = ""
        for (z in 24 downTo 9 step 3) {
            for (x in 24 downTo 6 step 3) {
                identifier += if (getRealCoords(BlockPos(x, 66, z)).state.isAir) "0" else "1"
            }
        }

        currentPositions = solutions[identifier]?.map { solution ->
            val render = getRealCoords(BlockPos(solution[0], 65, solution[1]))
            val click = getRealCoords(BlockPos(solution[2], 65, solution[3]))
            BoxPosition(AABB(render), click)
        }?.toMutableList() ?: mutableListOf()
    }

    fun onRenderWorld(ctx: WorldRenderContext, showAllClicks: Boolean, style: String, colour: Colour) {
        if (Dungeon.currentRoom?.name != "Boulder" || currentPositions.isEmpty()) return

        if (showAllClicks) currentPositions.forEach {
            ctx.drawStyledBox(style, it.render, colour, depth = false)
        } else currentPositions.firstOrNull()?.let {
            ctx.drawStyledBox(style, it.render, colour, depth = false)
        }
    }

    fun onInteract(packet: ServerboundUseItemOnPacket) {
        currentPositions.remove(currentPositions.firstOrNull { it.click == packet.hitResult.blockPos })
    }

    fun reset() {
        currentPositions = mutableListOf()
    }
}
