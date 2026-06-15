package quoi.module.impl.dungeon

import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.events.*
import quoi.api.input.CatKeys
import quoi.api.skyblock.Island
import quoi.api.skyblock.dungeon.Dungeon.floor
import quoi.api.skyblock.dungeon.Dungeon.inBoss
import quoi.api.skyblock.dungeon.Dungeon.inDungeons
import quoi.api.skyblock.dungeon.Dungeon.isProtectedBlock
import quoi.config.configList
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.UIComponent.Companion.visibleIf
import quoi.utils.ChatUtils.modMessage
import quoi.utils.Scheduler.scheduleLoop
import quoi.utils.WorldUtils.state
import quoi.utils.aabb
import quoi.utils.isWithinFov
import quoi.utils.minFovDot
import quoi.utils.skyblock.player.PlayerUtils.eyePosition
import quoi.utils.render.drawFilledBox
import quoi.utils.render.drawWireFrameBox
import quoi.utils.skyblock.item.ItemUtils.getBreakerCharges
import quoi.utils.skyblock.player.interact.AuraManager
import quoi.utils.skyblock.player.PlayerUtils
import quoi.utils.skyblock.player.SwapManager
import quoi.utils.ui.textPair

/*
 * TODO:
 *  add support for multiple configs
 */

// Kyleen
object DungeonBreaker : Module(
    "Dungeon Breaker",
    area = Island.Dungeon
) {
    private val chargesHud by textHud("Charges display") {
        visibleIf { mc.player != null && inDungeons && getBreakerCharges(player.mainHandItem) > 0 }
        textPair(
            string = "Charges:",
            supplier = { mc.player?.let { getBreakerCharges(it.mainHandItem) } ?: 0 },
            labelColour = colour,
            shadow = shadow,
            font = font
        )
    }.setting()

    private val zeroPingDungeonBreaker by switch("Zero ping", desc = "Insta-mine blocks.")
    private val onlyWhenFatigue by switch("Fatigue only", desc = "Only insta-mine blocks when mining fatigue is applied.").childOf(::zeroPingDungeonBreaker)
    private val disableInInventory by switch("Disable in inventory", desc = "Prevents dungeon breaker from working while inside of an inventory.")

    private val triggerBot by switch("Triggerbot", desc = "Mines preset blocks when looking at them.")
    private val triggerBotDelay by slider("Delay", 0, 0, 10, 1, desc = "Delay before mining the looked-at block.", unit = " ticks").childOf(::triggerBot)
    private val autoDb by switch("Auto dungeon breaker", desc = "Automatically mines preset route when in boss.")
    private val autoDbRange by slider("Range", 5.5, 1.0, 5.5, 0.1, desc = "Maximum distance to auto dungeon breaker blocks.", unit = " blocks").childOf(::autoDb)
    private val autoDbFov by slider("FOV", 360, 10, 360, 1, desc = "Only mines route blocks inside this field of view.", unit = "°").childOf(::autoDb)
    private val autoDbDelay by slider("Delay", 0, 0, 20, 1, desc = "Delay between auto dungeon breaker mining attempts.", unit = " ticks").childOf(::autoDb)
    private val zeroTickDb by switch("Zero tick").childOf(::autoDb)

    private val highlightBlocks by switch("Highlight blocks", true, desc = "Highlights blocks set for dungeon breaker.")
    private val blockStyle by selector("Block style", "Filled box", arrayListOf("Box", "Filled box", "Filled"), desc = "Render style for set dungeon breaker blocks.").childOf(::highlightBlocks)
    private val blockColour by colourPicker("Ready outline", Colour.GREEN.withAlpha(180), allowAlpha = true, desc = "Outline colour for blocks that can be mined.").childOf(::highlightBlocks).visibleIf { blockStyle.selected != "Filled" }
    private val blockFillColour by colourPicker("Ready fill", Colour.GREEN.withAlpha(45), allowAlpha = true, desc = "Fill colour for blocks that can be mined.").childOf(::highlightBlocks).visibleIf { blockStyle.selected != "Box" }
    private val missingBlockColour by colourPicker("Broken outline", Colour.RED.withAlpha(180), allowAlpha = true, desc = "Outline colour for set blocks that are currently air.").childOf(::highlightBlocks).visibleIf { blockStyle.selected != "Filled" }
    private val missingBlockFillColour by colourPicker("Broken fill", Colour.RED.withAlpha(35), allowAlpha = true, desc = "Fill colour for set blocks that are currently air.").childOf(::highlightBlocks).visibleIf { blockStyle.selected != "Box" }
    private val blockThickness by slider("Outline thickness", 3f, 1f, 8f, 1f, desc = "Outline thickness for highlighted blocks.").childOf(::highlightBlocks).visibleIf { blockStyle.selected != "Filled" }
    private val blockDepth by switch("Block depth check", true, desc = "Renders block highlights with depth check.").childOf(::highlightBlocks)
    private val dbBlocks by configList<BlockPos>("dungeonbreaker_blocks.json")

    private val toggleBlock by keybind("Toggle block", CatKeys.KEY_NONE, desc = "Adds or removes the block you are currently looking at.")
        .onPress {
            if (!enabled || !inBoss || floor?.floorNumber != 7) return@onPress
            if (disableInGUI()) return@onPress
            toggleLookedBlock()
        }

    private val clearBlocks by button("Clear blocks", desc = "Clears all dungeon breaker blocks.") {
        clearDbBlocks()
    }

    private var triggerTarget: BlockPos? = null
    private var triggerTicks = 0
    private var autoDbTicks = 0
    private val recentlyBroken = mutableMapOf<BlockPos, Long>()

    private const val DEFAULT_RANGE = 5.5

    init {
        on<PacketEvent.Sent, ServerboundPlayerActionPacket> {
            if (!zeroPingDungeonBreaker) return@on
            if (disableInGUI()) return@on
            if (onlyWhenFatigue && !player.hasEffect(MobEffects.MINING_FATIGUE)) return@on
            if (packet.action != ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK) return@on

            val packetPos = packet.pos

            mc.execute {
                val heldItem = player.mainHandItem
                if (getBreakerCharges(heldItem) <= 0) return@execute

                if (isProtectedBlock(packetPos)) return@execute

                val clipResult = level.clip(
                    ClipContext(
                        player.eyePosition,
                        Vec3.atCenterOf(packetPos),
                        ClipContext.Block.OUTLINE,
                        ClipContext.Fluid.NONE,
                        player
                    )
                )

                if (clipResult.type == HitResult.Type.BLOCK && clipResult.blockPos == packetPos) {
                    level.setBlock(packetPos, Blocks.AIR.defaultBlockState(), 3)
                }
            }
        }

        on<RenderEvent.World> {
            if (!highlightBlocks) return@on
            for (pos in dbBlocks) {
                val lineColour = if (pos.state.isAir) missingBlockColour else blockColour
                val fillColour = if (pos.state.isAir) missingBlockFillColour else blockFillColour
                when (blockStyle.selected) {
                    "Box" -> ctx.drawWireFrameBox(pos.aabb, lineColour, blockThickness, blockDepth)
                    "Filled box" -> {
                        ctx.drawFilledBox(pos.aabb, fillColour, blockDepth)
                        ctx.drawWireFrameBox(pos.aabb, lineColour, blockThickness, blockDepth)
                    }
                    "Filled" -> ctx.drawFilledBox(pos.aabb, fillColour, blockDepth)
                }
            }
        }

        on<TickEvent.Start> {
            if ((!autoDb && !triggerBot) || !inBoss || floor?.floorNumber != 7) return@on
            if (disableInGUI()) return@on
            if (dbBlocks.isEmpty()) return@on

            if (autoDb) tickAutoDb()
            if (triggerBot) tickTriggerBot()
        }

        scheduleLoop(10) {
            if (enabled && (autoDb || triggerBot)) clearCooldownCache()
        }
    }

    private fun clearDbBlocks() {
        dbBlocks.clear()
        modMessage("&aCleared all dungeon breaker blocks.")
    }

    private fun toggleLookedBlock() {
        val pos = lookedBlock() ?: return modMessage("&cLook at a block to toggle it.")
        if (isProtectedBlock(pos)) return modMessage("&cThat block is protected.")

        if (dbBlocks.remove(pos)) {
            modMessage("&cRemoved dungeon breaker block at &f${pos.x}, ${pos.y}, ${pos.z}&c.")
        } else {
            dbBlocks.add(pos)
            modMessage("&aAdded dungeon breaker block at &f${pos.x}, ${pos.y}, ${pos.z}&a.")
        }
    }

    private fun lookedBlock(): BlockPos? {
        val result = mc.hitResult
        if (result !is BlockHitResult || result.type != HitResult.Type.BLOCK) return null
        return result.blockPos
    }

    private fun disableInGUI() = disableInInventory && mc.gui.screen() != null

    private fun tickTriggerBot() {
        val pos = lookedBlock()?.takeIf { it in dbBlocks && isMineableDbBlock(it) }

        if (pos == null) {
            triggerTarget = null
            triggerTicks = 0
            return
        }

        if (triggerTarget != pos) {
            triggerTarget = pos
            triggerTicks = 0
        }

        if (triggerTicks++ < triggerBotDelay) return
        if (mineDbBlocks(listOf(pos), allowZeroTick = false)) {
            triggerTarget = null
            triggerTicks = 0
        }
    }

    private fun tickAutoDb() {
        if (autoDbTicks++ < autoDbDelay) return
        if (mineDbBlocks(dbBlocks, allowZeroTick = zeroTickDb, range = autoDbRange, fov = autoDbFov)) {
            autoDbTicks = 0
        }
    }

    private fun mineDbBlocks(targetBlocks: Collection<BlockPos>, allowZeroTick: Boolean, range: Double = DEFAULT_RANGE): Boolean {
        val blocks = targetBlocks.filter { isMineableDbBlock(it, range) }
        if (blocks.isEmpty()) return false

        val breakerSlot = PlayerUtils.breakerSlot ?: return false

        if (player.inventory.selectedSlot != breakerSlot) {
            if (!SwapManager.swapToSlot(breakerSlot).success) return false
            return false
        }

        val initialCharges = getBreakerCharges(player.inventory.getItem(breakerSlot))
        if (initialCharges == 0) return false

        blocks.forEachIndexed { i, pos ->
            if (i >= initialCharges) return true
            AuraManager.breakBlock(pos, immediate = true)
            recentlyBroken[pos] = System.currentTimeMillis()
            if (!allowZeroTick) return true
        }

        return true
    }

    private fun mineDbBlocks(targetBlocks: Collection<BlockPos>, allowZeroTick: Boolean, range: Double, fov: Int): Boolean {
        val eyePos = player.eyePosition()
        val lookVec = player.getViewVector(mc.deltaTracker.getGameTimeDeltaPartialTick(false)).normalize()
        val minFovDot = minFovDot(fov)
        val fullCircleFov = fov >= 360

        return mineDbBlocks(
            targetBlocks.filter { pos ->
                isWithinFov(eyePos, Vec3.atCenterOf(pos), lookVec, minFovDot, fullCircleFov)
            },
            allowZeroTick,
            range
        )
    }

    private fun isMineableDbBlock(pos: BlockPos, range: Double = DEFAULT_RANGE) =
        !recentlyBroken.containsKey(pos) &&
            level.isLoaded(pos) &&
            !pos.state.isAir &&
            pos.distToCenterSqr(player.eyePosition()) <= range * range

    private fun clearCooldownCache() {
        val now = System.currentTimeMillis()
        recentlyBroken.entries.removeIf { (pos, time) -> now - time > 10_500 || !pos.state.isAir }
    }
}
