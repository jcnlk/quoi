package quoi.utils.render

import com.mojang.blaze3d.pipeline.RenderPipeline
import net.minecraft.client.renderer.rendertype.LayeringTransform
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.resources.Identifier
import net.minecraft.client.renderer.feature.ItemFeatureRenderer
//? if >=26.3
//import net.minecraft.client.renderer.RenderPipelines
//? if >=26.4
//import net.minecraft.client.renderer.pipeline.variant.DebugPrimitiveRenderPipelineVariant

object CustomRenderLayer {
    private val entityOverlays = mutableMapOf<Pair<Identifier, Boolean>, RenderType>()
    private val entityOverlayEspLayers = mutableSetOf<RenderType>()

    @JvmStatic
    fun isEntityOverlayEsp(layer: RenderType): Boolean = layer in entityOverlayEspLayers

    internal fun overlayTexture(layer: RenderType): Identifier? {
        val texture = layer.state.textures["Sampler0"]?.location() ?: return null
        // Keep glint on its own shader
        if (texture == ItemFeatureRenderer.ENCHANTED_GLINT_ARMOR || texture == ItemFeatureRenderer.ENCHANTED_GLINT_ITEM) return null
        return texture
    }

    @JvmStatic
    fun entityOverlay(texture: Identifier, depth: Boolean): RenderType = entityOverlays.getOrPut(texture to depth) {
        create("quoi_entity_overlay", if (depth) CustomRenderPipelines.ENTITY_OVERLAY else CustomRenderPipelines.ENTITY_OVERLAY_ESP) {
            withTexture("Sampler0", texture)
            useLightmap()
            //? if <26.4
            useOverlay()
        }.also { if (!depth) entityOverlayEspLayers.add(it) }
    }

    private fun create(
        name: String,
        pipeline: RenderPipeline,
        configure: RenderSetup.RenderSetupBuilder.() -> Unit = {}
    ): RenderType {
        //? if >=26.4 {
        /*val builder = if (pipeline === CustomRenderPipelines.TRIANGLE_STRIP) {
            RenderSetup.builder { stage ->
                RenderPipelines.debugPrimitive(RenderPipelines.DebugPrimitiveParameters(DebugPrimitiveRenderPipelineVariant.QUADS, stage))
            }
        } else RenderSetup.builder(pipeline)
        *///?} else {
        val builder = RenderSetup.builder(pipeline)
        //?}
        val setup = builder
            .apply(configure)
            .createRenderSetup()
        return RenderType.create(name, setup)
    }

    val TRIANGLE_STRIP: RenderType = create("quoi_debug_quads", CustomRenderPipelines.TRIANGLE_STRIP) {
        //? if >=26.3 && <26.4
        //setOitPipelines(RenderPipelines.OIT_DEBUG_QUADS)
        setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
        sortOnUpload()
    }
    val TRIANGLE_STRIP_ESP: RenderType = create("quoi_debug_quads_esp", CustomRenderPipelines.TRIANGLE_STRIP_ESP) {
        sortOnUpload()
    }
}
