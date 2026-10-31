package quoi.module.impl.floor7

import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.*
import quoi.api.colour.Colour
import quoi.api.events.*
import quoi.api.events.core.Priority
import quoi.api.events.core.on
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.Floor7
import quoi.api.skyblock.dungeon.enums.Floor
import quoi.api.skyblock.dungeon.enums.Phase
import quoi.api.skyblock.location.Island
import quoi.api.skyblock.location.invoke
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.utils.EntityUtils.getEntities
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.getDirection
import quoi.utils.getHitResult
import quoi.utils.render.drawLine
import quoi.utils.skyblock.player.PlayerUtils.rightClick
import quoi.utils.skyblock.player.RotationUtils
import quoi.utils.skyblock.player.RotationUtils.rotate
import quoi.utils.skyblock.player.RotationUtils.rotateSmoothly
import quoi.utils.skyblock.player.SwapManager
import quoi.utils.skyblock.player.interact.AuraAction
import quoi.utils.skyblock.player.interact.AuraManager
import kotlin.math.cos
import kotlin.math.sin


//based on: https://github.com/Noamm9/NoammAddons/blob/b2880a52ab5342e48b17f5e0a89c62d13199eaf5/src/main/kotlin/com/github/noamm9/features/impl/floor7/M7Relics.kt
object Relics : Module(
    "Relics",
    desc = "Relic pickup, placement and movement helpers for M7.",
    area = Island.Dungeon(7, inBoss = true)
) {
    private val relicLook by switch("Relic look", desc = "Turns toward the cauldron when you pick up a red or orange relic.")
    private val relicLookTime by slider("Relic look time", 150, 10, 300, 1, unit = "ms").childOf(::relicLook)
    private val relicRun by switch("Relic run", desc = "Runs red and orange relics from the marked start spots toward their cauldrons.").childOf(::relicLook).asParent()
    private val relicTriggerbot by switch("Relic triggerbot", desc = "Picks up nearby relics and places your carried relic when aiming at its cauldron or the anvil below it. Automatically selects hotbar slot 9 for placement.")
    private val relicAura by switch("Relic aura", desc = "Automatically picks up and places relics within the configured range. Selects hotbar slot 9 for placement.")
    private val auraRange by slider("Aura range", 4.5, 1.0, 4.5, 0.1, unit = " blocks", desc = "Maximum distance for relic pickup and placement.").childOf(::relicAura)

    private var lastRelicClick = 0L
    private var lastHeldRelic: Relic? = null
    private var runningRelic: Relic? = null
    private var runTicks = 0

    private val inP5: Boolean get() = Dungeon.floor == Floor.M7 && Floor7.inPhase(Phase.P5)
    private val carriedRelic: Relic? get() = Relic.fromItem(player.inventory.getItem(8))
    private val redRunSpot = AABB(22.3, 6.0, 58.0, 23.0, 6.02, 58.7)
    private val orangeRunSpot = AABB(90.0, 6.0, 55.0, 90.7, 6.02, 55.7)

    init {
        on<WorldEvent.Change> { reset() }

        on<RenderEvent.World> {
            if (!inP5 || !relicLook || !relicRun) return@on
            listOf(redRunSpot, orangeRunSpot).forEach { spot ->
                val colour = if (atRunSpot(spot)) Colour.GREEN else Colour.RED
                val center = spot.center
                val points = (0..32).map { segment ->
                    val angle = segment * Math.PI * 2 / 32
                    Vec3(center.x + cos(angle) * 0.35, center.y, center.z + sin(angle) * 0.35)
                }
                ctx.drawLine(points, colour, depth = true)
            }
        }

        on<TickEvent.Start> {
            if (!inP5 || Dungeon.isDead || mc.screen != null) {
                runningRelic = null
                return@on
            }
            updateRelicLook()
            runningRelic?.let {
                if (!relicLook || !relicRun || carriedRelic != it || reachedCauldron(it) || ++runTicks > 200) runningRelic = null
                else SwapManager.swapToSlot(8)
            }
            if (System.currentTimeMillis() - lastRelicClick < 200) return@on
            carriedRelic?.let {
                placeRelic(it)
                return@on
            }

            if (relicAura) {
                val stands = getEntities<ArmorStand>().filter { isRelicStand(it) }.toList()
                stands.firstOrNull { canPickUp(it, auraRange) }?.let {
                    AuraManager.interactEntity(it, AuraAction.INTERACT_AT)
                    player.swing(InteractionHand.MAIN_HAND)
                    lastRelicClick = System.currentTimeMillis()
                    return@on
                }
            }

            if (!relicTriggerbot) return@on
            val target = (mc.hitResult as? EntityHitResult)?.entity as? ArmorStand ?: return@on
            if (!isRelicStand(target) || !canPickUp(target)) return@on
            player.rightClick()
            lastRelicClick = System.currentTimeMillis()
        }

        on<KeyEvent.Input>(Priority.LOWEST - 1) {
            val relic = runningRelic ?: return@on
            if (!inP5 || !relicLook || !relicRun || Dungeon.isDead || mc.screen != null || carriedRelic != relic || reachedCauldron(relic)) {
                runningRelic = null
                return@on
            }
            input.stop()
            input.forward = player.inventory.selectedSlot == 8
        }

        on<MouseEvent.Move> { runningRelic = null }
    }

    override fun onDisable() = reset()

    private fun reset() {
        lastRelicClick = 0L
        lastHeldRelic = null
        runningRelic = null
        runTicks = 0
    }

    private fun updateRelicLook() {
        val relic = carriedRelic
        if (relic == lastHeldRelic) return
        lastHeldRelic = relic
        if (!relicLook || Dungeon.isDead || (relic != Relic.RED && relic != Relic.ORANGE)) return
        val runYaw = runYaw(relic)
        if (relicRun && runYaw != null) {
            runningRelic = relic
            runTicks = 0
            RotationUtils.cancelRotationTask()
            player.rotate(runYaw, 0f)
            return
        }
        player.rotateSmoothly(getDirection(Vec3.atCenterOf(relic.cauldronPos)), relicLookTime * 1_000_000f)
    }

    private fun placeRelic(relic: Relic) {
        val hit = if (relicAura) placementHit(relic) else null
        val target = hit ?: (mc.hitResult as? BlockHitResult)?.takeIf {
            relicTriggerbot && it.type == HitResult.Type.BLOCK && relic.isPlacementTarget(it.blockPos) &&
                    player.eyePosition.distanceToSqr(it.location) <= 4.5 * 4.5
        } ?: return
        if (!SwapManager.swapToSlot(8).success) return
        gameMode.useItemOn(player, InteractionHand.MAIN_HAND, target)
        player.swing(InteractionHand.MAIN_HAND)
        lastRelicClick = System.currentTimeMillis()
    }

    private fun placementHit(relic: Relic): BlockHitResult? = relic.cauldronPos.getHitResult()?.takeIf {
        player.eyePosition.distanceToSqr(it.location) <= auraRange * auraRange
    }

    private fun atRunSpot(spot: AABB): Boolean =
        player.x in spot.minX..spot.maxX && player.y == 6.0 && player.z in spot.minZ..spot.maxZ

    private fun runYaw(relic: Relic): Float? = when (relic) {
        Relic.RED if atRunSpot(redRunSpot) -> -120f
        Relic.ORANGE if atRunSpot(orangeRunSpot) -> 111f
        else -> null
    }

    private fun reachedCauldron(relic: Relic): Boolean {
        val target = Vec3.atCenterOf(relic.cauldronPos)
        val dx = player.x - target.x
        val dz = player.z - target.z
        return dx * dx + dz * dz <= 3.0 * 3.0
    }

    private fun isRelicStand(stand: ArmorStand): Boolean =
        !stand.isRemoved && stand.isAlive &&
                stand.getItemBySlot(EquipmentSlot.HEAD).hoverName.string.noControlCodes.contains("Relic")

    private fun canPickUp(stand: ArmorStand, range: Double = 3.0): Boolean =
        stand.position().distanceToSqr(player.position()) <= range * range && Relic.entries.none { it.isAtCauldron(stand.position()) }

    private enum class Relic(
        val formalName: String,
        val cauldronPos: BlockPos,
        private val placedX: Int,
        private val placedZ: Int
    ) {
        RED("Corrupted Red Relic", BlockPos(51, 7, 42), 52, 43),
        ORANGE("Corrupted Orange Relic", BlockPos(57, 7, 42), 58, 43),
        GREEN("Corrupted Green Relic", BlockPos(49, 7, 44), 50, 45),
        BLUE("Corrupted Blue Relic", BlockPos(59, 7, 44), 60, 45),
        PURPLE("Corrupted Purple Relic", BlockPos(54, 7, 41), 55, 42);

        fun isPlacementTarget(pos: BlockPos): Boolean = pos == cauldronPos || pos == cauldronPos.below()

        fun isAtCauldron(pos: Vec3): Boolean {
            val dx = pos.x - placedX
            val dz = pos.z - placedZ
            return dx * dx + dz * dz < 1.5 * 1.5
        }

        companion object {
            fun fromName(name: String): Relic? = entries.find { it.formalName.equals(name.noControlCodes, ignoreCase = true) }
            fun fromItem(item: ItemStack): Relic? = fromName(item.hoverName.string)
        }
    }
}
