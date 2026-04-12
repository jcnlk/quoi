package quoi.module.impl.mining

import net.minecraft.core.BlockPos
import net.minecraft.client.renderer.blockentity.BeaconRenderer
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.decoration.ArmorStand
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.events.AreaEvent
import quoi.api.events.RenderEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.EventBus
import quoi.api.skyblock.Island
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.visibleIf
import quoi.utils.ChatUtils.literal
import quoi.utils.EntityUtils.getEntities
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.aabb
import quoi.utils.render.drawStyledBox
import quoi.utils.render.drawText
import quoi.utils.render.drawTracer
import quoi.utils.skyblock.ItemUtils.extraAttributes
import quoi.utils.skyblock.ItemUtils.skyblockId
import quoi.utils.vec3
import kotlin.math.sqrt

object MineshaftESP : Module(
    "Mineshaft ESP",
    area = Island.Mineshaft,
    desc = "Highlights Umber, Tungsten, Lapis and Vanguard spots in Glacite Mineshafts."
) {
    private val names by switch("Show names", true, desc = "Shows a label above detected spots.")
    private val box by switch("Box", true, desc = "Renders an outline box at detected spots.")
    private val fillBox by switch("Fill", false, desc = "Renders a filled box at detected spots.")
    private val beaconBeam by switch("Beacon beam", true, desc = "Renders a vertical beacon beam at detected spots.")
    private val tracer by switch("Tracer", true, desc = "Renders a tracer to detected spots.")
    private val depth by switch("Depth check", false, desc = "Whether the ESP should respect depth.")
    private val fillAlpha by slider("Fill alpha", 0.7f, 0.05f, 1f, 0.05f).visibleIf { fillBox }
    private val thickness by slider("Thickness", 4f, 1f, 8f, 1f)
    private val beamHeight by slider("Beacon height", 160, 16, 384, 8).visibleIf { beaconBeam }
    private val tracerThickness by slider("Tracer thickness", 4f, 1f, 8f, 1f).visibleIf { tracer }

    private val waypoints = linkedMapOf<BlockPos, MineshaftType>()

    init {
        events.add(EventBus.on<WorldEvent.Change>(
            callback = { waypoints.clear() },
            add = false
        ))

        events.add(EventBus.on<AreaEvent.Main>(
            callback = {
                if (this.area != Island.Mineshaft) waypoints.clear()
            },
            add = false
        ))

        on<TickEvent.End> {
            if (player.tickCount % 20 != 0) return@on
            scanWaypoints()
        }

        on<RenderEvent.World> {
            waypoints.forEach { (pos, type) ->
                val waypointBox = pos.aabb
                val colour = type.colour
                val fillColour = colour.withAlpha(fillAlpha)

                if (box || fillBox) {
                    ctx.drawStyledBox(if (fillBox) "Filled box" else "Box", waypointBox, colour, fillColour, thickness, depth)
                }

                if (beaconBeam) {
                    renderBeaconBeam(ctx, pos, colour.rgb)
                }

                val textPos = pos.vec3.add(0.5, 2.5, 0.5)
                if (names) {
                    val scale = (0.5 + sqrt(player.distanceToSqr(textPos.x, textPos.y, textPos.z)) / 10.0).toFloat()
                    ctx.drawText(literal(type.label), textPos, scale = scale, depth = false)
                }

                if (tracer) {
                    ctx.drawTracer(pos.vec3.add(0.5, 1.5, 0.5), colour, tracerThickness, depth)
                }
            }
        }
    }

    private fun scanWaypoints() {
        val found = linkedMapOf<BlockPos, MineshaftType>()

        getEntities<ArmorStand>().forEach { stand ->
            val type = stand.getMineshaftType() ?: return@forEach
            found.putIfAbsent(stand.blockPosition(), type)
        }

        waypoints.clear()
        waypoints.putAll(found)
    }

    private fun renderBeaconBeam(ctx: WorldRenderContext, pos: BlockPos, colour: Int) {
        val pose = com.mojang.blaze3d.vertex.PoseStack()
        val cameraPos = mc.gameRenderer.mainCamera.position()
        val time = (level.gameTime + mc.deltaTracker.getGameTimeDeltaPartialTick(true)).toFloat()

        pose.pushPose()
        pose.translate(
            pos.x.toDouble() - cameraPos.x,
            pos.y.toDouble() - cameraPos.y,
            pos.z.toDouble() - cameraPos.z
        )
        BeaconRenderer.submitBeaconBeam(
            pose,
            ctx.commandQueue(),
            BeaconRenderer.BEAM_LOCATION,
            1.0f,
            time,
            colour,
            0,
            beamHeight,
            0.2f,
            0.25f
        )
        pose.popPose()
    }

    private fun ArmorStand.getMineshaftType(): MineshaftType? {
        val helmet = getItemBySlot(EquipmentSlot.HEAD).takeUnless { it.isEmpty } ?: return null
        val id = helmet.skyblockId ?: helmet.extraAttributes?.toString()
        return MineshaftType.entries.firstOrNull { type ->
            id?.contains(type.skyblockId, true) == true ||
                helmet.hoverName.string.noControlCodes.contains(type.displayName, true)
        }
    }

    private enum class MineshaftType(
        val skyblockId: String,
        val displayName: String,
        val label: String,
        val colour: Colour
    ) {
        LAPIS("LAPIS_ARMOR_HELMET", "Lapis", "&9&lLapis", Colour.RGB(85, 85, 255)),
        UMBER("ARMOR_OF_YOG_HELMET", "Umber", "&6&lUmber", Colour.RGB(255, 170, 0)),
        TUNGSTEN("MINERAL_HELMET", "Tungsten", "&7&lTungsten", Colour.RGB(170, 170, 170)),
        VANGUARD("VANGUARD_HELMET", "Vanguard", "&b&lVanguard", Colour.RGB(85, 255, 255))
    }
}
