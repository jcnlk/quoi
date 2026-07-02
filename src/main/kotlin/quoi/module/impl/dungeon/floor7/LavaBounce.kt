package quoi.module.impl.dungeon.floor7

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.events.RenderEvent
import quoi.api.events.TickEvent
import quoi.api.events.core.on
import quoi.api.input.CatKeys
import quoi.api.skyblock.location.Island
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.location.invoke
import quoi.config.configList
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.UIComponent.Companion.visibleIf
import quoi.utils.ChatUtils
import quoi.utils.aabb
import quoi.utils.isWithinFov
import quoi.utils.minFovDot
import quoi.utils.render.drawFilledBox
import quoi.utils.render.drawWireFrameBox
import quoi.utils.skyblock.player.PlayerUtils.eyePosition
import quoi.utils.skyblock.player.SwapManager

object LavaBounce : Module(
    "Lava Bounce",
    area = Island.Dungeon(7, inBoss = true),
    desc = "Automatically places soul sand, chests, or ender chests for lava bounce spots."
) {
    private val auto by switch("Auto", desc = "Automatically places a bounce block while falling into configured lava.")
    private val autoFov by slider("FOV", 360, 10, 360, 1, unit = "°", desc = "Only auto places on lava spots inside this field of view.")
        .childOf(::auto)
    private val triggerbot by switch("Triggerbot", desc = "Places a bounce block when looking at a valid P3 lava bounce spot.")
    private val cooldown by slider("Cooldown", 500L, 0L, 2_000L, 50L, unit = "ms", desc = "Cooldown per bounce block.")

    private val useConfig by switch("Use config", desc = "Only uses lava positions configured with Toggle lava.")
    private val toggleLava by keybind("Toggle lava", CatKeys.KEY_NONE, desc = "Adds or removes the lava block you are looking at.")
        .onPress {
            if (!enabled || !Dungeon.inBoss || Dungeon.floor?.floorNumber != 7) return@onPress
            toggleLookedLava()
        }

    private val renderBlocks by switch("Render blocks", true, desc = "Highlights configured lava bounce spots.")
        .childOf(::useConfig)
    private val renderStyle by selector("Style", "Filled box", arrayListOf("Box", "Filled box", "Filled"), desc = "Render style for configured lava bounce spots.")
        .childOf(::renderBlocks)
        .visibleIf { useConfig && renderBlocks }
    private val outlineColour by colourPicker("Outline colour", Colour.RED.withAlpha(180), allowAlpha = true, desc = "Outline colour for configured lava bounce spots.")
        .childOf(::renderBlocks)
        .visibleIf { useConfig && renderBlocks && renderStyle.selected != "Filled" }
    private val fillColour by colourPicker("Fill colour", Colour.RED.withAlpha(90), allowAlpha = true, desc = "Fill colour for configured lava bounce spots.")
        .childOf(::renderBlocks)
        .visibleIf { useConfig && renderBlocks && renderStyle.selected != "Box" }
    private val thickness by slider("Thickness", 2f, 1f, 8f, 1f, desc = "Outline thickness for configured lava bounce spots.")
        .childOf(::renderBlocks)
        .visibleIf { useConfig && renderBlocks && renderStyle.selected != "Filled" }
    private val depth by switch("Depth check", true, desc = "Renders lava bounce highlights with depth check.")
        .childOf(::renderBlocks)
        .visibleIf { useConfig && renderBlocks }

    private val lavaBlocks by configList<BlockPos>("dungeon/lavabounce_blocks.json")
    private val cooldowns = hashMapOf<BlockPos, Long>()
    private val bounceableBlock = setOf(Items.SOUL_SAND, Items.CHEST, Items.ENDER_CHEST)

    init {
        on<RenderEvent.World> {
            if (!useConfig || !renderBlocks) return@on

            for (pos in lavaBlocks) {
                when (renderStyle.selected) {
                    "Box" -> ctx.drawWireFrameBox(pos.aabb, outlineColour, thickness, depth)
                    "Filled box" -> {
                        ctx.drawFilledBox(pos.aabb, fillColour, depth)
                        ctx.drawWireFrameBox(pos.aabb, outlineColour, thickness, depth)
                    }
                    "Filled" -> ctx.drawFilledBox(pos.aabb, fillColour, depth)
                }
            }
        }

        on<TickEvent.Start> {
            if (mc.gui.screen() != null || !Dungeon.inBoss || Dungeon.floor?.floorNumber != 7) return@on

            if (auto) tickAuto()
            if (triggerbot) tickTriggerbot()
        }
    }

    private fun tickAuto() {
        if (player.isInLava || player.onGround()) return

        val under = findBlockUnderLava() ?: return
        val lava = under.above()
        if (useConfig && lava !in lavaBlocks) return
        if (!lava.isInAutoFov()) return
        if (!level.getBlockState(under).getShape(level, under).isEmpty) {
            val eyePos = player.eyePosition()
            val top = Vec3(under.x + 0.5, under.y + 0.999, under.z + 0.5)
            val motionY = player.deltaMovement.y
            val nextMotionY = ((motionY - 0.08) * 0.98).coerceAtLeast(-3.9) * 2.0
            val nextY = player.y + nextMotionY

            if (nextY > top.y || eyePos.distanceToSqr(top) > 20.25) return
            placeBounceBlock(under, BlockHitResult(top, Direction.UP, under, false))
        }
    }

    private fun tickTriggerbot() {
        val hitResult = mc.hitResult as? BlockHitResult ?: return
        if (hitResult.type != HitResult.Type.BLOCK) return

        val item = player.mainHandItem.item
        if (item !in bounceableBlock) return

        val pos = hitResult.blockPos
        if (pos.y != 105) return
        if (level.getBlockState(pos).block != Blocks.STONE_BRICKS) return
        if (level.getBlockState(pos.above()).block != Blocks.LAVA) return
        if (useConfig && pos.above() !in lavaBlocks) return

        placeBounceBlock(pos, hitResult, swap = false)
    }

    private fun placeBounceBlock(pos: BlockPos, hitResult: BlockHitResult, swap: Boolean = true): Boolean {
        val now = System.currentTimeMillis()
        val immutablePos = pos.immutable()
        if (now - cooldowns.getOrDefault(immutablePos, 0L) < cooldown) return false

        if (swap && !SwapManager.swapById("SOUL_SAND", "CHEST", "ENDER_CHEST").success) return false

        val result = mc.gameMode?.useItemOn(player, InteractionHand.MAIN_HAND, hitResult) ?: return false
        if (!result.consumesAction()) return false

        player.swing(InteractionHand.MAIN_HAND)
        cooldowns[immutablePos] = now
        return true
    }

    private fun findBlockUnderLava(): BlockPos? {
        val pos = player.blockPosition().mutable()

        for (y in player.blockY downTo 1) {
            pos.y = y
            val state = level.getBlockState(pos)
            when {
                state.`is`(Blocks.LAVA) -> return pos.setY(y - 1).immutable()
                !state.`is`(Blocks.AIR) -> return null
            }
        }

        return null
    }

    private fun toggleLookedLava() {
        val result = player.pick(4.5, 1f, true)
        if (result !is BlockHitResult || result.type == HitResult.Type.MISS) {
            ChatUtils.modMessage("&cLook at a lava block to toggle it.")
            return
        }

        val pos = result.blockPos
        if (!level.getBlockState(pos).`is`(Blocks.LAVA)) {
            ChatUtils.modMessage("&cThat block is not lava.")
            return
        }

        if (lavaBlocks.remove(pos)) {
            ChatUtils.modMessage("&cRemoved lava bounce at &f${pos.x}, ${pos.y}, ${pos.z}&c.")
        } else {
            lavaBlocks.add(pos.immutable())
            ChatUtils.modMessage("&aAdded lava bounce at &f${pos.x}, ${pos.y}, ${pos.z}&a.")
        }
    }

    private fun BlockPos.isInAutoFov(): Boolean {
        if (autoFov >= 360) return true

        val eyePos = player.eyePosition()
        val lookVec = player.getViewVector(mc.deltaTracker.getGameTimeDeltaPartialTick(false)).normalize()
        return isWithinFov(eyePos, Vec3.atCenterOf(this), lookVec, minFovDot(autoFov), fullCircleFov = false)
    }
}
