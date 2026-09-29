package quoi.utils.ui.rendering

import net.minecraft.client.gui.GuiGraphicsExtractor

/**
 * Backend abstraction for the custom UI renderer.
 *
 * All coordinates are in physical window pixels (the abobaui coordinate space).
 * Implementations must support nested [push]/[pop] state saving, an affine transform
 * stack ([translate]/[scale]/[rotate]), a scissor stack and a global alpha multiplier,
 * matching NanoVG semantics which the UI framework was originally written against.
 */
interface RendererBackend {

    /**
     * Runs [content] so that all primitives drawn inside end up composited into the
     * current GUI frame represented by [ctx].
     */
    fun frame(ctx: GuiGraphicsExtractor, content: () -> Unit)

    fun push()

    fun pop()

    fun translate(x: Float, y: Float)

    fun scale(x: Float, y: Float)

    fun rotate(radians: Float)

    fun globalAlpha(amount: Float)

    fun pushScissor(x: Float, y: Float, w: Float, h: Float)

    fun popScissor()

    fun line(x1: Float, y1: Float, x2: Float, y2: Float, thickness: Float, color: Int)

    fun rect(x: Float, y: Float, w: Float, h: Float, color: Int, tl: Float, bl: Float, br: Float, tr: Float)

    fun hollowRect(x: Float, y: Float, w: Float, h: Float, thickness: Float, color: Int, tl: Float, bl: Float, br: Float, tr: Float)

    fun gradientRect(x: Float, y: Float, w: Float, h: Float, color1: Int, color2: Int, direction: quoi.utils.ui.data.Gradient, tl: Float, bl: Float, br: Float, tr: Float)

    fun dropShadow(x: Float, y: Float, width: Float, height: Float, blur: Float, spread: Float, radius: Float, colour: Int)

    fun circle(x: Float, y: Float, radius: Float, color: Int)

    fun text(text: String, x: Float, y: Float, size: Float, color: Int, font: Font)

    fun textWidth(text: String, size: Float, font: Font): Float

    fun image(image: Image, x: Float, y: Float, w: Float, h: Float, tl: Float, bl: Float, br: Float, tr: Float, color: Int?)

    /** Increments the reference count for [image], loading it if necessary. */
    fun registerImage(image: Image)

    /**
     * Decrements the reference count for [image], freeing it when it reaches zero.
     * Returns true when the image was fully released.
     */
    fun deleteImage(image: Image): Boolean
}
