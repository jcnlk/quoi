package quoi.module.impl.misc

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.core.component.DataComponents
import net.minecraft.world.effect.MobEffectUtil
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import quoi.api.events.TickEvent
import quoi.module.Module
import quoi.module.settings.Setting.Companion.json
import quoi.utils.skyblock.ItemUtils.loreString
import quoi.utils.skyblock.ItemUtils.skyblockId
import quoi.utils.ui.settingFromK0
import kotlin.math.pow

// Kyleen
object ItemAnimations : Module(
    "Item Animations",
    desc = "Changes how the held item looks on screen"
) {

    private var x by slider("X", 0.0f, -1.0f, 1.0f, 0.1)
    private var y by slider("Y", 0.0f, -1.0f, 1.0f, 0.1)
    private var z by slider("Z", 0.0f, -1.0f, 1.0f, 0.1)
    private var blockYOffset by slider("Block Y Fix", 0.05f, -1.0f, 1.0f, 0.05)
    private var yaw by slider("Yaw", 0.0f, -180.0f, 180.0f, 1.0)
    private var pitch by slider("Pitch", 0.0f, -180.0f, 180.0f, 1.0)
    private var roll by slider("Roll", 0.0f, -180.0f, 180.0f, 1.0)
    private var scale by slider("Scale", 0.0, -4.0, 4.0, 0.1)
    private var swingSpeed by slider("Swing speed", 0.0, -4.0, 4.0, 0.1)
    private val ignoreHand by switch("Ignore hand")
    private val ignoreMap by switch("Ignore map")
    private val ignoreEffects by switch("Ignore effects")
    private val applyInThirdPerson by switch("Apply in third person").json("Third person")
    private val applyToOtherPlayers by switch("Apply to other players").json("Other players")
    private val noReequipReset by switch("No re-equip reset")
    private val inplaceSwing by switch("Swing in place")
    private val noSwing by switch("No swing animation")
    private val noSwingTerm by switch("No term swing")
    private val noSwingShortbow by switch("No shortbow swing")
    private val noHandSway by switch("No hand sway")
    private val noEatAnimation by switch("No eat animation")
    private val reset by button("Reset") { resetSettings() }

    private var swinging = false
    private var swingTimeTick = 0
    private var attackAnim = 0f
    private var prevAttackAnim = 0f
    private val thirdPersonSwings = mutableMapOf<Int, ThirdPersonSwing>()

    private data class ThirdPersonSwing(val startTime: Double, var previousVanilla: Float)

    override fun onDisable() {
        super.onDisable()
        swinging = false
        thirdPersonSwings.clear()
    }

    private fun resetSettings() {
        setOf(
            ::x, ::y, ::z, ::blockYOffset,
            ::yaw, ::pitch,
            ::roll, ::scale, ::swingSpeed
        ).forEach { settingFromK0(it).reset() }
    }

    private fun calcSwingSpeed() = 2.0.pow(swingSpeed)

    private fun disableSwingRotation(held: ItemStack?): Boolean {
        if (!enabled) return false
        if (noSwing) return true

        if (!noSwingTerm && !noSwingShortbow) return false

        if (held == null || held.isEmpty) return false

        if (noSwingTerm) {
            if (held.skyblockId == "TERMINATOR") return true
        }

        if (noSwingShortbow) {
            if (held.loreString?.contains("Shortbow", ignoreCase = true) == true) return true
        }

        return false
    }

    private fun disableSwingRotation(): Boolean = disableSwingRotation(mc.player?.mainHandItem)

    private fun getCurrentSwingDuration(): Int {
        if (ignoreEffects) return 6
        val player = mc.player ?: return 6
        return if (MobEffectUtil.hasDigSpeed(player)) {
            6 - (1 + MobEffectUtil.getDigSpeedAmplification(player))
        } else {
            6 + (1 + (player.getEffect(MobEffects.MINING_FATIGUE)?.amplifier ?: -1)) * 2
        }
    }

    @JvmStatic fun disableReequip(): Boolean = enabled && noReequipReset
    @JvmStatic fun disableHandSway(): Boolean = enabled && noHandSway
    @JvmStatic fun disableEat(): Boolean = enabled && noEatAnimation

    @JvmStatic fun affectHand(): Boolean = !ignoreHand
    @JvmStatic fun affectMap(): Boolean = !ignoreMap

    @JvmStatic
    fun disableSwingTranslation(): Boolean {
        return enabled && inplaceSwing
    }

    @JvmStatic
    fun disableSwingBob(): Boolean {
        return disableSwingTranslation() || disableSwingRotation()
    }

    @JvmStatic
    fun applyTransformations(pose: PoseStack, stack: ItemStack?) {
        if (!enabled) return

        pose.mulPose(Axis.XP.rotationDegrees(pitch))
        pose.mulPose(Axis.YP.rotationDegrees(yaw))
        pose.mulPose(Axis.ZP.rotationDegrees(roll))

        var renderY = y

        if (stack != null && !stack.isEmpty) {
            val item = stack.item
            if (item is net.minecraft.world.item.PlayerHeadItem) {
                renderY += (blockYOffset * 2.0f)
            } else if (item is BlockItem) {
                renderY += blockYOffset
            }
        }

        if (x != 0.0f || renderY != 0.0f || z != 0.0f) {
            pose.translate(x, renderY, z)
        }
    }

    @JvmStatic
    fun applyScale(pose: PoseStack) {
        if (!enabled) return
        val s = 2.0.pow(scale).toFloat()
        if (s != 1f) pose.scale(s, s, s)
    }

    @JvmStatic
    fun applyThirdPersonPlayerTransformations(pose: PoseStack, stack: ItemStack, playerId: Int) {
        if (!shouldApplyThirdPerson(stack, playerId)) return

        applyTransformations(pose, stack)
        applyScale(pose)
    }

    @JvmStatic
    fun getThirdPersonSwingAnimation(current: Float, stack: ItemStack, playerId: Int): Float {
        if (!shouldApplyThirdPerson(stack, playerId)) return current
        if (disableSwingRotation(stack)) return 0f

        val localPlayerId = mc.player?.id ?: return current
        val pt = mc.deltaTracker.getGameTimeDeltaPartialTick(false)
        if (playerId == localPlayerId) return getSwingAnimation(pt)

        val speed = calcSwingSpeed().takeIf { it != 1.0 } ?: return current.also { thirdPersonSwings.remove(playerId) }
        val currentTime = (mc.level ?: return current).gameTime.toDouble() + pt
        val existingSwing = thirdPersonSwings[playerId]
        val existingProgress = existingSwing?.progress(currentTime, speed)

        val vanillaStartedSwing = current > 0f && (existingSwing == null || existingSwing.previousVanilla <= 0f || current < existingSwing.previousVanilla)
        val swing = if (vanillaStartedSwing && (existingProgress == null || existingProgress >= 0.5f))
            ThirdPersonSwing(currentTime, current).also { thirdPersonSwings[playerId] = it }
        else existingSwing ?: return current

        val progress = swing.progress(currentTime, speed)
        swing.previousVanilla = current

        return if (progress >= 1f) {
            thirdPersonSwings.remove(playerId)
            0f
        } else progress.coerceIn(0f, 1f)
    }

    private fun ThirdPersonSwing.progress(time: Double, speed: Double) =
        ((time - startTime) * speed / getCurrentSwingDuration()).toFloat()

    private fun shouldApplyThirdPerson(stack: ItemStack, playerId: Int): Boolean {
        val localPlayerId = mc.player?.id ?: return false
        return enabled &&
            (if (playerId == localPlayerId) applyInThirdPerson else applyToOtherPlayers) &&
            (!stack.isEmpty || affectHand()) &&
            (!stack.has(DataComponents.MAP_ID) || affectMap())
    }

    @JvmStatic
    fun getSwingAnimation(pt: Float): Float {
        if (disableSwingRotation()) return 0f

        var d = attackAnim - prevAttackAnim
        if (d < 0.0) d++
        return prevAttackAnim + d * pt
    }

    @JvmStatic
    fun onSwing() {
        if (!enabled) return
        if (swinging && swingTimeTick >= 0 && (swingTimeTick * calcSwingSpeed()) < getCurrentSwingDuration() / 2) return
        swingTimeTick = -1
        swinging = true
    }

    init {
        on<TickEvent.End> {
            prevAttackAnim = attackAnim
            val total = getCurrentSwingDuration()

            if (swinging) {
                swingTimeTick++
                val currentProgress = swingTimeTick * calcSwingSpeed()
                if (currentProgress >= total) {
                    swingTimeTick = 0
                    swinging = false
                }
            } else {
                swingTimeTick = 0
            }

            attackAnim = (swingTimeTick * calcSwingSpeed()).toFloat() / total
        }
    }
}
