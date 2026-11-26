//~ gui_pipeline

package quoi.api.skyblock.dungeon.odonscanning

import net.minecraft.client.gui.components.PlayerFaceExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import net.minecraft.util.Mth
import net.minecraft.world.entity.player.PlayerSkin
import quoi.QuoiMod.mc
import quoi.api.abobaui.constraints.impl.size.Copying
import quoi.api.abobaui.dsl.size
import quoi.api.abobaui.dsl.withScale
import quoi.api.abobaui.elements.Element
import quoi.api.colour.Colour
import quoi.api.skyblock.dungeon.Dungeon
import quoi.utils.equalsOneOf
import quoi.utils.render.DrawContextUtils.withMatrix
import quoi.utils.skyblock.item.ItemUtils.skyblockId

/**
 * modified Odin (BSD 3-Clause)
 * copyright (c) 2025 odtheking
 * original: https://github.com/odtheking/Odin/blob/main/src/main/kotlin/com/odtheking/odin/features/impl/dungeon/map/MapRenderer.kt
 */
internal class PlayerMarkers(var config: MapRenderer.MapConfig) : Element(size(Copying, Copying)) {
    override var usingCtx = true

    override fun drawCtx() = withScale {
        if (!config.icons) return@withScale
        val localPlayer = mc.player ?: return@withScale
        val partialTick = mc.deltaTracker.getGameTimeDeltaPartialTick(false)
        val showNames = config.icon.name && (!config.icon.whenLeap || localPlayer.mainHandItem.skyblockId.equalsOneOf(
            "SPIRIT_LEAP",
            "INFINITE_SPIRIT_LEAP"
        ))

        ctx.pose().scale(config.scale)
        for (player in Dungeon.dungeonTeammatesNoSelf) {
            if (player.isDead) continue
            val position = player.position(partialTick) ?: continue
            drawMarker(
                position, player.yaw(partialTick), player.playerSkin, player.clazz.colour,
                if (showNames) player.name else null, config.icon.heads
            )
        }

        val self = Dungeon.currentDungeonPlayer
        if (!self.isDead) {
            val position = localPlayer.getPosition(partialTick)
            drawMarker(
                MapScan.worldPosition(position.x, position.z),
                Mth.rotLerp(partialTick, localPlayer.yRotO, localPlayer.yRot),
                localPlayer.skin, self.clazz.colour, null, config.icon.ownHead
            )
        }
    }

    private fun drawMarker(
        position: Pair<Float, Float>,
        yaw: Float,
        skin: PlayerSkin?,
        classColour: Colour,
        name: String?,
        head: Boolean
    ) {
        val (px, pz) = position
        if (px !in 0f..(width / config.scale) || pz !in 0f..(height / config.scale)) return
        val icon = config.icon
        ctx.withMatrix(px, pz) {
            if (name != null) {
                ctx.withMatrix(scale = icon.nameScale) {
                    ctx.text(
                        mc.font,
                        name,
                        -mc.font.width(name) / 2,
                        ((4 * icon.scale + 2) / icon.nameScale).toInt(),
                        Colour.WHITE.rgb,
                        config.shadow
                    )
                }
            }
            ctx.pose().rotate(Math.toRadians(180.0 + yaw).toFloat())
            ctx.pose().scale(icon.scale)
            if (head && skin != null) {
                if (icon.border) {
                    val borderScale = 1f + icon.thickness / (4f * config.scale * icon.scale)
                    ctx.withMatrix(scale = borderScale) {
                        ctx.fill(-4, -4, 4, 4, (if (icon.classColour) classColour else icon.borderColour).rgb)
                    }
                }
                PlayerFaceExtractor.extractRenderState(ctx, skin, -4, -4, 8)
            } else {
                ctx.blit(RenderPipelines.GUI_TEXTURED, ARROW, -2, -4, 2f, 0f, 5, 7, 8, 8)
            }
        }
    }

    companion object {
        private val ARROW = Identifier.withDefaultNamespace("textures/map/decorations/frame.png")
    }
}
