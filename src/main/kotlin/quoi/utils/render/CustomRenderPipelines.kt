package quoi.utils.render

import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.ColorTargetState
import com.mojang.blaze3d.pipeline.DepthStencilState
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.CompareOp
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import net.minecraft.client.renderer.RenderPipelines
//? if >=26.4-snapshot-5
//import net.minecraft.client.renderer.BindGroupLayouts
//$ primitive_type_import
import com.mojang.blaze3d.vertex.VertexFormat

/**
 * from OdinFabric (BSD 3-Clause)
 * copyright (c) 2025-2026 odtheking
 * original: https://github.com/odtheking/OdinFabric/blob/main/src/main/kotlin/com/odtheking/odin/utils/render/CustomRenderPipelines.kt
 */
object CustomRenderPipelines {
    //? if >=26.4-snapshot-5 {
    /*private val filledSnippet = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
        .withBindGroupLayout(BindGroupLayouts.PROJECTION)
        .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
        .withVertexShader("core/position_color")
        .withFragmentShader("core/position_color")
        .buildSnippet()
    *///?}

    // Follow vanilla's depth convention, which became reversed Z in 26.2.
    private val testedDepth = DepthStencilState(DepthStencilState.DEFAULT.depthTest(), false)
    //$ pipeline_start TRIANGLE_STRIP
    val TRIANGLE_STRIP: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation("quoi/pipeline/debug_filled_box")
            .withCull(false)
            //$ pipeline_vertices POSITION_COLOR QUADS {
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
            //$}
            .withDepthStencilState(testedDepth)
            .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
            .build()
    //$ pipeline_end {
    )
    //$}

    //$ pipeline_start TRIANGLE_STRIP_ESP
    val TRIANGLE_STRIP_ESP: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation("quoi/pipeline/debug_filled_box_esp")
            .withCull(false)
            //$ pipeline_vertices POSITION_COLOR QUADS {
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
            //$}
            .withDepthStencilState(DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
            .build()
    //$ pipeline_end {
    )
    //$}
}
