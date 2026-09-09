package quoi.module.impl.floor7

import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.events.*
import quoi.api.events.core.on
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.Stage
import quoi.api.skyblock.location.Island
import quoi.api.skyblock.location.invoke
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.utils.ChatUtils
import quoi.utils.ChatUtils.modMessage
import quoi.utils.EntityUtils
import quoi.utils.StringUtils
import quoi.utils.WorldUtils.state
import quoi.utils.getDirection
import quoi.utils.render.drawFilledBox
import quoi.utils.skyblock.player.RotationAnimation
import quoi.utils.skyblock.player.RotationUtils.rotate
import quoi.utils.skyblock.player.RotationUtils.rotationTask
import quoi.utils.skyblock.player.interact.AuraManager
import kotlin.random.Random

@Suppress("UNNECESSARY_SAFE_CALL")
object SimonSays : Module(
    "Simon Says",
    desc = "Automatically completes Simon says device.",
    area = Island.Dungeon(7, stage = Stage.S1)
) {
    private val solver by switch("Solver")
    private val firstCol by colourPicker("First colour", Colour.GREEN.withAlpha(0.5f), allowAlpha = true).childOf(::solver)
    private val secondCol by colourPicker("Second colour", Colour.YELLOW.withAlpha(0.5f), allowAlpha = true).childOf(::solver)
    private val thirdCol by colourPicker("Third colour", Colour.RED.withAlpha(0.5f), allowAlpha = true).childOf(::solver)

    private val auto by switch("Auto")
    private val delay by slider("Delay", 200, 50, 500, 10, "Minimum time between clicks. Smooth rotation can run during this cooldown.", unit = "ms").childOf(::auto)
    private val startClicks by slider("Start Clicks", 3, 1, 10, 1, "Number of clicks sent to start the device.").childOf(::auto)
    private val startDelay by slider("Start delay", 150, 50, 1250, 10, "Minimum time between start clicks.", unit = "ms").childOf(::auto)
    private val rotationMode by selector("Rotation mode", RotationAnimation.Mode.None, desc = "None clicks without turning. Natural varies movement; Curve follows a smooth timed turn.").childOf(::auto)
    private val rotationTime by slider("Rotation time", 180, 50, 500, 10, "Base turn duration. Larger turns take longer; natural movement varies.", unit = "ms").childOf(::rotationMode) { it.selected != RotationAnimation.Mode.None }
    private val announceTime by switch("Announce time", desc = "Announces device completion time in party chat")

    private var lastClickTime = 0L
    private var progress = 0
    private var doneFirst = false
    private var doingSS = false
    private val clicks = ArrayList<BlockPos>()
    private val startButton = BlockPos(110, 121, 91)
    private val standBox = AABB(108.0, 120.0, 90.0, 115.0, 125.0, 95.0)

    private var startActive = false
    private var startStep = 0
    private var nextStartClickAt = 0L
    private var startUsesCrosshair = false
    private var lastManualReset = 0L
    private var startTime = 0L
    private var pendingClick: PendingClick? = null
    private var aim: Aim? = null

    init {
        on<WorldEvent.Change> { fullReset() }

        on<DungeonEvent.PhaseComplete> { start(usingCrosshair = true) }

        on<PacketEvent.Sent, ServerboundUseItemOnPacket> {
            val target = pendingClick?.pos
            if (target != null && packet.hitResult.blockPos == target) {
                lastClickTime = System.currentTimeMillis()
                pendingClick = null
                if (target == startButton) {
                    startStep++
                    nextStartClickAt = lastClickTime + startDelay
                    if (startStep >= startClicks) {
                        doingSS = true
                        startTime = lastClickTime
                        startActive = false
                    }
                    return@on
                }
                if (clicks.getOrNull(progress) == target) progress++
                aim = null
            }
            if (startActive || packet.hitResult.blockPos != startButton) return@on

            val isActive = EntityUtils.getEntities<ArmorStand>(standBox) {
                it.distanceTo(player) < 6 && it.displayName?.string?.contains("Device Active") == true
            }.isNotEmpty()
            if (isActive) return@on

            if (System.currentTimeMillis() - lastManualReset > 500) {
                if (auto) {
                    cancel()
                    fullReset()
                    start()
                } else {
                    fullReset()
                    doingSS = true
                    startTime = System.currentTimeMillis()
                }
                lastManualReset = System.currentTimeMillis()
            }
        }

        on<BlockEvent.Update> {
            if (pos.y !in 120..123 || pos.z !in 92..95) return@on

            if (pos.x == 111 && updated.block == Blocks.SEA_LANTERN) {
                val buttonPos = BlockPos(110, pos.y, pos.z)
                if (clicks.getOrNull(0) == buttonPos) {
                    progress = 0
                }

                if (clicks.size == 2 && clicks[0] == buttonPos && !doneFirst) {
                    doneFirst = true
                    clicks.removeFirst()
                }

                if (!clicks.contains(buttonPos)) {
                    progress = 0
                    clicks.add(buttonPos)
                }
                return@on
            }

            if (pos.x == 110) {
                if (updated.block == Blocks.STONE_BUTTON && updated.hasProperty(BlockStateProperties.POWERED) && updated.getValue(
                        BlockStateProperties.POWERED)) {
                    val i = clicks.indexOf(pos)
                    if (i != -1) {
                        progress = i + 1
                    }
                }
            }
        }

        on<TickEvent.Start> {
            if (startActive) {
                if (Dungeon.isDead || player.distanceToSqr(startButton.center) > 25 ||
                    (auto && rotationMode.selected != RotationAnimation.Mode.None && startUsesCrosshair && (mc.hitResult as? BlockHitResult)?.blockPos != startButton)) {
                    fullReset()
                    return@on
                }
                if (!auto) {
                    doingSS = true
                    startTime = System.currentTimeMillis()
                    startActive = false
                } else {
                    pendingClick?.let {
                        if (System.currentTimeMillis() - it.sentAt < 2000) return@on
                        pendingClick = null
                    }
                    if (System.currentTimeMillis() >= nextStartClickAt) sendStartClick()
                }
                return@on
            }

            if (!doingSS) return@on
            if (Dungeon.isDead || player.distanceToSqr(startButton.center) > 25) {
                aim = null
                pendingClick = null
                return@on
            }

            if (!auto) {
                aim = null
                pendingClick = null
            }

            val now = System.currentTimeMillis()
            pendingClick?.let {
                if (now - it.sentAt < 2000) return@on
                pendingClick = null
            }

            val canClick = BlockPos(110, 123, 92).state.block == Blocks.STONE_BUTTON || doneFirst
            if (canClick) {
                if (!doneFirst && clicks.size == 3) clicks.removeFirst()
                doneFirst = true
            }

            if (!auto) return@on

            val next = clicks.getOrNull(progress)
            if (rotationMode.selected != RotationAnimation.Mode.None) {
                val target = if (!doneFirst && startClicks > 1) clicks.getOrNull(1) else next ?: clicks.firstOrNull()
                if (target != null) aimAtButton(target)
            } else aim = null

            if (!canClick || next == null || next.state.block != Blocks.STONE_BUTTON || now - lastClickTime < delay) return@on
            if (rotationMode.selected != RotationAnimation.Mode.None) {
                if (aim?.pos != next || aim?.ready != true) return@on
                val hit = player.pick(5.0, 1.0f, false) as? BlockHitResult
                if (hit?.type != HitResult.Type.BLOCK || hit.blockPos != next) {
                    aim = null
                    return@on
                }
                pendingClick = PendingClick(next, now)
                gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit)
                player.swing(InteractionHand.MAIN_HAND)
            } else {
                pendingClick = PendingClick(next, now)
                clickButton(next)
            }
        }

        on<RenderEvent.World> {
            if (player.distanceToSqr(startButton.center) > 1600) return@on

            if (doingSS) {
                var finished = false
                var active = false
                EntityUtils.getEntities<ArmorStand>(standBox) { it.distanceTo(player) < 6 }.forEach {
                    val name = it.displayName?.string ?: return@forEach
                    if ("Device Active" in name) finished = true
                    if ("Device" in name) active = true
                }

                if (finished) {
                    val time = StringUtils.formatTime(System.currentTimeMillis() - startTime)
                    modMessage("Simon Says took $time")
                    if (announceTime) ChatUtils.command("pc Simon Says took $time")
                    fullReset()
                } else if (!active) fullReset()
            }

            if (!solver || !doingSS) return@on

            for (i in progress until clicks.size) {
                val pos = clicks[i]
                val col = when (i - progress) {
                    0 -> firstCol
                    1 -> secondCol
                    else -> thirdCol
                }

                val box = AABB(
                    pos.x + 0.875, pos.y + 0.375, pos.z + 0.3125,
                    pos.x + 1.0, pos.y + 0.625, pos.z + 0.6875
                )
                ctx.drawFilledBox(box, col)
            }
        }
    }

    private fun fullReset() {
        startActive = false
        startStep = 0
        nextStartClickAt = 0L
        startUsesCrosshair = false
        reset()
    }

    override fun onEnable() = fullReset()
    override fun onDisable() = fullReset()

    private fun reset() {
        clicks.clear()
        progress = 0
        doneFirst = false
        doingSS = false
        startTime = 0L
        pendingClick = null
        aim = null
    }

    private fun start(usingCrosshair: Boolean = false) {
        if (Dungeon.isDead || player.distanceToSqr(startButton.center) > 25 || startActive || doingSS) return
        if (auto && rotationMode.selected != RotationAnimation.Mode.None && usingCrosshair && (mc.hitResult as? BlockHitResult)?.blockPos != startButton) return

        reset()
        startActive = true
        startStep = 0
        startUsesCrosshair = usingCrosshair
        nextStartClickAt = System.currentTimeMillis()
    }

    private fun sendStartClick() {
        if (startStep < startClicks - 1) reset()
        pendingClick = PendingClick(startButton, System.currentTimeMillis())
        clickButton(startButton)
    }

    private fun clickButton(pos: BlockPos) {
        if (Dungeon.isDead || player.distanceToSqr(pos.center) > 25) return
        lastClickTime = System.currentTimeMillis()
        AuraManager.interactBlock(pos)
        player.swing(InteractionHand.MAIN_HAND)
    }

    private fun aimAtButton(pos: BlockPos) {
        aim?.let {
            if (it.pos == pos && it.mode == rotationMode.selected && System.currentTimeMillis() - it.startedAt < 2000) return
        }
        val target = Aim(pos, Vec3(
            pos.x + 0.9375,
            pos.y + 0.5 + Random.nextDouble(-0.045, 0.045),
            pos.z + 0.5 + Random.nextDouble(-0.075, 0.075),
        ), rotationMode.selected)
        aim = target
        val currentHit = player.pick(5.0, 1.0f, false) as? BlockHitResult
        if (currentHit?.type == HitResult.Type.BLOCK && currentHit.blockPos == pos) {
            target.ready = true
            return
        }

        val rotatingPlayer = player
        val motion = RotationAnimation(player.yRot.toDouble(), player.xRot.toDouble(), rotationTime, target.mode)

        rotationTask {
            if (aim !== target || mc.player !== rotatingPlayer || !active || !auto ||
                rotationMode.selected == RotationAnimation.Mode.None ||
                rotationMode.selected != target.mode || !doingSS || Dungeon.isDead || distanceToSqr(pos.center) > 25) return@rotationTask true
            val direction = getDirection(target.point)
            val step = motion.sample(direction.yaw.toDouble(), direction.pitch.toDouble(), mc.options.sensitivity().get())
            rotate(step.yaw, step.pitch)
            target.ready = step.finished
            target.ready
        }
    }

    private class PendingClick(val pos: BlockPos, val sentAt: Long)

    private class Aim(val pos: BlockPos, val point: Vec3, val mode: RotationAnimation.Mode) {
        val startedAt = System.currentTimeMillis()
        var ready = false
    }
}
