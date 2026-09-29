package quoi.utils.ui.rendering

import quoi.QuoiMod.mc
import quoi.api.colour.Colour
import quoi.utils.ui.data.Gradient
import quoi.utils.ui.data.Radii
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.resources.Identifier
import java.io.File

/**
 * Facade for all custom UI drawing. Keeps the renderer API the abobaui framework and the
 * modules were written against, while the actual drawing is delegated to a [RendererBackend].
 *
 * Coordinates are physical window pixels; see [RendererBackend].
 */
object UIRenderer {

    val backend: RendererBackend = McBackend()

    val defaultFont = Font("Default", mc.resourceManager.getResource(Identifier.parse("quoi:font.ttf")).get().open())
    val customFont = Font("Custom", File("config/quoi!/font.ttf").takeIf { it.exists() }?.inputStream() ?: mc.resourceManager.getResource(Identifier.parse("quoi:font.ttf")).get().open())
    val minecraftFont = Font("Minecraft")

    private val imageCache = HashMap<String, Image>()

    /**
     * Composites everything drawn by [content] into the GUI frame represented by [ctx].
     * Replaces the old direct NVGSpecialRenderer picture-in-picture call.
     */
    fun frame(ctx: GuiGraphicsExtractor, content: () -> Unit) = backend.frame(ctx, content)

    fun push() = backend.push()

    fun pop() = backend.pop()

    fun scale(x: Float, y: Float) = backend.scale(x, y)

    fun translate(x: Float, y: Float) = backend.translate(x, y)

    fun rotate(amount: Float) = backend.rotate(amount)

    fun globalAlpha(amount: Float) = backend.globalAlpha(amount.coerceIn(0f, 1f))

    fun pushScissor(x: Float, y: Float, w: Float, h: Float) = backend.pushScissor(x, y, w, h)

    fun popScissor() = backend.popScissor()

    fun line(x1: Float, y1: Float, x2: Float, y2: Float, thickness: Float, color: Int) =
        backend.line(x1, y1, x2, y2, thickness, color)

    fun rect(x: Float, y: Float, w: Float, h: Float, color: Int, tl: Float, bl: Float, br: Float, tr: Float) =
        backend.rect(x, y, w, h, color, tl, bl, br, tr)

    fun rect(x: Float, y: Float, w: Float, h: Float, color: Int, radius: Float = 0f) =
        rect(x, y, w, h, color, radius, radius, radius, radius)

    fun rect(x: Float, y: Float, w: Float, h: Float, color: Int, radii: Radii) =
        rect(x, y, w, h, color, radii.topLeft, radii.bottomLeft, radii.bottomRight, radii.topRight)

    fun rect(x: Float, y: Float, w: Float, h: Float, color: Int) =
        rect(x, y, w, h, color, 0f, 0f, 0f, 0f)

    fun hollowRect(x: Float, y: Float, w: Float, h: Float, thickness: Float, color: Int, tl: Float, bl: Float, br: Float, tr: Float) =
        backend.hollowRect(x, y, w, h, thickness, color, tl, bl, br, tr)

    fun hollowRect(x: Float, y: Float, w: Float, h: Float, thickness: Float, color: Int, radius: Float = 0f) =
        hollowRect(x, y, w, h, thickness, color, radius, radius, radius, radius)

    fun hollowRect(x: Float, y: Float, w: Float, h: Float, thickness: Float, color: Int, radii: Radii) =
        hollowRect(x, y, w, h, thickness, color, radii.topLeft, radii.bottomLeft, radii.bottomRight, radii.topRight)

    fun gradientRect(
        x: Float, y: Float, w: Float, h: Float, color1: Int, color2: Int, gradient: Gradient,
        tl: Float, bl: Float, br: Float, tr: Float,
    ) = backend.gradientRect(x, y, w, h, color1, color2, gradient, tl, bl, br, tr)

    fun gradientRect(
        x: Float, y: Float, w: Float, h: Float, color1: Int, color2: Int, direction: Gradient, radius: Float = 0f
    ) = gradientRect(x, y, w, h, color1, color2, direction, radius, radius, radius, radius)

    fun gradientRect(
        x: Float, y: Float, w: Float, h: Float, color1: Int, color2: Int, direction: Gradient, radii: Radii
    ) = gradientRect(x, y, w, h, color1, color2, direction, radii.topLeft, radii.bottomLeft, radii.bottomRight, radii.topRight)

    fun dropShadow(
        x: Float, y: Float, width: Float, height: Float, blur: Float, spread: Float,
        radius: Float = 0f, colour: Int = Colour.RGB(0, 0, 0, 0.5f).rgb,
    ) = backend.dropShadow(x, y, width, height, blur, spread, radius, colour)

    fun dropShadow(x: Float, y: Float, w: Float, h: Float, blur: Float, spread: Float, radii: Radii, colour: Int = Colour.RGB(0, 0, 0, 0.5f).rgb) =
        dropShadow(x, y, w, h, blur, spread, radii.topLeft, colour)

    fun circle(x: Float, y: Float, radius: Float, color: Int) = backend.circle(x, y, radius, color)

    fun text(text: String, x: Float, y: Float, size: Float, color: Int, font: Font) =
        backend.text(text, x, y, size, color, font)

    fun formattedText(text: String, x: Float, y: Float, size: Float, colour: Int, font: Font) {
        var x = x
        for (segment in TextEngine.parse(text, colour)) {
            text(segment.text, x, y, size, segment.color, font)

            val w = textWidth(segment.text, size, font)
            val t = size / 12f
            if (segment.underline) {
                line(x, y + size * 0.95f, x + w, y + size * 0.95f, t, segment.color)
            }

            if (segment.strike) {
                line(x, y + size * 0.5f, x + w, y + size * 0.5f, t, segment.color)
            }

            x += w
        }
    }

    fun textWidth(text: String, size: Float, font: Font): Float = backend.textWidth(text, size, font)

    fun wrapText(text: String, maxWidth: Float, size: Float, font: Font): List<String> =
        TextEngine.wrap(text, maxWidth) { textWidth(it, size, font) }

    fun image(image: Image, x: Float, y: Float, w: Float, h: Float, tl: Float, bl: Float, br: Float, tr: Float, color: Int? = null) =
        backend.image(image, x, y, w, h, tl, bl, br, tr, color)

    fun image(image: Image, x: Float, y: Float, w: Float, h: Float, radii: Radii, color: Int? = null) =
        image(image, x, y, w, h, radii.topLeft, radii.bottomLeft, radii.bottomRight, radii.topRight, color)

    fun image(image: Image, x: Float, y: Float, w: Float, h: Float) =
        image(image, x, y, w, h, 0f, 0f, 0f, 0f, null)

    fun String.image() = createImage("/assets/quoi/ui/images/$this")

    fun createImage(resourcePath: String): Image {
        val image = imageCache.getOrPut(resourcePath) { Image(resourcePath) }
        backend.registerImage(image)
        return image
    }

    // lowers reference count by 1, if it reaches 0 it gets deleted from mem
    fun deleteImage(image: Image) {
        if (backend.deleteImage(image)) imageCache.remove(image.identifier)
    }
}
