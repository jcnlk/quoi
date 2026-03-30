package quoi.utils.render

import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.rendertype.RenderTypes

/**
 * 1.21.11 no longer exposes RenderType.create publicly, so use the closest
 * public vanilla render types for the existing debug geometry paths.
 */
object CustomRenderLayer {
    val LINE_LIST: RenderType = RenderTypes.lines()
    val LINE_LIST_ESP: RenderType = RenderTypes.linesTranslucent()
    val TRIANGLE_STRIP: RenderType = RenderTypes.debugFilledBox()
    val TRIANGLE_STRIP_ESP: RenderType = RenderTypes.debugQuads()
}
