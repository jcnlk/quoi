package quoi.utils.ui.rendering

import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.renderer.texture.DynamicTexture
import org.lwjgl.stb.STBTTFontinfo
import org.lwjgl.stb.STBTruetype.*
import org.lwjgl.system.MemoryStack
import java.nio.ByteBuffer
import kotlin.math.floor
import kotlin.math.max

/**
 * CPU-side TrueType glyph rasterizer + atlas for the Minecraft-native UI backend.
 *
 * Uses STB TrueType (already a vanilla dependency, and the same rasterizer family NanoVG
 * used) so glyph shapes and metrics match the previous renderer. Glyph bitmaps are packed
 * into [DynamicTexture] atlas pages; all GPU access goes through Minecraft's abstractions,
 * so this works on every RenderDevice backend (OpenGL and Vulkan).
 *
 * Metrics model replicates fontstash/NanoVG:
 *  - pixel scale = stbtt_ScaleForPixelHeight (size spans ascent..descent),
 *  - top-aligned text: baseline = y + ascent * scale,
 *  - advances accumulate kerning, widths are continuous in `size`.
 *
 * Bitmaps are rasterized at integer pixel-size buckets and scaled by size/bucket when
 * drawn; measurements always use the exact float size.
 */
class McFonts {

    companion object {
        const val PAGE_SIZE = 512
        const val PADDING = 1
        const val MIN_BUCKET = 4
        const val MAX_BUCKET = 96
    }

    class GlyphQuad(
        val texture: DynamicTexture,
        val x0: Float, val y0: Float, val x1: Float, val y1: Float,
        val u0: Float, val v0: Float, val u1: Float, val v1: Float,
    )

    private val loadedFonts = HashMap<Font, LoadedFont>()
    private val sizedFonts = HashMap<Pair<Font, Int>, SizedFont>()
    private val pages = ArrayList<AtlasPage>()

    private fun loaded(font: Font): LoadedFont =
        loadedFonts.getOrPut(font) { LoadedFont(font) }

    private fun sized(font: Font, bucket: Int): SizedFont =
        sizedFonts.getOrPut(font to bucket) { SizedFont(loaded(font), bucket) }

    /**
     * Fontstash stores raster sizes in tenths of a pixel. Keeping that precision matters
     * on HiDPI displays: NanoVG rasterized at size * devicePixelRatio (and transformed
     * text scale), then scaled the atlas quad back into UI coordinates.
     */
    private fun bucketFor(size: Float, rasterScale: Float): Int =
        (size * rasterScale * 10f).toInt().coerceIn(MIN_BUCKET * 10, MAX_BUCKET * 10)

