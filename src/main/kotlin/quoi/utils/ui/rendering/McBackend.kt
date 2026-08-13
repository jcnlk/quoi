package quoi.utils.ui.rendering

import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.vertex.VertexConsumer
import it.unimi.dsi.fastutil.floats.FloatArrayList
import it.unimi.dsi.fastutil.ints.IntArrayList
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.render.TextureSetup
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.state.gui.GuiElementRenderState
import net.minecraft.client.renderer.texture.DynamicTexture
import org.joml.Matrix3x2f
import org.joml.Matrix3x2fc
import org.joml.Vector2f
import quoi.QuoiMod.mc
import quoi.mixins.accessors.GuiGraphicsExtractorAccessor
import quoi.utils.ui.data.Gradient
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sqrt

/**
 * Minecraft-native [RendererBackend]: tessellates all primitives CPU-side ([UIGeometry])
 * and submits them as custom [GuiElementRenderState]s through the vanilla 26.2 GUI
 * extraction pipeline (`RenderPipelines.GUI` / `GUI_TEXTURED`), which renders identically
 * on the OpenGL and Vulkan RenderDevice backends. No direct GL/VK calls.
 *
 * Coordinates are physical window pixels; a base pose scales them into gui space.
 * Transform/alpha state follows NanoVG semantics (saved by [push], restored by [pop]);
 * the scissor stack is explicit ([pushScissor]/[popScissor]) and applied in the current
 * transform space as an axis-aligned bounds approximation, like `nvgScissor`.
 *
 * Consecutive primitives sharing pipeline + texture + scissor are merged into a single
 * render state, preserving painter's order within the batch; overlapping batches are
 * ordered by the vanilla GuiRenderState bounds-stacking.
 */
class McBackend : RendererBackend {

    val fonts = McFonts()
    val images = McImages()

    private var ctx: GuiGraphicsExtractor? = null
    private var basePose = Matrix3x2f()
    private var invGuiScale = 1f

    private class DrawState(val pose: Matrix3x2f = Matrix3x2f(), var alpha: Float = 1f) {
        fun set(other: DrawState) {
            pose.set(other.pose)
            alpha = other.alpha
        }
    }

    private val stateStack = ArrayList<DrawState>()
    private var state = DrawState()

    /** window-space scissor rects as (x, y, maxX, maxY), already intersected. */
    private class ScissorEntry(val rect: FloatArray, val screenRect: ScreenRectangle?)

    private val scissorStack = ArrayList<ScissorEntry>()

    // pending batch
    private val pendingVerts = FloatArrayList() // x, y, u, v per vertex
    private val pendingCols = IntArrayList()
    private var pendingPipeline: RenderPipeline? = null
    private var pendingTexture: DynamicTexture? = null
    private var pendingScissor: ScissorEntry? = null
    private var pendingMinX = Float.MAX_VALUE
    private var pendingMinY = Float.MAX_VALUE
    private var pendingMaxX = -Float.MAX_VALUE
    private var pendingMaxY = -Float.MAX_VALUE

    private val tmpVec = Vector2f()

    override fun frame(ctx: GuiGraphicsExtractor, content: () -> Unit) {
        check(this.ctx == null) { "[McBackend] frame() reentered" }
        this.ctx = ctx
        invGuiScale = 1f / max(1, mc.window.guiScale)
        basePose.set(ctx.pose()).scale(invGuiScale, invGuiScale)

        stateStack.clear()
        state = DrawState()
        scissorStack.clear()
        resetBatch()

        try {
            content()
            flush()
            fonts.flushUploads()
        } finally {
            this.ctx = null
        }
    }

    override fun push() {
        val saved = DrawState()
        saved.set(state)
        stateStack.add(saved)
    }

    override fun pop() {
        val saved = stateStack.removeLastOrNull() ?: return
        state.set(saved)
    }

    override fun translate(x: Float, y: Float) {
        state.pose.translate(x, y)
    }

    override fun scale(x: Float, y: Float) {
        state.pose.scale(x, y)
    }

    override fun rotate(radians: Float) {
        state.pose.rotate(radians)
    }

    override fun globalAlpha(amount: Float) {
        state.alpha = amount.coerceIn(0f, 1f)
    }

