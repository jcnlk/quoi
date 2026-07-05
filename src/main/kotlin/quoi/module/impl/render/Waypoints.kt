package quoi.module.impl.render

import net.minecraft.core.BlockPos
import net.minecraft.client.renderer.blockentity.BeaconRenderer
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import quoi.api.colour.Colour
import quoi.api.events.ChatEvent
import quoi.api.events.RenderEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.on
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.utils.ChatUtils.modMessage
import quoi.utils.StringUtils.toFixed
import quoi.utils.render.drawFilledBox
import quoi.utils.render.drawText
import quoi.utils.render.drawWireFrameBox
import kotlin.math.abs

object Waypoints : Module(
    "Waypoints",
    desc = "Creates temporary waypoints from coordinates in chat."
) {
    private val fromParty by switch("Party chat").asParent()
    private val partyClearOnArrive by switch("Clear on arrive").childOf(::fromParty)
    private val partyDuration by slider("Duration", 120, 0, 300, 1, unit = "s", desc = "How long party waypoints remain visible. 0 keeps them permanently.").childOf(::fromParty)
    private val partyColour by colourPicker("Colour", Colour.RGB(85, 125, 255, 0.67f), true).childOf(::fromParty)

    private val fromAll by switch("All chat").asParent()
    private val allClearOnArrive by switch("Clear on arrive").childOf(::fromAll)
    private val allDuration by slider("Duration", 60, 0, 300, 1, unit = "s", desc = "How long public-chat waypoints remain visible. 0 keeps them permanently.").childOf(::fromAll)
    private val allColour by colourPicker("Colour", Colour.RGB(85, 220, 220, 0.67f), true).childOf(::fromAll)

    private val personalWaypoints by switch("Create from own messages", desc = "Creates a waypoint when your own coordinates message appears in party or public chat.")

    private val partyRegex = Regex("^Party > (?:\\[[^]]*])? ?(\\w{1,16})(?: [ቾ⚒])?: x: (-?\\d+),? y: (-?\\d+),? z: (-?\\d+).*")
    private val allRegex = Regex("^(?!Party >).*?(?:\\[[^]]*])? ?(\\w{1,16})(?: [ቾ⚒])?: x: (-?\\d+),? y: (-?\\d+),? z: (-?\\d+).*")

    private val waypoints = mutableListOf<Waypoint>()
    init {
        on<ChatEvent.Packet> {
            val partyMatch = if (fromParty) partyRegex.matchEntire(unformatted) else null
            val match = partyMatch ?: if (fromAll) allRegex.matchEntire(unformatted) else null
            val (sender, x, y, z) = match?.destructured ?: return@on
            if (!personalWaypoints && sender == player.name.string) return@on
            val type = if (partyMatch != null) WaypointType.PARTY else WaypointType.ALL
            add(sender, x.toInt(), y.toInt(), z.toInt(), type)
        }

        on<RenderEvent.World> {
            val now = System.currentTimeMillis()
            waypoints.removeIf { waypoint ->
                val distance = player.position().distanceTo(waypoint.center)
                val expired = now >= waypoint.expiresAt
                val arrived = waypoint.clearOnArrive && distance <= 8.0
                if (!expired && !arrived) renderWaypoint(waypoint, distance)
                expired || arrived
            }
        }

        on<WorldEvent.Change> { waypoints.clear() }
    }

    private fun RenderEvent.World.renderWaypoint(waypoint: Waypoint, distance: Double) {
        val box = AABB(waypoint.pos)
        val colour = waypoint.colour
        renderBeaconBeam(ctx, waypoint.pos, colour.rgb)
        ctx.drawFilledBox(box, colour)
        ctx.drawWireFrameBox(box, colour, 3f)
        val label = Component.literal("${waypoint.name} §7(${distance.toFixed(1)}m)")
        ctx.drawText(label, waypoint.center.add(0.0, 1.0, 0.0), shadow = true, scale = 1f)
    }

    private fun renderBeaconBeam(ctx: WorldRenderContext, pos: BlockPos, colour: Int) {
        val pose = com.mojang.blaze3d.vertex.PoseStack()
        val cameraPos = mc.gameRenderer.mainCamera.position()
        val time = (level.gameTime + mc.deltaTracker.getGameTimeDeltaPartialTick(true)).toFloat()

        pose.pushPose()
        pose.translate(pos.x - cameraPos.x, pos.y - cameraPos.y, pos.z - cameraPos.z)
        BeaconRenderer.submitBeaconBeam(
            pose,
            ctx.commandQueue(),
            BeaconRenderer.BEAM_LOCATION,
            1.0f,
            time,
            colour,
            0,
            160,
            0.2f,
            0.25f
        )
        pose.popPose()
    }

    private fun add(name: String, x: Int, y: Int, z: Int, type: WaypointType) {
        if (listOf(x, y, z).any { abs(it) > 30_000_000 }) return modMessage("&cWaypoint is out of bounds.")
        waypoints.removeIf { it.name.equals(name, ignoreCase = true) }
        if (waypoints.any { it.pos.x == x && it.pos.y == y && it.pos.z == z }) {
            return modMessage("&cA waypoint already exists at $x, $y, $z.")
        }
        val expiresAt = type.duration.takeIf { it > 0 }
            ?.let { System.currentTimeMillis() + it * 1000L }
            ?: Long.MAX_VALUE
        waypoints += Waypoint(name.ifBlank { "Waypoint" }, BlockPos(x, y, z), expiresAt, type)
    }

    private data class Waypoint(
        val name: String,
        val pos: BlockPos,
        val expiresAt: Long,
        val type: WaypointType
    ) {
        val center: Vec3 get() = Vec3.atCenterOf(pos)
        val colour: Colour get() = type.colour
        val clearOnArrive: Boolean get() = type.clearOnArrive
    }

    enum class WaypointType {
        PARTY, ALL;

        val colour: Colour
            get() = when (this) {
                PARTY -> partyColour
                ALL -> allColour
            }

        val duration: Int
            get() = when (this) {
                PARTY -> partyDuration
                ALL -> allDuration
            }

        val clearOnArrive: Boolean
            get() = when (this) {
                PARTY -> partyClearOnArrive
                ALL -> allClearOnArrive
            }
    }
}
