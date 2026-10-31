package quoi.utils.render

import com.mojang.blaze3d.pipeline.RenderPipeline
import net.minecraft.client.renderer.rendertype.LayeringTransform
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
//? if >=26.3
//import net.minecraft.client.renderer.RenderPipelines
//? if >=26.4-snapshot-5
//import net.minecraft.client.renderer.pipeline.variant.DebugPrimitiveRenderPipelineVariant

object CustomRenderLayer {
    private fun create(
        name: String,
        pipeline: RenderPipeline,
        configure: RenderSetup.RenderSetupBuilder.() -> Unit = {}
    ): RenderType {
        //? if >=26.4-snapshot-5 {
        /*val builder = if (pipeline === CustomRenderPipelines.TRIANGLE_STRIP) {
            RenderSetup.builder { stage ->
                RenderPipelines.debugPrimitive(RenderPipelines.DebugPrimitiveParameters(DebugPrimitiveRenderPipelineVariant.QUADS, stage))
            }
        } else RenderSetup.nonOitBuilder(pipeline)
        *///?} else {
        val builder = RenderSetup.builder(pipeline)
        //?}
        val setup = builder
            //? if <26.2
            .bufferSize(RenderType.TRANSIENT_BUFFER_SIZE)
            .apply(configure)
            .createRenderSetup()
        return RenderType.create(name, setup)
    }

    val TRIANGLE_STRIP: RenderType = create("quoi_debug_quads", CustomRenderPipelines.TRIANGLE_STRIP) {
        //? if >=26.3 && <26.4-snapshot-5
        //setOitPipelines(RenderPipelines.OIT_DEBUG_QUADS)
        setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
        sortOnUpload()
    }
    val TRIANGLE_STRIP_ESP: RenderType = create("quoi_debug_quads_esp", CustomRenderPipelines.TRIANGLE_STRIP_ESP) {
        sortOnUpload()
    }
}