    override fun pushScissor(x: Float, y: Float, w: Float, h: Float) {
        // like nvgScissor: rect specified in current transform space, kept as window-space AABB
        val rect = transformedAabb(x, y, x + w, y + h)
        val intersected = scissorStack.lastOrNull()?.rect?.let { prev ->
            UIGeometry.intersect(prev[0], prev[1], prev[2], prev[3], rect[0], rect[1], rect[2], rect[3])
        } ?: rect
        scissorStack.add(ScissorEntry(intersected, toScreenRect(intersected)))
    }

    override fun popScissor() {
        scissorStack.removeLastOrNull()
    }

    override fun line(x1: Float, y1: Float, x2: Float, y2: Float, thickness: Float, color: Int) {
        if (thickness > 0f) {
            val inner = UIGeometry.lineQuad(x1, y1, x2, y2, (thickness - AA_FRINGE).coerceAtLeast(0f))
            val outer = UIGeometry.lineQuad(x1, y1, x2, y2, thickness + AA_FRINGE)
            emitQuads(inner, color)
            emitQuadEdgeFade(inner, outer, color)
        }
    }

    override fun rect(x: Float, y: Float, w: Float, h: Float, color: Int, tl: Float, bl: Float, br: Float, tr: Float) {
        // the historical renderer extended fills by half a pixel vertically
        val outline = UIGeometry.dedupeClosedOutline(
            UIGeometry.roundedRectOutline(x, y, w, h + 0.5f, tl, bl, br, tr)
        )
        // Axis-aligned sharp rectangles already land exactly on the pixel grid. Keep
        // them exactly inside their element bounds; scissor conversion rounds outwards
        // separately, so no geometry overdraw is needed to avoid clipped edge pixels.
        if (tl <= 0f && bl <= 0f && br <= 0f && tr <= 0f) {
            emitQuads(UIGeometry.fanToQuads(outline), color)
            return
        }
        val inner = roundedRectContour(x, y, w, h + 0.5f, tl, bl, br, tr, -AA_FRINGE * 0.5f)
        val outer = roundedRectContour(x, y, w, h + 0.5f, tl, bl, br, tr, AA_FRINGE * 0.5f)
        emitQuads(UIGeometry.fanToQuads(inner), color)
        emitBandFade(inner, outer, color, color and 0xFFFFFF)
    }

    override fun hollowRect(x: Float, y: Float, w: Float, h: Float, thickness: Float, color: Int, tl: Float, bl: Float, br: Float, tr: Float) {
        val half = thickness * 0.5f
        val coreHalf = (half - AA_FRINGE * 0.5f).coerceAtLeast(0f)
        val transparent = color and 0xFFFFFF
        val outerCore = roundedRectContour(x, y, w, h, tl, bl, br, tr, coreHalf)
        val innerCore = roundedRectContour(x, y, w, h, tl, bl, br, tr, -coreHalf)
        emitQuads(UIGeometry.bandToQuads(innerCore, outerCore), color)
        emitBandFade(
            outerCore,
            roundedRectContour(x, y, w, h, tl, bl, br, tr, half + AA_FRINGE * 0.5f),
            color, transparent,
        )
        // The inner fringe runs towards the shape interior. Swap both contours and
        // colours so bandToQuads keeps its outward winding and survives GUI backface culling.
        emitBandFade(
            roundedRectContour(x, y, w, h, tl, bl, br, tr, -half - AA_FRINGE * 0.5f),
            innerCore,
            transparent, color,
        )
    }

    override fun gradientRect(x: Float, y: Float, w: Float, h: Float, color1: Int, color2: Int, direction: Gradient, tl: Float, bl: Float, br: Float, tr: Float) {
        val inner = roundedRectContour(x, y, w, h, tl, bl, br, tr, -AA_FRINGE * 0.5f)
        val outer = roundedRectContour(x, y, w, h, tl, bl, br, tr, AA_FRINGE * 0.5f)
        val quads = UIGeometry.fanToQuads(inner)
        // vertex colours sampled from the linear gradient at each vertex: exact for a
        // linear function under barycentric interpolation
        emitQuads(quads) { vx, vy ->
            val t = when (direction) {
                Gradient.LeftToRight -> if (w != 0f) ((vx - x) / w) else 0f
                Gradient.TopToBottom -> if (h != 0f) ((vy - y) / h) else 0f
            }.coerceIn(0f, 1f)
            lerpColor(color1, color2, t)
        }
        emitGradientFringe(inner, outer, x, y, w, h, color1, color2, direction)
    }

