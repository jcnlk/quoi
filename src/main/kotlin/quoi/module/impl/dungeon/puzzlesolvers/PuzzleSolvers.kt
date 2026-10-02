package quoi.module.impl.dungeon.puzzlesolvers

import com.google.gson.reflect.TypeToken
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.level.block.ButtonBlock
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import quoi.QuoiMod.logger
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.location.Island
import quoi.api.skyblock.location.invoke
import quoi.config.ConfigSystem.gson
import quoi.module.Module
import quoi.module.impl.dungeon.autoclear.executor.ClearExecutor
import quoi.module.impl.dungeon.autoclear.impl.InteractiveMap
import quoi.module.impl.dungeon.puzzlesolvers.impl.BlazeSolver
import quoi.module.impl.dungeon.puzzlesolvers.impl.BoulderSolver
import quoi.module.impl.dungeon.puzzlesolvers.impl.CreeperBeamsSolver
import quoi.module.impl.dungeon.puzzlesolvers.impl.IceFillSolver
import quoi.module.impl.dungeon.puzzlesolvers.impl.IcePathSolver
import quoi.module.impl.dungeon.puzzlesolvers.impl.QuizSolver
import quoi.module.impl.dungeon.puzzlesolvers.impl.TeleportMazeSolver
import quoi.module.impl.dungeon.puzzlesolvers.impl.ThreeWeirdosSolver
import quoi.module.impl.dungeon.puzzlesolvers.impl.TicTacToeSolver
import quoi.module.impl.dungeon.puzzlesolvers.impl.WaterBoardSolver
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.UIComponent.Companion.visibleIf

@Suppress("unused_expression")
object PuzzleSolvers : Module(
    "Puzzle Solvers",
    desc = "Displays solutions and automatically completes dungeon puzzles: Ice Fill, Teleport Maze, Quiz, Three Weirdos, Tic Tac Toe, Water Board, Creeper Beams, Blaze, Ice Path, and Boulder.",
    area = Island.Dungeon(inClear = true)
) {
    init {
        IceFillSolver
        TeleportMazeSolver
        QuizSolver
        ThreeWeirdosSolver
        TicTacToeSolver
        WaterBoardSolver
        CreeperBeamsSolver
        BlazeSolver
        IcePathSolver
        BoulderSolver
    }

    private val bowDropdown by text("Bow settings").visibleIf { CreeperBeamsSolver.auto || CreeperBeamsSolver.triggerbot || BlazeSolver.auto || BlazeSolver.triggerbot || IcePathSolver.auto }
    val shootCd by slider("Shoot cooldown", 500L, 250L, 1000L, 50L, unit = "ms").childOf(::bowDropdown)
    val missCd by slider("Miss cooldown", 550L, 300L, 1050L, 50L, unit = "ms").childOf(::bowDropdown)

    val screenBlocksAuto: Boolean
        get() = mc.screen != null && !InteractiveMap.mapOpen

    fun triggerBlock(pos: BlockPos): Boolean {
        if (mc.screen != null || Dungeon.isDead || ClearExecutor.active) return false
        val hit = mc.hitResult as? BlockHitResult ?: return false
        if (hit.type != HitResult.Type.BLOCK || hit.blockPos != pos) return false
        val state = level.getBlockState(pos)
        if (state.block is ButtonBlock && state.getValue(ButtonBlock.POWERED)) return false
        val range = player.blockInteractionRange()
        if (player.eyePosition.distanceToSqr(hit.location) > range * range) return false
        if (!gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit).consumesAction()) return false
        player.swing(InteractionHand.MAIN_HAND)
        return true
    }

    inline fun <reified T> loadSolution(file: String, fallback: T): T {
        val path = "/assets/quoi/puzzles/$file"
        return try {
            val isr = PuzzleSolvers::class.java.getResourceAsStream(path)?.let {
                InputStreamReader(
                    it,
                    StandardCharsets.UTF_8
                )
            }
            if (isr != null) {
                val text = isr.readText()
                isr.close()
                gson.fromJson(text, object : TypeToken<T>() {}.type) ?: fallback
            } else fallback
        } catch (e: Exception) {
            logger.error("Error loading $path", e)
            fallback
        }
    }
}