    /** Advance width of [text] at the exact float [size], including kerning. */
    fun width(font: Font, text: String, size: Float): Float {
        val loaded = loaded(font)
        val scale = loaded.scaleFor(size)
        var width = 0f
        var prevGlyph = -1
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            val glyph = loaded.glyphIndex(cp)
            // continuous float metrics: matches NanoVG behaviour on hidpi setups, where
            // fontstash's integer pen steps happen at size*devicePixelRatio and get scaled
            // back down, so effective widths are fractional (this also preserves the
            // stable ClickGUI width-sort order)
            if (prevGlyph != -1) width += loaded.kern(prevGlyph, glyph) * scale
            width += loaded.advance(glyph) * scale
            prevGlyph = glyph
        }
        return width
    }

    /** Pixel ascent (distance from text top to baseline) at the float [size]. */
    fun ascent(font: Font, size: Float): Float {
        val loaded = loaded(font)
        return loaded.ascent * loaded.scaleFor(size)
    }

    /**
     * Lays out [text] top-left anchored at ([x],[y]) and invokes [emit] for every visible
     * glyph with atlas texture + geometry in window-pixel coordinates.
     */
    fun layout(
        font: Font,
        text: String,
        x: Float,
        y: Float,
        size: Float,
        rasterScale: Float = 1f,
        emit: (GlyphQuad) -> Unit,
    ) {
        val loaded = loaded(font)
        val scale = loaded.scaleFor(size)
        val bucket = bucketFor(size, rasterScale)
        val sizedFont = sized(font, bucket)
        val rasterSize = bucket / 10f
        val bitmapScale = size / rasterSize
        val effectiveRasterScale = rasterSize / size

        val baseline = y + loaded.ascent * scale

        var penX = x
        var prevGlyph = -1
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            val glyph = loaded.glyphIndex(cp)
            // pen advances stay continuous (see width()); only the bitmap origin is
            // snapped to the pixel grid like fontstash's floorf(x + xoff), which keeps
            // glyphs crisp under 1:1 texel mapping without changing metrics
            if (prevGlyph != -1) penX += loaded.kern(prevGlyph, glyph) * scale

            val sprite = sizedFont.sprite(glyph)
            if (sprite != null && sprite.w > 0 && sprite.h > 0) {
                // Fontstash snaps in atlas/raster space and NanoVG then applies
                // invscale. Snapping directly in UI space makes HiDPI text smaller and
                // thinner, and causes transformed text to shimmer while moving.
                val gx = floor((penX + sprite.xoff * bitmapScale) * effectiveRasterScale) / effectiveRasterScale
                val gy = floor((baseline + sprite.yoff * bitmapScale) * effectiveRasterScale) / effectiveRasterScale
                emit(
                    GlyphQuad(
                        sprite.page.texture,
                        gx, gy,
                        gx + sprite.w * bitmapScale, gy + sprite.h * bitmapScale,
                        sprite.u0, sprite.v0, sprite.u1, sprite.v1,
                    )
                )
            }

            penX += loaded.advance(glyph) * scale
            prevGlyph = glyph
        }
    }

    /** Uploads any atlas pages that gained new glyphs since the last call. */
    fun flushUploads() {
        pages.forEach { page ->
            if (page.dirty) {
                page.texture.upload()
                page.dirty = false
            }
        }
    }

    private fun newPage(): AtlasPage {
        val page = AtlasPage(pages.size)
        pages.add(page)
        return page
    }

    private inner class LoadedFont(font: Font) {
        // the buffer must stay strongly referenced for as long as the STBTTFontinfo lives
        val buffer: ByteBuffer
        val info: STBTTFontinfo = STBTTFontinfo.malloc()
        val ascent: Int
        val descent: Int

        private val glyphIndexCache = HashMap<Int, Int>()
        private val advanceCache = HashMap<Int, Int>()
        private val kernCache = HashMap<Long, Int>()

        init {
            val data = font.buffer()
            buffer = if (data.remaining() > 0) data else UIRenderer.defaultFont.buffer()
            require(stbtt_InitFont(info, buffer)) { "Failed to load font ${font.name}" }
            MemoryStack.stackPush().use { stack ->
                val a = stack.mallocInt(1)
                val d = stack.mallocInt(1)
                val l = stack.mallocInt(1)
                stbtt_GetFontVMetrics(info, a, d, l)
                ascent = a[0]
                descent = d[0]
            }
        }

        fun scaleFor(size: Float): Float = stbtt_ScaleForPixelHeight(info, size)

        fun glyphIndex(cp: Int): Int = glyphIndexCache.getOrPut(cp) { stbtt_FindGlyphIndex(info, cp) }

        fun advance(glyph: Int): Int = advanceCache.getOrPut(glyph) {
            MemoryStack.stackPush().use { stack ->
                val adv = stack.mallocInt(1)
                val lsb = stack.mallocInt(1)
                stbtt_GetGlyphHMetrics(info, glyph, adv, lsb)
                adv[0]
            }
        }

        fun kern(g1: Int, g2: Int): Int = kernCache.getOrPut((g1.toLong() shl 32) or g2.toLong()) {
            stbtt_GetGlyphKernAdvance(info, g1, g2)
        }
    }

    private inner class AtlasPage(index: Int) {
        val image = NativeImage(NativeImage.Format.RGBA, PAGE_SIZE, PAGE_SIZE, true)
        val texture = DynamicTexture({ "quoi! ui font atlas $index" }, image)
        val packer = ShelfPacker(PAGE_SIZE, PAGE_SIZE)
        var dirty = false
    }

    private class GlyphSprite(
        val page: AtlasPage,
        val u0: Float, val v0: Float, val u1: Float, val v1: Float,
        val w: Int, val h: Int, val xoff: Int, val yoff: Int,
    )

    private inner class SizedFont(val loaded: LoadedFont, val bucket: Int) {
        val bitmapScale = loaded.scaleFor(bucket / 10f)
        private val sprites = HashMap<Int, GlyphSprite?>()

        fun sprite(glyph: Int): GlyphSprite? = sprites.getOrPut(glyph) { rasterize(glyph) }

        private fun rasterize(glyph: Int): GlyphSprite? {
            MemoryStack.stackPush().use { stack ->
                val w = stack.mallocInt(1)
                val h = stack.mallocInt(1)
                val xoff = stack.mallocInt(1)
                val yoff = stack.mallocInt(1)
                val bitmap = stbtt_GetGlyphBitmap(loaded.info, bitmapScale, bitmapScale, glyph, w, h, xoff, yoff)
                    ?: return null
                try {
                    val gw = w[0]
                    val gh = h[0]
                    if (gw <= 0 || gh <= 0) return null

                    var page = pages.lastOrNull() ?: newPage()
                    var spot = page.packer.place(gw + PADDING * 2, gh + PADDING * 2)
                    if (spot == null) {
                        page = newPage()
                        spot = page.packer.place(gw + PADDING * 2, gh + PADDING * 2)
                            ?: return null // glyph larger than a page; skip rendering it
                    }
                    val px = spot[0] + PADDING
                    val py = spot[1] + PADDING

                    for (gy in 0 until gh) {
                        for (gx in 0 until gw) {
                            val coverage = bitmap.get(gy * gw + gx).toInt() and 0xFF
                            // white with coverage alpha; channel order irrelevant for white
                            page.image.setPixel(px + gx, py + gy, (coverage shl 24) or 0xFFFFFF)
                        }
                    }
                    page.dirty = true

                    return GlyphSprite(
                        page,
                        px.toFloat() / PAGE_SIZE,
                        py.toFloat() / PAGE_SIZE,
                        (px + gw).toFloat() / PAGE_SIZE,
                        (py + gh).toFloat() / PAGE_SIZE,
                        gw, gh, xoff[0], yoff[0],
                    )
                } finally {
                    stbtt_FreeBitmap(bitmap)
                }
            }
        }
    }
}
