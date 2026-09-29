package quoi.utils.ui.rendering

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure 2D tessellation for the Minecraft-native UI backend. Produces flat `FloatArray`
 * polygons/strips (x,y pairs) that the backend turns into GPU quads. No Minecraft or
 * LWJGL dependencies so everything here is unit testable.
 */
object UIGeometry {

    /**
     * Segments used to approximate a 90° arc of the given pixel radius, using NanoVG's
     * adaptive curve division with a 0.25px tessellation tolerance:
     * ceil((π/2) / acos(r / (r + tol))). Small UI controls (toggles, pills) get visibly
     * smoother arcs than a linear-in-radius heuristic.
     */
    fun arcSegments(radius: Float): Int {
        if (radius <= 0f) return 2
        val da = kotlin.math.acos((radius / (radius + 0.25f)).toDouble())
        return ceil((PI * 0.5) / da).toInt().coerceIn(2, 24)
    }

    /**
     * Clamps per-corner radii the same way NanoVG does: each radius is limited to half
     * of the rectangle's smaller dimension.
     */
    fun clampRadius(radius: Float, w: Float, h: Float): Float =
        min(max(0f, radius), min(abs(w), abs(h)) * 0.5f)

    /**
     * Outline of a rounded rectangle as x,y pairs in clockwise order starting from the
     * left edge of the top-left corner. Sharp rectangles produce 4 points.
     * Corner order of the parameters matches the renderer API: tl, bl, br, tr.
     */
    fun roundedRectOutline(
        x: Float, y: Float, w: Float, h: Float,
        tl: Float, bl: Float, br: Float, tr: Float,
    ): FloatArray {
        val rtl = clampRadius(tl, w, h)
        val rbl = clampRadius(bl, w, h)
        val rbr = clampRadius(br, w, h)
        val rtr = clampRadius(tr, w, h)

        if (rtl == 0f && rbl == 0f && rbr == 0f && rtr == 0f) {
            return floatArrayOf(x, y, x + w, y, x + w, y + h, x, y + h)
        }

        val points = ArrayList<Float>(64)

        // each corner: arc from start angle going clockwise 90°
        corner(points, x + rtl, y + rtl, rtl, 180f) // top-left: from left edge to top edge
        corner(points, x + w - rtr, y + rtr, rtr, 270f) // top-right
        corner(points, x + w - rbr, y + h - rbr, rbr, 0f) // bottom-right
        corner(points, x + rbl, y + h - rbl, rbl, 90f) // bottom-left

        return points.toFloatArray()
    }

    private fun corner(out: MutableList<Float>, cx: Float, cy: Float, r: Float, startDeg: Float) {
        if (r <= 0f) {
            // r == 0 means cx,cy is the rectangle corner itself
            out.add(cx)
            out.add(cy)
            return
        }
        val segments = arcSegments(r)
        for (i in 0..segments) {
            val angle = Math.toRadians(startDeg + 90.0 * i / segments)
            out.add(cx + (r * cos(angle)).toFloat())
            out.add(cy + (r * sin(angle)).toFloat())
        }
    }

    /** Outline of a full circle as x,y pairs. */
    fun circleOutline(cx: Float, cy: Float, radius: Float): FloatArray {
        val segments = max(8, arcSegments(radius) * 4)
        return circleOutlineFixedSegments(cx, cy, radius, segments)
    }

    /** Circle outline with a caller-supplied segment count for concentric AA bands. */
    fun circleOutlineFixedSegments(cx: Float, cy: Float, radius: Float, segments: Int): FloatArray {
        val segments = max(8, segments)
        val points = FloatArray(segments * 2)
        for (i in 0 until segments) {
            val angle = 2.0 * PI * i / segments
            points[i * 2] = cx + (radius * cos(angle)).toFloat()
            points[i * 2 + 1] = cy + (radius * sin(angle)).toFloat()
        }
        return points
    }

    /**
     * Converts a convex outline (x,y pairs, clockwise in y-down screen space) into a fan
     * of quads (4 vertices each, packed x,y per vertex => 8 floats per quad) anchored at
     * the first outline point. Quads are emitted with the same winding as vanilla GUI
     * quads (negative shoelace in screen space) so they survive back-face culling.
     */
    fun fanToQuads(outline: FloatArray): FloatArray {
        val pointCount = outline.size / 2
        if (pointCount < 3) return FloatArray(0)
        val ax = outline[0]
        val ay = outline[1]
        val quadCount = (pointCount - 2 + 1) / 2 // two triangles per quad
        val out = FloatArray(quadCount * 8)
        var o = 0
        var i = 1
        while (i < pointCount - 1) {
            val bx = outline[i * 2]
            val by = outline[i * 2 + 1]
            val cx = outline[(i + 1) * 2]
            val cy = outline[(i + 1) * 2 + 1]
            val hasSecond = i + 2 < pointCount
            val dx = if (hasSecond) outline[(i + 2) * 2] else cx
            val dy = if (hasSecond) outline[(i + 2) * 2 + 1] else cy
            // (a, d, c, b): reversed traversal flips the outline's positive shoelace
            out[o++] = ax; out[o++] = ay
            out[o++] = dx; out[o++] = dy
            out[o++] = cx; out[o++] = cy
            out[o++] = bx; out[o++] = by
            i += 2
        }
        return out
    }