    override fun dropShadow(x: Float, y: Float, width: Float, height: Float, blur: Float, spread: Float, radius: Float, colour: Int) {
        // NanoVG box gradient: linear falloff over [spread - blur/2, spread + blur/2]
        // measured from the base rectangle, solid inside that, hollow under the rectangle.
        // Offset rounded-rect outlines are exact iso-distance curves, so banding between
        // them reproduces the gradient; only the falloff stays linear instead of feathered.
        val outerExtent = spread + blur * 0.5f
        if (outerExtent <= 0f) return
        val solidExtent = (spread - blur * 0.5f).coerceAtLeast(0f)

        val outerSize = 2 * outerExtent
        val segments = IntArray(4) {
            UIGeometry.segmentsFor(radius + outerExtent, width + outerSize, height + outerSize)
        }

        fun ring(amount: Float): FloatArray = UIGeometry.roundedRectOutlineFixedSegments(
            x - amount, y - amount, width + 2 * amount, height + 2 * amount,
            radius + amount, radius + amount, radius + amount, radius + amount,
            segments,
        )

        val transparent = colour and 0xFFFFFF

        if (blur <= 0f) {
            // hard-edged shadow: solid ring out to the spread box
            if (spread > 0f) emitQuads(UIGeometry.bandToQuads(ring(0f), ring(spread)), colour)
            return
        }

        if (solidExtent > 0f) {
            emitQuads(UIGeometry.bandToQuads(ring(0f), ring(solidExtent)), colour)
            emitBandFade(ring(solidExtent), ring(outerExtent), colour, transparent)
        } else {
            // solid ring collapsed: the rectangle edge sits inside the falloff already
            val edgeAlpha = ((spread + blur * 0.5f) / blur).coerceIn(0f, 1f)
            val edgeColour = (((colour ushr 24) * edgeAlpha).toInt().coerceIn(0, 255) shl 24) or transparent
            emitBandFade(ring(0f), ring(outerExtent), edgeColour, transparent)
        }
    }

    override fun circle(x: Float, y: Float, radius: Float, color: Int) {
        val segments = max(8, UIGeometry.arcSegments(radius + AA_FRINGE * 0.5f) * 4)
        val inner = UIGeometry.circleOutlineFixedSegments(
            x, y, (radius - AA_FRINGE * 0.5f).coerceAtLeast(0f), segments
        )
        val outer = UIGeometry.circleOutlineFixedSegments(x, y, radius + AA_FRINGE * 0.5f, segments)
        emitQuads(UIGeometry.fanToQuads(inner), color)
        emitBandFade(inner, outer, color, color and 0xFFFFFF)
    }

    override fun text(text: String, x: Float, y: Float, size: Float, color: Int, font: Font) {
        if (text.isEmpty() || size <= 0f) return
        fonts.layout(font, text, x, y + 0.5f, size, textRasterScale()) { glyph ->
            emitTexturedQuad(
                glyph.texture,
                glyph.x0, glyph.y0, glyph.x1, glyph.y1,
                glyph.u0, glyph.v0, glyph.u1, glyph.v1,
                color,
            )
        }
    }

    /** Mirrors NanoVG's quantized average transform scale times its device pixel ratio. */
    private fun textRasterScale(): Float {
        val sx = sqrt(state.pose.m00() * state.pose.m00() + state.pose.m10() * state.pose.m10())
        val sy = sqrt(state.pose.m01() * state.pose.m01() + state.pose.m11() * state.pose.m11())
        val transformScale = (round(((sx + sy) * 0.5f) * 100f) / 100f).coerceAtMost(4f)
        return (transformScale * devicePixelRatio()).coerceAtLeast(0.01f)
    }

    private fun devicePixelRatio(): Float {
        val screenWidth = mc.window.screenWidth
        return if (screenWidth == 0) 1f else mc.window.width.toFloat() / screenWidth
    }

    /** Exact concentric rounded-rect contour; avoids miter noise on tiny pill controls. */
    private fun roundedRectContour(
        x: Float, y: Float, w: Float, h: Float,
        tl: Float, bl: Float, br: Float, tr: Float,
        amount: Float,
    ): FloatArray {
        val segments = intArrayOf(
            UIGeometry.segmentsFor(tl, w, h),
            UIGeometry.segmentsFor(tr, w, h),
            UIGeometry.segmentsFor(br, w, h),
            UIGeometry.segmentsFor(bl, w, h),
        )
        return UIGeometry.dedupeClosedOutline(
            UIGeometry.roundedRectOutlineFixedSegments(
                x - amount, y - amount, w + amount * 2f, h + amount * 2f,
                tl + amount, bl + amount, br + amount, tr + amount,
                segments,
            )
        )
    }

