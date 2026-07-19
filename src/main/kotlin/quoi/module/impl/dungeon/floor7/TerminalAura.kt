package quoi.module.impl.dungeon.floor7

import net.minecraft.network.protocol.game.ServerboundInteractPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.decoration.ArmorStand
import quoi.api.events.TickEvent
import quoi.api.events.core.on
import quoi.api.skyblock.location.Island
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.Floor7Utils
import quoi.api.skyblock.dungeon.Phase
import quoi.api.skyblock.location.invoke
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.utils.EntityUtils
import quoi.utils.isWithinFov
import quoi.utils.minFovDot
import quoi.utils.skyblock.player.LeapManager

// Kyleen
@Suppress("UNNECESSARY_SAFE_CALL")
object TerminalAura : Module(
    "Terminal Aura",
    desc = "Automatically opens terminals.",
    area = Island.Dungeon(7, inBoss = true)
) {
    private val auraDistance by slider("Distance", 4.0, 0.0, 4.0, 0.1)
    private val auraDelay by slider("Delay", 750, 0, 2000, 50)
    private val auraFov by slider("Aura FOV", 360, 10, 360, 1, unit = "°")
    private val groundOnly by switch("Ground only")
    private val leapDelayEnabled by switch("Leap delay", desc = "Delays opening terminals for x seconds after leap")
    private val leapDelay by slider("Leap delay time", 0.5, 0.1, 5.0, 0.1, unit = "s").childOf(::leapDelayEnabled)

    private var lastClick = 0L

    init {
        on<TickEvent.Start> {
            if (!Floor7Utils.inPhase(Phase.P3) || Dungeon.inTerminal || Dungeon.isDead || mc.screen != null) return@on
            if (System.currentTimeMillis() - lastClick < auraDelay) return@on

            if (leapDelayEnabled) {
                val delayMs = (leapDelay * 1000.0).toLong()
                if (System.currentTimeMillis() - LeapManager.lastLeap < delayMs) return@on
            }

            if (groundOnly && !player.onGround()) return@on

            val eyePos = player.eyePosition
            val lookVec = player.getViewVector(mc.deltaTracker.getGameTimeDeltaPartialTick(false)).normalize()
            val minFovDot = minFovDot(auraFov)
            val fullCircleFov = auraFov >= 360
            val entities = EntityUtils.getEntities<ArmorStand>(player.boundingBox.inflate(auraDistance))

            for (entity in entities) {
                val name = entity.displayName?.string ?: continue

                if (!name.contains("Inactive Terminal")) continue
                if (entity.isRemoved || !entity.isAlive) continue

                val entityCenter = entity.position().add(0.0, entity.bbHeight / 2.0, 0.0)

                if (eyePos.distanceToSqr(entityCenter) > auraDistance * auraDistance) continue
                if (!isWithinFov(eyePos, entityCenter, lookVec, minFovDot, fullCircleFov)) continue

                val aabb = entity.boundingBox.inflate(0.1)
                val hitResult = aabb.clip(eyePos, entityCenter)

                if (hitResult.isEmpty) continue

                val hitVec = hitResult.get()

                val packet = ServerboundInteractPacket(entity.id, InteractionHand.MAIN_HAND, hitVec.subtract(entity.position()), player.isShiftKeyDown)

                connection.send(packet)
                player.swing(InteractionHand.MAIN_HAND)

                lastClick = System.currentTimeMillis()
                break
            }
        }
    }
}
