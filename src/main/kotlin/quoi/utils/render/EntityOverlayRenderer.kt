package quoi.utils.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.QuadInstance
import net.minecraft.client.renderer.OrderedSubmitNodeCollector
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.resources.model.geometry.BakedQuad

/**
 * Tracks the current entity's overlay while submitting its model and held items
 */
object EntityOverlayRenderer {
    private val current = ThreadLocal<EntityOverlayState>()

    @JvmStatic
    fun setState(state: EntityOverlayState?): EntityOverlayState? {
        val previous = current.get()
        if (state != null && state.`quoi$getOverlayColour`() ushr 24 != 0) current.set(state)
        else current.remove()
        return previous
    }

    @JvmStatic
    fun renderType(original: RenderType): RenderType {
        val state = current.get() ?: return original
        val texture = CustomRenderLayer.overlayTexture(original) ?: return original
        return CustomRenderLayer.entityOverlay(texture, state.`quoi$getOverlayDepth`())
    }

    @JvmStatic
    fun overlayCoords(original: Int, layer: RenderType): Int {
        val state = current.get() ?: return original
        if (CustomRenderLayer.overlayTexture(layer) == null) return original
        // UV1 stores the overlay colour as ARGB
        return state.`quoi$getOverlayColour`()
    }

    @JvmStatic
    fun submitItem(
        collector: OrderedSubmitNodeCollector,
        stack: PoseStack,
        light: Int,
        tints: IntArray,
        quads: List<BakedQuad>
    ): Boolean {
        val state = current.get() ?: return false
        val colour = state.`quoi$getOverlayColour`()
        val depth = state.`quoi$getOverlayDepth`()
        val textures = quads.groupBy { CustomRenderLayer.overlayTexture(it.materialInfo().itemRenderType()) ?: return false }
        for ((texture, geometry) in textures) {
            collector.submitCustomGeometry(stack, CustomRenderLayer.entityOverlay(texture, depth)) { pose, vertices ->
                val instance = QuadInstance()
                instance.setLightCoords(light)
                instance.setOverlayCoords(colour)
                for (quad in geometry) {
                    instance.setColor(tints.getOrElse(quad.materialInfo().tintIndex()) { -1 })
                    vertices.putBakedQuad(pose, quad, instance)
                }
            }
        }
        return true
    }
}
