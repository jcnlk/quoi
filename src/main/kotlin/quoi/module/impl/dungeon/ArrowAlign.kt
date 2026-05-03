package quoi.module.impl.dungeon

import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ServerboundInteractPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.decoration.ItemFrame
import net.minecraft.world.item.Items
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.Vec3
import quoi.api.colour.Colour
import quoi.api.events.MouseEvent
import quoi.api.events.RenderEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.skyblock.Island
import quoi.api.skyblock.dungeon.Dungeon.inP3
import quoi.api.skyblock.dungeon.Dungeon.isDead
import quoi.api.skyblock.invoke
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.utils.ChatUtils.literal
import quoi.utils.EntityUtils
import quoi.utils.addVec
import quoi.utils.render.drawText

// Kyleen
object ArrowAlign : Module(
    "Arrow Align",
    desc = "Shows the solution for arrow align device.",
    area = Island.Dungeon(7, inBoss = true)
) {
    private val solver by switch("Solver")
    private val blockWrongClicks by switch("Block wrong clicks", desc = "Prevents clicking solved arrows. Sneak to disable.").childOf(::solver)
    private val auto by switch("Auto")
    private val range by slider("Range", 5.0, 2.1, 6.5, 0.1, desc = "Maximum range for the align aura.").childOf(::auto)
    private val triggerbot by switch("Triggerbot", desc = "Automatically clicks the correct arrow when you look at it.")
    private val triggerbotDelay by slider("Triggerbot delay", 200, 70, 500, 10, unit = "ms").childOf(::triggerbot)
    private val sneakToDisableTriggerbot by switch("Sneak to disable").childOf(::triggerbot)

    private val deviceStandLocation = BlockPos(0, 120, 77)
    private val deviceCorner = BlockPos(-2, 120, 75)

    private val recentClicks = LongArray(25)
    private val persistentFrames = arrayOfNulls<CachedFrame>(25)
    private val renderList = mutableListOf<Pair<Vec3, Int>>()
    private var lastTriggerbotClick = 0L

    private data class CachedFrame(val entity: ItemFrame, var rotation: Int)

    private val solutions = listOf(
        listOf(7, 7, 7, 7, null, 1, null, null, null, null, 1, 3, 3, 3, 3, null, null, null, null, 1, null, 7, 7, 7, 1),
        listOf(null, null, null, null, null, 1, null, 1, null, 1, 1, null, 1, null, 1, 1, null, 1, null, 1, null, null, null, null, null),
        listOf(5, 3, 3, 3, null, 5, null, null, null, null, 7, 7, null, null, null, 1, null, null, null, null, 1, 3, 3, 3, null),
        listOf(null, null, null, null, null, null, 1, null, 1, null, 7, 1, 7, 1, 3, 1, null, 1, null, 1, null, null, null, null, null),
        listOf(null, null, 7, 7, 5, null, 7, 1, null, 5, null, null, null, null, null, null, 7, 5, null, 1, null, null, 7, 7, 1),
        listOf(7, 7, null, null, null, 1, null, null, null, null, 1, 3, 3, 3, 3, null, null, null, null, 1, null, null, null, 7, 1),
        listOf(5, 3, 3, 3, 3, 5, null, null, null, 1, 7, 7, null, null, 1, null, null, null, null, 1, null, 7, 7, 7, 1),
        listOf(7, 7, null, null, null, 1, null, null, null, null, 1, 3, null, 7, 5, null, null, null, null, 5, null, null, null, 3, 3),
        listOf(null, null, null, null, null, 1, 3, 3, 3, 3, null, null, null, null, 1, 7, 7, 7, 7, 1, null, null, null, null, null)
    )

    init {
        on<TickEvent.End> {
            if (!isDead && (solver || auto || triggerbot)) handleArrowAlign()
        }

        on<RenderEvent.World> {
            if (!solver || renderList.isEmpty()) return@on
            renderList.forEach { (pos, clicks) ->
                val col =
                    if (clicks < 3) Colour.GREEN
                    else if (clicks < 5) Colour.YELLOW
                    else Colour.RED

                ctx.drawText(literal(clicks.toString()).withColor(col.rgb), pos, scale = 1.0f, depth = true)
            }
        }

        on<MouseEvent.Click> {
            if (!solver || !blockWrongClicks || button != 1 || !state || player.isShiftKeyDown) return@on

            val targetFrame = (mc.hitResult as? EntityHitResult)?.entity as? ItemFrame ?: return@on
            if (targetFrame.item.item != Items.ARROW) return@on

            val frameIndex = getFrameIndex(targetFrame.blockPosition())
            if (frameIndex !in 0..24) return@on

            val currentFrames = getCurrentFrames()
            val frame = currentFrames[frameIndex] ?: return@on
            if (frame.entity.id != targetFrame.id) return@on

            val solution = findSolution(currentFrames) ?: return@on
            if (clicksNeeded(frame, solution[frameIndex]) > 0) return@on

            cancel()
        }

        on<WorldEvent.Change> {
            persistentFrames.fill(null)
            recentClicks.fill(0L)
            renderList.clear()
            lastTriggerbotClick = 0L
        }
    }

    private fun handleArrowAlign() {
        if (player.distanceToSqr(Vec3.atCenterOf(deviceStandLocation)) > 100) {
            renderList.clear()
            return
        }

        val currentFrames = getCurrentFrames()
        val solution = findSolution(currentFrames) ?: return

        renderList.clear()
        for (i in 0 until 25) {
            val frame = currentFrames[i] ?: continue
            val needed = clicksNeeded(frame, solution[i])
            if (needed > 0) {
                renderList.add(frame.entity.position().addVec(x = 0.1) to needed)
            }
        }

        if (auto) handleAuto(currentFrames, solution)
        if (triggerbot) handleTriggerbot(currentFrames, solution)
    }

    private fun handleAuto(currentFrames: Array<CachedFrame?>, solution: List<Int?>) {

        val closest = (0 until 25).minByOrNull { i ->
            val f = currentFrames[i] ?: return@minByOrNull Double.MAX_VALUE
            if (clicksNeeded(f, solution[i]) <= 0) Double.MAX_VALUE else player.distanceToSqr(f.entity)
        }

        for (i in 0 until 25) {
            val frame = currentFrames[i] ?: continue
            var clicksNeeded = clicksNeeded(frame, solution[i])

            if (clicksNeeded <= 0) continue
            if (frame.entity.distanceToSqr(player) > range * range) continue

            if (!inP3 && i == closest) {
                clicksNeeded--
            }

            if (clicksNeeded > 0) {
                recentClicks[i] = System.currentTimeMillis()
                repeat(clicksNeeded) {
                    clickFrame(frame)
                }
                break
            }
        }
    }

    private fun handleTriggerbot(currentFrames: Array<CachedFrame?>, solution: List<Int?>) {
        if ((sneakToDisableTriggerbot && player.isShiftKeyDown) || System.currentTimeMillis() - lastTriggerbotClick < triggerbotDelay) return

        val targetFrame = (mc.hitResult as? EntityHitResult)?.entity as? ItemFrame ?: return
        val frameIndex = getFrameIndex(targetFrame.blockPosition())
        if (frameIndex !in 0..24) return

        val frame = currentFrames[frameIndex] ?: return
        if (frame.entity.id != targetFrame.id) return

        if (clicksNeeded(frame, solution[frameIndex]) <= 0) return

        recentClicks[frameIndex] = System.currentTimeMillis()
        lastTriggerbotClick = recentClicks[frameIndex]
        clickFrame(frame)
    }

    private fun findSolution(currentFrames: Array<CachedFrame?>): List<Int?>? {
        for (solution in solutions) {
            var match = true
            for (i in 0 until 25) {
                if ((solution[i] == null) != (currentFrames[i] == null)) {
                    match = false
                    break
                }
            }
            if (match) return solution
        }

        return null
    }

    private fun clicksNeeded(frame: CachedFrame, targetRotation: Int?): Int {
        if (targetRotation == null) return 0
        return (targetRotation - frame.rotation + 8) % 8
    }

    private fun clickFrame(frame: CachedFrame) {
        frame.rotation = (frame.rotation + 1) % 8
        mc.connection?.send(
            ServerboundInteractPacket.createInteractionPacket(
                frame.entity,
                player.isShiftKeyDown,
                InteractionHand.MAIN_HAND,
                Vec3(0.03125, 0.0, 0.0)
            )
        )

        mc.connection?.send(
            ServerboundInteractPacket.createInteractionPacket(
                frame.entity,
                player.isShiftKeyDown,
                InteractionHand.MAIN_HAND
            )
        )
    }

    private fun getFrameIndex(pos: BlockPos): Int {
        if (pos.x != deviceCorner.x || pos.y !in deviceCorner.y..deviceCorner.y + 4 || pos.z !in deviceCorner.z..deviceCorner.z + 4) {
            return -1
        }

        return (pos.y - deviceCorner.y) + (pos.z - deviceCorner.z) * 5
    }

    private fun getCurrentFrames(): Array<CachedFrame?> {
        val scanBox = AABB(deviceCorner).expandTowards(5.0, 5.0, 5.0).inflate(1.0)
        val entities = EntityUtils.getEntities<ItemFrame>(scanBox) {
            it.item.item == Items.ARROW
        }

        val frames = arrayOfNulls<CachedFrame>(25)
        val start = deviceCorner
        val currentTime = System.currentTimeMillis()

        for (dz in 0 until 5) {
            for (dy in 0 until 5) {
                val index = dy + dz * 5

                if (persistentFrames[index] != null && currentTime - recentClicks[index] < 1000) {
                    frames[index] = persistentFrames[index]
                    continue
                }

                val targetPos = BlockPos(start.x, start.y + dy, start.z + dz)

                val frameEntity = entities.find { it.blockPosition() == targetPos }

                if (frameEntity != null) {
                    val cached = CachedFrame(frameEntity, frameEntity.rotation)
                    persistentFrames[index] = cached
                    frames[index] = cached
                } else {
                    persistentFrames[index] = null
                }
            }
        }
        return frames
    }
}