    override fun textWidth(text: String, size: Float, font: Font): Float =
        fonts.width(font, text, size)

    override fun image(image: Image, x: Float, y: Float, w: Float, h: Float, tl: Float, bl: Float, br: Float, tr: Float, color: Int?) {
        val entry = images.entry(image)
        val tint = color ?: -1

        if (tl == 0f && bl == 0f && br == 0f && tr == 0f) {
            emitTexturedQuad(entry.texture, x, y, x + w, y + h, 0f, 0f, 1f, 1f, tint)
            return
        }

        val outline = UIGeometry.roundedRectOutline(x, y, w, h, tl, bl, br, tr)
        val quads = UIGeometry.fanToQuads(outline)
        beginBatch(RenderPipelines.GUI_TEXTURED, entry.texture, scissorStack.lastOrNull())
        var i = 0
        while (i < quads.size) {
            val vx = quads[i]
            val vy = quads[i + 1]
            val u = if (w != 0f) (vx - x) / w else 0f
            val v = if (h != 0f) (vy - y) / h else 0f
            addVertex(vx, vy, u, v, multiplyAlpha(tint))
            i += 2
        }
    }

    override fun registerImage(image: Image) = images.register(image)

    override fun deleteImage(image: Image): Boolean = images.delete(image)

    // ---------------------------------------------------------------- batching internals

    private fun emitQuads(quads: FloatArray, color: Int) {
        if (quads.isEmpty()) return
        beginBatch(RenderPipelines.GUI, null, scissorStack.lastOrNull())
        val col = multiplyAlpha(color)
        var i = 0
        while (i < quads.size) {
            addVertex(quads[i], quads[i + 1], 0f, 0f, col)
            i += 2
        }
    }

    private inline fun emitQuads(quads: FloatArray, color: (Float, Float) -> Int) {
        if (quads.isEmpty()) return
        beginBatch(RenderPipelines.GUI, null, scissorStack.lastOrNull())
        var i = 0
        while (i < quads.size) {
            val vx = quads[i]
            val vy = quads[i + 1]
            addVertex(vx, vy, 0f, 0f, multiplyAlpha(color(vx, vy)))
            i += 2
        }
    }

    /** Band between two outlines with the inner edge at [innerColor] and outer at [outerColor]. */
    private fun emitBandFade(inner: FloatArray, outer: FloatArray, innerColor: Int, outerColor: Int) {
        val quads = UIGeometry.bandToQuads(inner, outer)
        if (quads.isEmpty()) return
        beginBatch(RenderPipelines.GUI, null, scissorStack.lastOrNull())
        val ic = multiplyAlpha(innerColor)
        val oc = multiplyAlpha(outerColor)
        // bandToQuads vertex order: inner[i], inner[j], outer[j], outer[i]
        var i = 0
        while (i < quads.size) {
            addVertex(quads[i], quads[i + 1], 0f, 0f, ic)
            addVertex(quads[i + 2], quads[i + 3], 0f, 0f, ic)
            addVertex(quads[i + 4], quads[i + 5], 0f, 0f, oc)
            addVertex(quads[i + 6], quads[i + 7], 0f, 0f, oc)
            i += 8
        }
    }

    /** AA fringe for a line quad; corresponding corners are paired directly. */
    private fun emitQuadEdgeFade(inner: FloatArray, outer: FloatArray, color: Int) {
        if (inner.size != 8 || outer.size != 8) return
        emitBandFade(inner, outer, color, color and 0xFFFFFF)
    }

    private fun emitGradientFringe(
        inner: FloatArray, outer: FloatArray,
        x: Float, y: Float, w: Float, h: Float,
        color1: Int, color2: Int, direction: Gradient,
    ) {
        val quads = UIGeometry.bandToQuads(inner, outer)
        if (quads.isEmpty()) return
        beginBatch(RenderPipelines.GUI, null, scissorStack.lastOrNull())
        var i = 0
        while (i < quads.size) {
            for (vertex in 0 until 4) {
                val vx = quads[i + vertex * 2]
                val vy = quads[i + vertex * 2 + 1]
                val t = when (direction) {
                    Gradient.LeftToRight -> if (w != 0f) ((vx - x) / w) else 0f
                    Gradient.TopToBottom -> if (h != 0f) ((vy - y) / h) else 0f
                }.coerceIn(0f, 1f)
                var col = lerpColor(color1, color2, t)
                if (vertex >= 2) col = col and 0xFFFFFF
                addVertex(vx, vy, 0f, 0f, multiplyAlpha(col))
            }
            i += 8
        }
    }

