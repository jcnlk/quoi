package quoi.utils.ui.rendering

import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.renderer.texture.DynamicTexture
import java.io.FileNotFoundException

/**
 * Image storage for the Minecraft-native UI backend: decodes PNG/JPEG bytes through
 * [NativeImage] (stb-based, backend-neutral) into [DynamicTexture]s with reference counting.
 *
 * SVG assets are not rasterized at runtime any more (NanoSVG left together with NanoVG);
 * every bundled `*.svg` ships a pre-rasterized `*.svg.png` sibling which is loaded instead.
 */
class McImages {

    class Entry(val texture: DynamicTexture, val width: Int, val height: Int) {
        var count = 0
    }

    private val entries = HashMap<Image, Entry>()

    fun register(image: Image) {
        entries.getOrPut(image) { load(image) }.count++
    }

    fun delete(image: Image): Boolean {
        val entry = entries[image] ?: return false
        entry.count--
        if (entry.count == 0) {
            entry.texture.close()
            entries.remove(image)
            return true
        }
        return false
    }

    fun entry(image: Image): Entry =
        entries[image] ?: throw IllegalStateException("Image (${image.identifier}) doesn't exist")

    private fun load(image: Image): Entry {
        val bytes = if (image.isSVG) {
            // the constructor-opened stream points at the .svg source; replace it with the
            // pre-rasterized sibling
            runCatching { image.stream.close() }
            val path = image.identifier + ".png"
            (javaClass.getResourceAsStream(path) ?: throw FileNotFoundException("$path (pre-rasterized SVG missing)"))
                .use { it.readBytes() }
        } else {
            image.stream.use { it.readBytes() }
        }

        val nativeImage = NativeImage.read(bytes)
        val texture = DynamicTexture({ "quoi! ui image ${image.identifier}" }, nativeImage)
        return Entry(texture, nativeImage.width, nativeImage.height)
    }
}
