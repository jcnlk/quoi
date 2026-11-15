package quoi.module.impl.floor7

import net.minecraft.network.protocol.game.ServerboundInteractPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.phys.EntityHitResult
import quoi.api.events.SkyblockPlayerEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.Event
import quoi.api.events.core.EventDispatcher
import quoi.api.events.core.on
import quoi.api.skyblock.dungeon.Dungeon.isDead
import quoi.api.skyblock.dungeon.enums.Phase
import quoi.api.skyblock.dungeon.terminals.TerminalUtils.inTerminal
import quoi.api.skyblock.location.Island
import quoi.api.skyblock.location.invoke
import quoi.module.Module
import quoi.module.settings.Setting.Companion.json
import quoi.module.settings.UIComponent.Companion.visibleIf
import quoi.utils.DualTime
import quoi.utils.EntityUtils
import quoi.utils.isWithinFov
import quoi.utils.minFovDot

@Suppress("UNNECESSARY_SAFE_CALL")
object Terminals : Module(
    "Terminals",
    desc = "Opens nearby terminals with an aura or triggerbot.",
    area = Island.Dungeon(7, inBoss = true, phase = Phase.P3)
) {
    private val mode by selector("Mode", "Aura", listOf("Aura", "Triggerbot"), desc = "Aura opens nearby terminals automatically. Triggerbot only opens the terminal you are looking at.")
    private val distance by slider("Distance", 4.0, 0.0, 4.0, 0.1)
    private val delay by slider("Delay", 750, 0, 2000, 50, unit = "ms")
    private val auraFov by slider("Aura FOV", 360, 10, 360, 1, unit = "°").visibleIf { mode.selected == "Aura" }
    private val groundOnly by switch("Ground only")
    private val leapDelay by slider("Leap delay", 10, 0, 100, 1, unit = "t", desc = "Delays opening terminals for x ticks after leap").json("Leap delay ticks")

    private var lastClick = 0L
    private var lastLeap: DualTime? = null

    init {
        on<TickEvent.Start> {
            if (inTerminal || isDead || mc.gui.screen() != null) return@on
            if (System.currentTimeMillis() - lastClick < delay) return@on

            lastLeap?.let {
                val elapsed = DualTime(EventDispatcher.totalTicks.toLong()) - it
                if (elapsed.ticks < leapDelay) return@on
            }

            if (groundOnly && !player.onGround()) return@on

            val eyePos = player.eyePosition
            val lookVec = player.getViewVector(mc.deltaTracker.getGameTimeDeltaPartialTick(false)).normalize()
            val minFovDot = minFovDot(auraFov)
            val fullCircleFov = auraFov >= 360
            val triggerHit = if (mode.selected == "Triggerbot") {
                mc.hitResult as? EntityHitResult ?: return@on
            } else null
            val entities = if (triggerHit != null) listOfNotNull(triggerHit.entity as? ArmorStand)
            else EntityUtils.getEntities<ArmorStand>(player.boundingBox.inflate(distance))

            for (entity in entities) {
                val name = entity.displayName?.string ?: continue

                if (!name.contains("Inactive Terminal")) continue
                if (entity.isRemoved || !entity.isAlive) continue
                if (eyePos.y < entity.y) continue // hypixel declines term opening when ur y-lvl is below term y-lvl

                val entityCenter = entity.position().add(0.0, entity.bbHeight / 2.0, 0.0)

                if (eyePos.distanceToSqr(entityCenter) > distance * distance) continue
                if (triggerHit == null && !isWithinFov(eyePos, entityCenter, lookVec, minFovDot, fullCircleFov)) continue

                val hitVec = if (triggerHit != null) triggerHit.location else {
                    val hitResult = entity.boundingBox.inflate(0.1).clip(eyePos, entityCenter)

                    if (hitResult.isEmpty) continue

                    hitResult.get()
                }

                val packet = ServerboundInteractPacket(entity.id, InteractionHand.MAIN_HAND, hitVec.subtract(entity.position()), player.isShiftKeyDown)

                connection.send(packet) // TODO: replace with aura manager
                player.swing(InteractionHand.MAIN_HAND)

                lastClick = System.currentTimeMillis()
                break
            }
        }

        on<SkyblockPlayerEvent.Leap> {
            lastLeap = DualTime(EventDispatcher.totalTicks.toLong())
        }

        on<WorldEvent.Change> {
            lastLeap = null
        }
    }

    override fun shouldHandle(event: Event): Boolean = when (event) {
        is SkyblockPlayerEvent.Leap, is WorldEvent.Change -> true
        else -> super.shouldHandle(event)
    }

    override fun onDisable() {
        lastLeap = null
    }
}