    private fun emitTexturedQuad(
        texture: DynamicTexture,
        x0: Float, y0: Float, x1: Float, y1: Float,
        u0: Float, v0: Float, u1: Float, v1: Float,
        color: Int,
    ) {
        beginBatch(RenderPipelines.GUI_TEXTURED, texture, scissorStack.lastOrNull())
        val col = multiplyAlpha(color)
        addVertex(x0, y0, u0, v0, col)
        addVertex(x0, y1, u0, v1, col)
        addVertex(x1, y1, u1, v1, col)
        addVertex(x1, y0, u1, v0, col)
    }

    private fun beginBatch(pipeline: RenderPipeline, texture: DynamicTexture?, scissor: ScissorEntry?) {
        if (pipeline !== pendingPipeline || texture !== pendingTexture || scissor !== pendingScissor) {
            flush()
            pendingPipeline = pipeline
            pendingTexture = texture
            pendingScissor = scissor
        }
    }

    private fun addVertex(x: Float, y: Float, u: Float, v: Float, color: Int) {
        // pre-transform by the local pose so transform changes don't break batches
        tmpVec.set(x, y)
        state.pose.transformPosition(tmpVec)
        pendingVerts.add(tmpVec.x)
        pendingVerts.add(tmpVec.y)
        pendingVerts.add(u)
        pendingVerts.add(v)
        pendingCols.add(color)
        if (tmpVec.x < pendingMinX) pendingMinX = tmpVec.x
        if (tmpVec.y < pendingMinY) pendingMinY = tmpVec.y
        if (tmpVec.x > pendingMaxX) pendingMaxX = tmpVec.x
        if (tmpVec.y > pendingMaxY) pendingMaxY = tmpVec.y
    }