    /**
     * Converts an outline into a closed stroke band of quads with the given [thickness],
     * centred on the outline (like NanoVG strokes). Returns packed quads (8 floats each).
     *
     * Coincident consecutive points (which pill-shaped outlines produce where adjacent
     * arcs meet) are removed first: a zero-length edge has no normal, and feeding it to
     * the miter maths produced huge spikes at the seam.
     */
    fun strokeToQuads(outline: FloatArray, thickness: Float): FloatArray {
        val outline = dedupeClosedOutline(outline)
        val outer = offsetClosedOutline(outline, thickness * 0.5f)
        val inner = offsetClosedOutline(outline, thickness * -0.5f)
        return bandToQuads(inner, outer)
    }

    /**
     * Offsets a closed clockwise outline along its averaged edge normals. Positive values
     * expand outwards, negative values contract inwards. Point count is preserved so the
     * result can be paired with the source outline using [bandToQuads].
     */
    fun offsetClosedOutline(source: FloatArray, amount: Float): FloatArray {
        val outline = dedupeClosedOutline(source)
        val pointCount = outline.size / 2
        if (pointCount < 2) return FloatArray(0)
        val out = FloatArray(outline.size)
        for (i in 0 until pointCount) {
            val px = outline[((i - 1 + pointCount) % pointCount) * 2]
            val py = outline[((i - 1 + pointCount) % pointCount) * 2 + 1]
            val cx = outline[i * 2]
            val cy = outline[i * 2 + 1]
            val nx2 = outline[((i + 1) % pointCount) * 2]
            val ny2 = outline[((i + 1) % pointCount) * 2 + 1]

            // edge normals (clockwise polygon => outward normal is (-dy, dx) negated)
            var n1x = -(cy - py); var n1y = cx - px
            var n2x = -(ny2 - cy); var n2y = nx2 - cx
            val l1 = sqrt(n1x * n1x + n1y * n1y)
            val l2 = sqrt(n2x * n2x + n2y * n2y)
            if (l1 > 1e-6f) { n1x /= l1; n1y /= l1 }
            if (l2 > 1e-6f) { n2x /= l2; n2y /= l2 }

            var mx: Float
            var my: Float
            var scale = 1f
            if (l1 <= 1e-6f || l2 <= 1e-6f) {
                // degenerate neighbour edge: fall back to the valid normal, no miter
                mx = if (l2 > 1e-6f) n2x else n1x
                my = if (l2 > 1e-6f) n2y else n1y
            } else {
                mx = n1x + n2x
                my = n1y + n2y
                val ml = sqrt(mx * mx + my * my)
                if (ml > 1e-6f) {
                    mx /= ml; my /= ml
                    // miter scale, clamped to avoid spikes on very sharp joins
                    scale = 1f / (mx * n2x + my * n2y).coerceAtLeast(0.5f)
                } else {
                    mx = n2x; my = n2y
                }
            }

            out[i * 2] = cx - mx * amount * scale
            out[i * 2 + 1] = cy - my * amount * scale
        }
        return out
    }

    /** Removes coincident consecutive points from a closed outline, incl. the wraparound. */
    fun dedupeClosedOutline(outline: FloatArray): FloatArray {
        val pointCount = outline.size / 2
        if (pointCount < 2) return outline
        val out = ArrayList<Float>(outline.size)
        for (i in 0 until pointCount) {
            val x = outline[i * 2]
            val y = outline[i * 2 + 1]
            if (out.size >= 2 && abs(out[out.size - 2] - x) < 1e-4f && abs(out[out.size - 1] - y) < 1e-4f) continue
            out.add(x)
            out.add(y)
        }
        if (out.size >= 4 && abs(out[0] - out[out.size - 2]) < 1e-4f && abs(out[1] - out[out.size - 1]) < 1e-4f) {
            out.removeAt(out.size - 1)
            out.removeAt(out.size - 1)
        }
        return out.toFloatArray()
    }

    /** A single quad for a line from (x1,y1) to (x2,y2) with the given [thickness]. */
    fun lineQuad(x1: Float, y1: Float, x2: Float, y2: Float, thickness: Float): FloatArray {
        var dx = x2 - x1
        var dy = y2 - y1
        val len = sqrt(dx * dx + dy * dy)
        if (len < 1e-6f) {
            dx = 1f; dy = 0f
        } else {
            dx /= len; dy /= len
        }
        val nx = -dy * thickness * 0.5f
        val ny = dx * thickness * 0.5f
        return floatArrayOf(
            x1 + nx, y1 + ny,
            x2 + nx, y2 + ny,
            x2 - nx, y2 - ny,
            x1 - nx, y1 - ny,
        )
    }

