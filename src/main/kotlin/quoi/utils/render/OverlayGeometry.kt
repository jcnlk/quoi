package quoi.utils.render

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.SubmitNodeCollection
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.feature.CustomFeatureRenderer

internal fun SubmitNodeCollector.submitOverlayGeometry(
    stack: PoseStack,
    depth: Boolean,
    renderer: SubmitNodeCollector.CustomGeometryRenderer
) {
    if (depth) {
        submitCustomGeometry(stack, CustomRenderLayer.TRIANGLE_STRIP, renderer)
    } else {
        val collection = order(Int.MAX_VALUE) as SubmitNodeCollection
        val submit = CustomFeatureRenderer.Submit(stack.last().copy(), CustomRenderLayer.TRIANGLE_STRIP_ESP, renderer)
        //~ if >=26.3 'collection.alwaysOnTop.' -> 'collection.alwaysOnTopGizmos.'
        collection.alwaysOnTop.submit(submit)
    }
}
