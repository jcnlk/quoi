package quoi.module.impl.floor7

import quoi.utils.center

import net.minecraft.core.BlockPos
import net.minecraft.util.Mth.wrapDegrees
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import quoi.api.abobaui.dsl.ms
import quoi.api.animations.Animation
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.events.*
import quoi.api.events.core.on
import quoi.api.events.core.Priority
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
import quoi.utils.skyblock.player.RotationUtils.rotate
import quoi.utils.skyblock.player.RotationUtils.rotationTask
import quoi.utils.skyblock.player.PlayerUtils.rightClick
import quoi.utils.skyblock.player.interact.AuraManager
import kotlin.math.hypot
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
    private val autoStart by switch("Auto Start", desc = "Starts the skip on Goldor's opening message while you aim at the start button.")
    private val startClicks by slider("Start Clicks", 3, 1, 10, 1, "Number of clicks sent to start the device.")
    private val startClickDelay by slider("Start Click Delay", 3, 1, 25, 1, "Client ticks between start clicks.", unit = "t")
    private val smoothRotate by switch("Smooth rotate").childOf(::auto)
    private val rotationMode by selector("Rotation mode", RotationMode.Natural, desc = "Natural varies movement while turning. Curve follows a timed animation.").childOf(::smoothRotate)
    private val rotateStyle by selector("Style", Animation.Style.SmootherStep).childOf(::rotationMode) { it.selected == RotationMode.Curve }
    private val rotationSpeed by slider("Rotation speed", 40, 20, 60, 1, "Controls how quickly the camera turns.").childOf(::rotationMode) { it.selected == RotationMode.Natural }
    private val tremorFrequency by slider("Tremor frequency", 40, 0, 80, 1, "Frequency of small aim movements.").childOf(::rotationMode) { it.selected == RotationMode.Natural }
    private val movementVariation by slider("Movement variation", 10, 5, 15, 1, "Variation in movement during a turn.").childOf(::rotationMode) { it.selected == RotationMode.Natural }
    private val rotationTime by slider("Rotation time", 180, 50, 500, 10, "Duration of a 30-degree turn when distance scaling is enabled.", unit = "ms").childOf(::rotationMode) { it.selected == RotationMode.Curve }
    private val scaleRotationTime by switch("Scale turn duration", true, desc = "Uses shorter rotations for nearby buttons and longer rotations for larger turns.").childOf(::rotationMode) { it.selected == RotationMode.Curve }
    private val minimumTurnTime by slider("Minimum turn time", 140, 50, 500, 10, "Minimum duration of a scaled turn, including very small corrections.", unit = "ms").childOf(::scaleRotationTime)
    private val returnToFirst by switch("Aim at first button", true, desc = "Turns back to the first sequence button after the final click, ready for the next round.").childOf(::smoothRotate)
    private val aimVariation by slider("Aim variation", 60, 0, 100, 5, "Varies the aim point inside the button face. Keeps the chosen point until clicked; 0 uses the center.", unit = "%").childOf(::smoothRotate)
    private val dontCheck by switch("Faster SS?").childOf(::auto)

    private val announceTime by switch("Announce time", desc = "Announces device completion time in party chat")
    private val forceDevice by switch("Force device")
    @Suppress("unused")
    private val resetSS by button("Reset") { fullReset() }

    private var lastClickTime = 0L
    private var progress = 0
    private var doneFirst = false
    private var doingSS = false
    private var clicked = false
    private var clicks = ArrayList<BlockPos>()
    private var clickedButton: BlockPos? = null
    private var allButtons = ArrayList<BlockPos>()
    private val startButton = BlockPos(110, 121, 91)
    private val standBox = AABB(108.0, 120.0, 90.0, 115.0, 125.0, 95.0)

    private var startActive = false
    private var startStep = 0
    private var startTicksRemaining = 0
    private var startUsesCrosshair = false
    private var pendingStartClick = false
    private var lastManualReset = 0L
    private var startTime = 0L
    private var smoothClickInFlight = false
    private var rotationReady = false
    private var pendingSolverButton: BlockPos? = null
    private var solverGeneration = 0
    private var pendingClickTime = 0L
    private var returnAimRequested = false
    private var returnAimTarget: BlockPos? = null
    private var returnAimStarted = 0L
    private var startAimPending = false
    private var startAimTarget: BlockPos? = null
    private var startAimInFlight = false
    private var startAimStarted = 0L
    private val buttonAimPoints = mutableMapOf<BlockPos, Vec3>()

    init {
        on<WorldEvent.Change> {
            fullReset()
        }

        on<ChatEvent.Packet>(priority = Priority.LOWEST, acceptCancelled = true) {
            if (!autoStart || unformatted != "[BOSS] Goldor: Who dares trespass into my domain?") return@on
            val currentLevel = mc.level
            mc.execute {
                if (!active || mc.level !== currentLevel || !autoStart) return@execute
                if ((mc.hitResult as? BlockHitResult)?.blockPos != startButton) return@execute
                start(usingCrosshair = true)
            }
        }

        on<PacketEvent.Sent, ServerboundUseItemOnPacket> {
            val target = pendingSolverButton
            if (target != null && packet.hitResult.blockPos == target) {
                startAimPending = false
                if (clicks.getOrNull(progress) == target) progress++
                lastClickTime = System.currentTimeMillis()
                clickedButton = target
                buttonAimPoints.remove(target)
                pendingSolverButton = null
                smoothClickInFlight = false
                rotationReady = false
                if (clicks.isNotEmpty() && progress >= clicks.size) returnAimRequested = true
            }
            if (pendingStartClick && packet.hitResult.blockPos == startButton) {
                pendingStartClick = false
                return@on
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
                    if (allButtons.isNotEmpty()) allButtons.removeFirst()
                }

                if (!clicks.contains(buttonPos)) {
                    progress = 0
                    clicks.add(buttonPos)
                    allButtons.add(buttonPos)
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
            if (startAimTarget != null && (!auto || !smoothRotate || !doingSS || Dungeon.isDead ||
                    player.distanceToSqr(startAimTarget!!.center) > 25 ||
                    (startAimInFlight && System.currentTimeMillis() - startAimStarted > 2000))) {
                if (startAimInFlight) solverGeneration++
                startAimTarget = null
                startAimInFlight = false
            }
            if (returnAimTarget != null && (!auto || !smoothRotate || !returnToFirst || !doingSS || Dungeon.isDead ||
                    System.currentTimeMillis() - returnAimStarted > 2000)) {
                returnAimTarget = null
                solverGeneration++
            }
            if (returnAimRequested) {
                returnAimRequested = false
                val first = clicks.firstOrNull()
                if (auto && smoothRotate && returnToFirst && doingSS && !Dungeon.isDead &&
                    pendingSolverButton == null && first != null && player.distanceToSqr(first.center) <= 25) {
                    returnAimTarget = first
                    returnAimStarted = System.currentTimeMillis()
                    aimAtButton(first) { returnAimTarget = null }
                }
            }
            if (returnAimTarget != null) return@on
            if (pendingSolverButton != null && (!auto || !doingSS || Dungeon.isDead ||
                    System.currentTimeMillis() - pendingClickTime > 2000)) {
                pendingSolverButton = null
                smoothClickInFlight = false
                rotationReady = false
                solverGeneration++
            }
            if (smoothClickInFlight && rotationReady) {
                if (System.currentTimeMillis() - lastClickTime < delay) return@on
                val target = pendingSolverButton
                if (target != null && active && auto && doingSS && !Dungeon.isDead &&
                    clicks.getOrNull(progress) == target && target.state.block == Blocks.STONE_BUTTON) {
                    val hit = player.pick(5.0, 1.0f, false) as? BlockHitResult
                    if (hit?.type == HitResult.Type.BLOCK && hit.blockPos == target) {
                        gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit)
                        player.swing(InteractionHand.MAIN_HAND)
                    }
                }
                pendingSolverButton = null
                smoothClickInFlight = false
                rotationReady = false
                lastClickTime = System.currentTimeMillis()
                return@on
            }
            if (pendingSolverButton != null) return@on
            if (startActive) {
                if (Dungeon.isDead || player.distanceToSqr(startButton.center) > 25 ||
                    (startUsesCrosshair && (!autoStart || (mc.hitResult as? BlockHitResult)?.blockPos != startButton)) ||
                    (!startUsesCrosshair && !auto)) {
                    fullReset()
                    return@on
                }
                if (--startTicksRemaining <= 0) sendStartClick()
                return@on
            }

            if (!doingSS || (!(auto && smoothRotate) && System.currentTimeMillis() - lastClickTime < delay)) return@on
            if (player.distanceToSqr(startButton.center) > 25) return@on

            if (startAimPending && auto && smoothRotate && !Dungeon.isDead) {
                val first = clicks.getOrNull(if (!doneFirst && startClicks > 1) 1 else 0)
                if (first != null && first != startAimTarget) {
                    startAimTarget = first
                    startAimInFlight = true
                    startAimStarted = System.currentTimeMillis()
                    aimAtButton(first) {
                        startAimInFlight = false
                        if (pendingSolverButton == first) rotationReady = true
                    }
                }
            }

            val canClick = BlockPos(110, 123, 92).state.block == Blocks.STONE_BUTTON

            if (canClick || ((dontCheck && auto) && doneFirst)) {

                if (!doneFirst && clicks.size == 3) {
                    clicks.removeAt(0)
                    if (allButtons.isNotEmpty()) allButtons.removeAt(0)
                }

                doneFirst = true

                if (auto) clicks.getOrNull(progress)?.let { nextPos ->
                    if (nextPos.state.block == Blocks.STONE_BUTTON) {
                        clickButton(nextPos, advanceProgress = true)
                    }
                }
            }
        }

        on<RenderEvent.World> {
            if (player.distanceToSqr(startButton.center) > 1600) return@on

            if (doingSS) {
                var finished = false
                var active = forceDevice
                EntityUtils.getEntities<ArmorStand>(standBox) { it.distanceTo(player) < 6 }.forEach {
                    val name = it.displayName?.string ?: return@forEach
                    if ("Device Active" in name) finished = true
                    if ("Device" in name) active = true
                }

                if (finished) {
                    val time = StringUtils.formatTime(System.currentTimeMillis() - startTime)
                    ChatUtils.modMessage("Simon Says took $time")
                    if (announceTime) ChatUtils.command("pc Simon Says took $time")
                    fullReset()
                } else if (!active) fullReset()

                if (System.currentTimeMillis() - lastClickTime > delay) clickedButton = null
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
        startTicksRemaining = 0
        startUsesCrosshair = false
        pendingStartClick = false
        reset()
    }

    override fun onEnable() {
        fullReset()
    }

    override fun onDisable() {
        fullReset()
    }

    private fun reset() {
        allButtons.clear()
        clicks.clear()
        progress = 0
        doneFirst = false
        doingSS = false
        clicked = false
        startTime = 0L
        smoothClickInFlight = false
        rotationReady = false
        pendingSolverButton = null
        pendingClickTime = 0L
        returnAimRequested = false
        returnAimTarget = null
        returnAimStarted = 0L
        startAimPending = false
        startAimTarget = null
        startAimInFlight = false
        startAimStarted = 0L
        buttonAimPoints.clear()
        solverGeneration++
    }

    private fun start(usingCrosshair: Boolean = false) {
        if (Dungeon.isDead) return
        if (player.distanceToSqr(startButton.center) > 25) return

        if (!startActive && !doingSS) {
            reset()
            clicked = true

            startActive = true
            startStep = 0
            startUsesCrosshair = usingCrosshair
            startTicksRemaining = 1
        }

    }

    private fun sendStartClick() {
        reset()
        if (startUsesCrosshair) {
            pendingStartClick = true
            player.rightClick()
        } else clickButton(startButton)
        startStep++
        startTicksRemaining = startClickDelay
        if (startStep >= startClicks) {
            doingSS = true
            startTime = System.currentTimeMillis()
            startActive = false
            startAimPending = true
        }
    }

    private fun clickButton(pos: BlockPos, advanceProgress: Boolean = false) {
        if (Dungeon.isDead || player.distanceToSqr(pos.center) > 25) return
        if (advanceProgress && pendingSolverButton != null) return

        val shouldSmooth = auto && smoothRotate && pos != startButton
        if (!shouldSmooth) lastClickTime = System.currentTimeMillis()
        if (advanceProgress) {
            pendingSolverButton = pos
            pendingClickTime = System.currentTimeMillis()
        }

        if (shouldSmooth) {
            if (smoothClickInFlight) return

            smoothClickInFlight = true
            rotationReady = false
            if (startAimInFlight && startAimTarget == pos) return
            startAimInFlight = false
            aimAtButton(pos) { if (pendingSolverButton == pos) rotationReady = true }
        } else {
            clickedButton = pos
            AuraManager.interactBlock(pos)
            player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false)
        }

    }

    private fun aimAtButton(pos: BlockPos, onFinish: () -> Unit) {
        val generation = ++solverGeneration
        val currentHit = player.pick(5.0, 1.0f, false) as? BlockHitResult
        if (currentHit?.type == HitResult.Type.BLOCK && currentHit.blockPos == pos) {
            onFinish()
            return
        }

        val rotatingPlayer = player
        val startYaw = rotatingPlayer.yRot
        val startPitch = rotatingPlayer.xRot
        val aimPoint = buttonAimPoints.getOrPut(pos) {
            val scale = aimVariation / 100.0
            Vec3(
                pos.x + 0.9375,
                pos.y + 0.5 + Random.nextDouble(-0.075, 0.075) * scale,
                pos.z + 0.5 + Random.nextDouble(-0.125, 0.125) * scale,
            )
        }
        val targetDirection = getDirection(aimPoint)
        val deltaYaw = wrapDegrees(targetDirection.yaw - startYaw)
        val deltaPitch = targetDirection.pitch - startPitch
        val duration = SimonSaysRotationTiming.duration(rotationTime, minimumTurnTime, hypot(deltaYaw, deltaPitch), scaleRotationTime)
        if (rotationMode.selected == RotationMode.Natural) {
            val motion = SimonSaysNaturalRotation(startYaw.toDouble(), startPitch.toDouble(), rotationSpeed.toDouble(), movementVariation.toDouble(), tremorFrequency.toDouble())
            rotationTask {
                if (generation != solverGeneration || mc.player !== rotatingPlayer || !active || !auto ||
                    !smoothRotate || rotationMode.selected != RotationMode.Natural || !doingSS || Dungeon.isDead || distanceToSqr(pos.center) > 25) return@rotationTask true
                val target = getDirection(aimPoint)
                val step = motion.sample(target.yaw.toDouble(), target.pitch.toDouble(), mc.options.sensitivity().get(), System.nanoTime(), mc.fps)
                rotate(step.yaw, step.pitch)
                if (step.finished) onFinish()
                step.finished
            }
            return
        }
        val animation = Animation(duration.ms, rotateStyle.selected)

        rotationTask {
            if (generation != solverGeneration || mc.player !== rotatingPlayer || !active || !auto ||
                !smoothRotate || rotationMode.selected != RotationMode.Curve || !doingSS || Dungeon.isDead || distanceToSqr(pos.center) > 25) return@rotationTask true
            val amount = animation.get()
            rotate(startYaw + deltaYaw * amount, startPitch + deltaPitch * amount)
            if (animation.finished) onFinish()
            animation.finished
        }
    }

    private enum class RotationMode {
        Natural,
        Curve
    }

}