    /**
     * A band of quads between two outlines with the same point count (used for the
     * drop-shadow falloff ring: inner outline at full colour, outer outline transparent).
     * Output: packed quads, vertex order inner[i], inner[j], outer[j], outer[i].
     */
    fun bandToQuads(innerOutline: FloatArray, outerOutline: FloatArray): FloatArray {
        require(innerOutline.size == outerOutline.size) { "outline sizes must match" }
        val pointCount = innerOutline.size / 2
        if (pointCount < 2) return FloatArray(0)
        val out = FloatArray(pointCount * 8)
        var o = 0
        for (i in 0 until pointCount) {
            val j = (i + 1) % pointCount
            out[o++] = innerOutline[i * 2]; out[o++] = innerOutline[i * 2 + 1]
            out[o++] = innerOutline[j * 2]; out[o++] = innerOutline[j * 2 + 1]
            out[o++] = outerOutline[j * 2]; out[o++] = outerOutline[j * 2 + 1]
            out[o++] = outerOutline[i * 2]; out[o++] = outerOutline[i * 2 + 1]
        }
        return out
    }

    /**
     * Outline of a rounded rectangle uniformly expanded outwards by [amount]
     * (used together with [bandToQuads]). Same point count as the source outline as long
     * as the same corner radii (+amount) and dimensions are used.
     */
    fun expandedRoundedRectOutline(
        x: Float, y: Float, w: Float, h: Float,
        tl: Float, bl: Float, br: Float, tr: Float,
        amount: Float,
    ): FloatArray = roundedRectOutlineFixedSegments(
        x - amount, y - amount, w + 2 * amount, h + 2 * amount,
        tl + amount, bl + amount, br + amount, tr + amount,
        // segment counts must match the inner outline for banding
        intArrayOf(
            segmentsFor(tl, w, h),
            segmentsFor(tr, w, h),
            segmentsFor(br, w, h),
            segmentsFor(bl, w, h),
        )
    )

    fun segmentsFor(radius: Float, w: Float, h: Float): Int {
        val r = clampRadius(radius, w, h)
        return if (r <= 0f) 0 else arcSegments(r)
    }

    /** Like [roundedRectOutline] but with externally fixed per-corner segment counts (tl, tr, br, bl). */
    fun roundedRectOutlineFixedSegments(
        x: Float, y: Float, w: Float, h: Float,
        tl: Float, bl: Float, br: Float, tr: Float,
        segments: IntArray,
    ): FloatArray {
        val rtl = clampRadius(tl, w, h)
        val rbl = clampRadius(bl, w, h)
        val rbr = clampRadius(br, w, h)
        val rtr = clampRadius(tr, w, h)

        val points = ArrayList<Float>(64)
        cornerFixed(points, x + rtl, y + rtl, rtl, 180f, segments[0])
        cornerFixed(points, x + w - rtr, y + rtr, rtr, 270f, segments[1])
        cornerFixed(points, x + w - rbr, y + h - rbr, rbr, 0f, segments[2])
        cornerFixed(points, x + rbl, y + h - rbl, rbl, 90f, segments[3])
        return points.toFloatArray()
    }

    private fun cornerFixed(out: MutableList<Float>, cx: Float, cy: Float, r: Float, startDeg: Float, segments: Int) {
        if (segments <= 0) {
            // sharp corner: emit the corner point once; offset centre back to the corner
            val sx = when (startDeg) {
                180f -> cx - r; 90f -> cx - r; else -> cx + r
            }
            val sy = when (startDeg) {
                180f -> cy - r; 270f -> cy - r; else -> cy + r
            }
            out.add(sx); out.add(sy)
            return
        }
        for (i in 0..segments) {
            val angle = Math.toRadians(startDeg + 90.0 * i / segments)
            out.add(cx + (r * cos(angle)).toFloat())
            out.add(cy + (r * sin(angle)).toFloat())
        }
    }

    /**
     * Intersection of two rectangles given as (x, y, maxX, maxY); used by the scissor stack.
     * Returns FloatArray(x, y, maxX, maxY) with width/height clamped to >= 0.
     */
    fun intersect(ax: Float, ay: Float, aMaxX: Float, aMaxY: Float, bx: Float, by: Float, bMaxX: Float, bMaxY: Float): FloatArray {
        val x = max(ax, bx)
        val y = max(ay, by)
        val mx = max(x, min(aMaxX, bMaxX))
        val my = max(y, min(aMaxY, bMaxY))
        return floatArrayOf(x, y, mx, my)
    }
}