    private fun flush() {
        if (pendingVerts.isEmpty) {
            resetBatch()
            return
        }
        val ctx = this.ctx ?: run { resetBatch(); return }

        val scissor = pendingScissor
        var bounds = toScreenRectOuter(pendingMinX, pendingMinY, pendingMaxX, pendingMaxY)
        if (scissor?.screenRect != null && bounds != null) {
            bounds = scissor.screenRect.intersection(bounds)
        }

        if (bounds != null && bounds.width > 0 && bounds.height > 0) {
            val textureSetup = pendingTexture?.let {
                TextureSetup.singleTexture(it.textureView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR))
            } ?: TextureSetup.noTexture()

            (ctx as GuiGraphicsExtractorAccessor).`quoi$getGuiRenderState`().addGuiElement(
                UIDrawState(
                    pendingPipeline ?: RenderPipelines.GUI,
                    textureSetup,
                    Matrix3x2f(basePose),
                    pendingVerts.toFloatArray(),
                    pendingCols.toIntArray(),
                    pendingTexture != null,
                    scissor?.screenRect,
                    bounds,
                )
            )
        }
        resetBatch()
    }

    private fun resetBatch() {
        pendingVerts.clear()
        pendingCols.clear()
        pendingPipeline = null
        pendingTexture = null
        pendingScissor = null
        pendingMinX = Float.MAX_VALUE
        pendingMinY = Float.MAX_VALUE
        pendingMaxX = -Float.MAX_VALUE
        pendingMaxY = -Float.MAX_VALUE
    }

    // ---------------------------------------------------------------- helpers

    private fun multiplyAlpha(color: Int): Int {
        val alpha = state.alpha
        if (alpha >= 1f) return color
        val a = ((color ushr 24) * alpha).toInt().coerceIn(0, 255)
        return (a shl 24) or (color and 0xFFFFFF)
    }

    private fun lerpColor(c1: Int, c2: Int, t: Float): Int {
        val a = ((c1 ushr 24) + (((c2 ushr 24) - (c1 ushr 24)) * t)).toInt().coerceIn(0, 255)
        val r = (((c1 shr 16) and 0xFF) + ((((c2 shr 16) and 0xFF) - ((c1 shr 16) and 0xFF)) * t)).toInt().coerceIn(0, 255)
        val g = (((c1 shr 8) and 0xFF) + ((((c2 shr 8) and 0xFF) - ((c1 shr 8) and 0xFF)) * t)).toInt().coerceIn(0, 255)
        val b = ((c1 and 0xFF) + (((c2 and 0xFF) - (c1 and 0xFF)) * t)).toInt().coerceIn(0, 255)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    /** AABB of (x0,y0)-(x1,y1) transformed by the current local pose, in window pixels. */
    private fun transformedAabb(x0: Float, y0: Float, x1: Float, y1: Float): FloatArray {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        val xs = floatArrayOf(x0, x1, x0, x1)
        val ys = floatArrayOf(y0, y0, y1, y1)
        for (i in 0 until 4) {
            tmpVec.set(xs[i], ys[i])
            state.pose.transformPosition(tmpVec)
            minX = min(minX, tmpVec.x)
            minY = min(minY, tmpVec.y)
            maxX = max(maxX, tmpVec.x)
            maxY = max(maxY, tmpVec.y)
        }
        return floatArrayOf(minX, minY, maxX, maxY)
    }

    /**
     * Window-px clip → GUI-pixel ScreenRectangle. Round outwards: nearest rounding can
     * move a clip edge inward by up to half a GUI pixel (1.5 physical px at scale 3),
     * visibly shaving category edges and rounded top/bottom caps. Geometry still limits
     * actual drawing at the precise window-pixel boundary.
     */
    private fun toScreenRect(rect: FloatArray): ScreenRectangle? {
        // GuiRenderer only clamps the right and bottom edges before forwarding the
        // scissor to RenderPass. Negative or completely off-screen rectangles therefore
        // fail RenderPass's strict bounds validation instead of merely clipping content.
        val windowWidth = mc.window.width.toFloat()
        val windowHeight = mc.window.height.toFloat()
        val clippedLeft = rect[0].coerceIn(0f, windowWidth)
        val clippedTop = rect[1].coerceIn(0f, windowHeight)
        val clippedRight = rect[2].coerceIn(0f, windowWidth)
        val clippedBottom = rect[3].coerceIn(0f, windowHeight)
        if (clippedRight <= clippedLeft || clippedBottom <= clippedTop) return EMPTY_SCISSOR

        val left = floor(clippedLeft * invGuiScale).toInt()
        val top = floor(clippedTop * invGuiScale).toInt()
        val right = ceil(clippedRight * invGuiScale).toInt()
        val bottom = ceil(clippedBottom * invGuiScale).toInt()
        if (right <= left || bottom <= top) return EMPTY_SCISSOR
        return ScreenRectangle(left, top, right - left, bottom - top)
    }

    /** Window-px AABB → gui-space ScreenRectangle, rounded outwards (for bounds). */
    private fun toScreenRectOuter(minX: Float, minY: Float, maxX: Float, maxY: Float): ScreenRectangle? {
        if (maxX <= minX || maxY <= minY) return null
        val left = floor(minX * invGuiScale).toInt()
        val top = floor(minY * invGuiScale).toInt()
        val right = ceil(maxX * invGuiScale).toInt()
        val bottom = ceil(maxY * invGuiScale).toInt()
        if (right <= left || bottom <= top) return null
        return ScreenRectangle(left, top, right - left, bottom - top)
    }

    private class UIDrawState(
        private val pipeline: RenderPipeline,
        private val textureSetup: TextureSetup,
        private val pose: Matrix3x2f,
        private val verts: FloatArray,
        private val cols: IntArray,
        private val textured: Boolean,
        private val scissor: ScreenRectangle?,
        private val boundsRect: ScreenRectangle,
    ) : GuiElementRenderState {

        override fun buildVertices(consumer: VertexConsumer) {
            var i = 0
            var c = 0
            while (i < verts.size) {
                val vertex = consumer.addVertexWith2DPose(pose, verts[i], verts[i + 1])
                if (textured) vertex.setUv(verts[i + 2], verts[i + 3])
                vertex.setColor(cols[c++])
                i += 4
            }
        }

        override fun pipeline(): RenderPipeline = pipeline

        override fun textureSetup(): TextureSetup = textureSetup

        override fun scissorArea(): ScreenRectangle? = scissor

        override fun bounds(): ScreenRectangle = boundsRect
    }

    private companion object {
        const val AA_FRINGE = 1f
        /** Fully clipped: 0-sized scissors still need an object so content stays hidden. */
        val EMPTY_SCISSOR = ScreenRectangle(0, 0, 0, 0)
    }
}
