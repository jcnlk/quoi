package quoi.module.impl.mining.glacitetunnels

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import quoi.api.colour.Colour
import quoi.api.events.ChatEvent
import quoi.api.events.GuiEvent
import quoi.api.events.MouseEvent
import quoi.api.events.PacketEvent
import quoi.api.events.RenderEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.on
import quoi.api.skyblock.location.Island
import quoi.api.skyblock.location.Location
import quoi.module.Module
import quoi.module.impl.mining.CommissionDisplay
import quoi.utils.ChatUtils.command
import quoi.utils.EntityUtils.renderPos
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.render.drawLine
import quoi.utils.render.drawText
import quoi.utils.skyblock.item.ItemUtils.skyblockId

object GlaciteTunnels : Module(
    "Glacite Tunnels",
    area = Island.DwarvenMines,
    desc = "Navigation and utilities for the Glacite Tunnels.",
) {
    private val commissionRoutes by switch("Commission routes", desc = "Shows the best route to the next collector commission location.")
    @Suppress("unused")
    private val baseWarpKey by keybind("Base warp", desc = "Warps to the Dwarven Base Camp with /warp basecamp.")
        .onPress {
            if (active && inGlaciteEnvironment() && mc.gui.screen() == null) command("warp basecamp")
        }
    private val pathColour by colourPicker("Path colour", Colour.RGB(76, 235, 160), allowAlpha = true)
    private val pathWidth by slider("Path width", 4f, 1f, 12f, 1f)
    private val targetTextSize by slider("Target text size", 1f, 0.5f, 2.5f, 0.1f)
    private val depthCheck by switch("Depth check", true)

    private val graph by lazy(LazyThreadSafetyMode.NONE) { TunnelGraph.load() }
    private val navigator = GlaciteTunnelNavigator(graph = { graph }, directlyReachable = ::hasWalkingLine)
    private var ticks = 0
    private var hasPigeonData = false
    private var awaitingClaim = false

    init {
        on<TickEvent.End> {
            if (!inGlaciteEnvironment()) {
                reset()
                return@on
            }
            ticks++
            if (!commissionRoutes) {
                navigator.hideRoute()
                return@on
            }
            updateRoute()
        }

        on<RenderEvent.World> {
            if (!inGlaciteEnvironment()) return@on
            val destination = navigator.target ?: return@on
            if (navigator.route.isEmpty()) return@on
            val points = buildList {
                add(player.renderPos.add(0.0, 0.15, 0.0))
                navigator.route.forEach { add(it.position.add(0.0, 0.15, 0.0)) }
            }
            ctx.drawLine(points, pathColour, depth = depthCheck, thickness = pathWidth)

            val viewer = player.renderPos
            val targetTextPosition = destination.position.add(0.0, 1.8, 0.0)
            val targetDistance = viewer.distanceTo(targetTextPosition).coerceAtLeast(MIN_TEXT_DISTANCE)
            val renderDistance = targetDistance.coerceAtMost(MAX_TEXT_DISTANCE)
            val renderPosition = viewer.add(targetTextPosition.subtract(viewer).scale(renderDistance / targetDistance))
            val displayName = (destination.name ?: "Commission").let {
                if (it.startsWith('§') && it.length >= 2) it.take(2) + "§l" + it.drop(2) else "§f§l$it"
            }
            ctx.drawText(
                Component.literal(displayName),
                renderPosition,
                shadow = true,
                scale = (renderDistance / 12.0 * targetTextSize).toFloat(),
            )
        }

        on<WorldEvent.Change> { reset() }

        on<ChatEvent.Packet> {
            if (COMMISSION_COMPLETE_TEXT in unformatted) awaitClaim()
        }

        on<MouseEvent.Click> {
            if (button != 0 || !state || mc.gui.screen() != null) return@on
            if (player.mainHandItem.skyblockId == "ROYAL_PIGEON") navigator.skipCurrentTarget()
        }

        on<PacketEvent.ReceivedPost> {
            handleCommissionMenuUpdate(packet)
        }

        on<GuiEvent.Slot.Click> {
            val container = screen as? AbstractContainerScreen<*> ?: return@on
            if (!container.title.string.noControlCodes.equals(COMMISSIONS_MENU, ignoreCase = true)) return@on
            if (slot.item.isCompletedCommission()) {
                hasPigeonData = true
                awaitClaim()
            }
        }
    }

    override fun onDisable() {
        reset()
    }

    private fun updateRoute() {
        if (!navigator.hasCommission && !hasPigeonData && !awaitingClaim && ticks % TABLIST_FALLBACK_TICKS == 1) {
            CommissionDisplay.currentActiveCommissionNames()
                .firstNotNullOfOrNull(graph::targetNameForCommission)
                ?.let(::applyFreshCommission)
        }
        navigator.update(player.position())
    }

    private fun applyFreshCommission(freshCommission: String) {
        awaitingClaim = false
        navigator.startCommission(freshCommission)
    }

    private fun handleCommissionMenuUpdate(packet: Packet<*>) {
        val container = mc.gui.screen() as? AbstractContainerScreen<*> ?: return
        if (!container.title.string.noControlCodes.equals(COMMISSIONS_MENU, ignoreCase = true)) return
        val menu = container.menu
        val updatedSlot = when (packet) {
            is ClientboundContainerSetContentPacket -> {
                if (packet.containerId != menu.containerId) return
                null
            }
            is ClientboundContainerSetSlotPacket -> {
                if (packet.containerId != menu.containerId || packet.slot !in menu.slots.indices) return
                packet.slot
            }
            else -> return
        }
        val preferredSlot = updatedSlot.takeIf { awaitingClaim }
        val activeCommission = menu.slots.map { it.item }
            .selectActiveGlaciteCommission(graph, preferredSlot)
            ?: return

        hasPigeonData = true
        if (activeCommission.routeTarget == null) {
            awaitingClaim = false
            awaitNextCommission()
        } else applyFreshCommission(activeCommission.routeTarget)
    }

    private fun awaitNextCommission() {
        navigator.clearCommission()
    }

    private fun awaitClaim() {
        awaitingClaim = true
        awaitNextCommission()
    }

    private fun reset() {
        navigator.reset()
        awaitingClaim = false
        hasPigeonData = false
        ticks = 0
    }

    private fun hasWalkingLine(from: Vec3, to: Vec3): Boolean {
        val level = mc.level ?: return false
        val eyeOffset = Vec3(0.0, 1.0, 0.0)
        return level.clip(ClipContext(from.add(eyeOffset), to.add(eyeOffset), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).type == HitResult.Type.MISS
    }

    private fun inGlaciteEnvironment(): Boolean = Location.subarea?.let { subarea ->
        GLACITE_SUBAREAS.any { subarea.contains(it, ignoreCase = true) }
    } == true

    private const val TABLIST_FALLBACK_TICKS = 20
    private const val MIN_TEXT_DISTANCE = 5.0
    private const val MAX_TEXT_DISTANCE = 50.0
    private const val COMMISSIONS_MENU = "Commissions"
    private const val COMMISSION_COMPLETE_TEXT = " Commission Complete!"
    private val GLACITE_SUBAREAS = setOf("Dwarven Base Camp", "Glacite Tunnels", "Great Glacite Lake")
}
