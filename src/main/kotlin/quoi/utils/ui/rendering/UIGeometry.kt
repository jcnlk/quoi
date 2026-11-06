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

    // arcSegments caps rounded corners at 24. Keep double precision so the
    // radius multiplication rounds exactly as it does with direct sin/cos calls.
    private val cornerDirections = Array(25) { segments ->
        DoubleArray(4 * (segments + 1) * 2).also { directions ->
            if (segments > 0) for (corner in 0..3) for (i in 0..segments) {
                val angle = Math.toRadians(corner * 90.0 + 90.0 * i / segments)
                val offset = (corner * (segments + 1) + i) * 2
                directions[offset] = cos(angle)
                directions[offset + 1] = sin(angle)
            }
        }
    }

    /**
     * Clip both triangles of each GUI quad, interpolating UVs and vertex colours.
     * Reads [vertexCount] vertices from the backing arrays and returns independent arrays.
     */
    fun clipQuads(vertices: FloatArray, colors: IntArray, rect: FloatArray, vertexCount: Int = colors.size): Pair<FloatArray, IntArray> {
        if (rect[2] <= rect[0] || rect[3] <= rect[1]) return FloatArray(0) to IntArray(0)
        var output = FloatArray(0)
        var outputColors = IntArray(0)
        var size = 0
        fun reserve(count: Int) {
            if (size + count > outputColors.size) {
                val capacity = max(size + count, max(vertexCount, outputColors.size * 2))
                output = output.copyOf(capacity * 4)
                outputColors = outputColors.copyOf(capacity)
            }
        }
        fun emit(polygon: ClipPolygon, index: Int) {
            reserve(1)
            polygon.vertices.copyInto(output, size * 4, index * 4, index * 4 + 4)
            outputColors[size++] = polygon.colors[index]
        }
        // A triangle clipped against four half-planes has at most seven vertices.
        // Reuse both buffers for every triangle in this batch.
        var polygonA: ClipPolygon? = null
        var polygonB: ClipPolygon? = null
        for (offset in 0 until vertexCount step 4) {
            var inside = true
            var left = true; var right = true; var above = true; var below = true
            for (i in 0..3) {
                val p = (offset + i) * 4
                val x = vertices[p]; val y = vertices[p + 1]
                inside = inside && x >= rect[0] && x <= rect[2] && y >= rect[1] && y <= rect[3]
                left = left && x < rect[0]; right = right && x > rect[2]
                above = above && y < rect[1]; below = below && y > rect[3]
            }
            if (inside) {
                reserve(4)
                vertices.copyInto(output, size * 4, offset * 4, (offset + 4) * 4)
                colors.copyInto(outputColors, size, offset, offset + 4)
                size += 4
                continue
            }
            if (left || right || above || below) continue

            val first = polygonA ?: ClipPolygon().also { polygonA = it }
            val second = polygonB ?: ClipPolygon().also { polygonB = it }
            for (triangle in 0..1) {
                var polygon = first
                var clipped = second
                polygon.size = 0
                polygon.add(vertices, colors, offset)
                polygon.add(vertices, colors, offset + triangle + 1)
                polygon.add(vertices, colors, offset + triangle + 2)
                for (edge in 0..3) {
                    if (polygon.size == 0) break
                    val axis = edge % 2
                    val boundary = rect[edge]
                    val keepGreater = edge < 2
                    fun coordinate(index: Int) = polygon.vertices[index * 4 + axis]
                    fun inside(index: Int) = if (keepGreater) coordinate(index) >= boundary else coordinate(index) <= boundary
                    clipped.size = 0
                    var previous = polygon.size - 1
                    for (next in 0 until polygon.size) {
                        if (inside(previous) != inside(next)) {
                            val t = (boundary - coordinate(previous)) / (coordinate(next) - coordinate(previous))
                            clipped.addIntersection(polygon, previous, next, t, axis, boundary)
                        }
                        if (inside(next)) clipped.add(polygon.vertices, polygon.colors, next)
                        previous = next
                    }
                    val spare = polygon
                    polygon = clipped
                    clipped = spare
                }
                for (i in 1 until polygon.size - 1) {
                    emit(polygon, 0); emit(polygon, i); emit(polygon, i + 1); emit(polygon, i + 1)
                }
            }
        }
        return (if (size * 4 == output.size) output else output.copyOf(size * 4)) to
            (if (size == outputColors.size) outputColors else outputColors.copyOf(size))
    }

    private class ClipPolygon {
        val vertices = FloatArray(7 * 4)
        val colors = IntArray(7)
        var size = 0

        fun add(source: FloatArray, sourceColors: IntArray, index: Int) {
            source.copyInto(vertices, size * 4, index * 4, index * 4 + 4)
            colors[size++] = sourceColors[index]
        }

        fun addIntersection(source: ClipPolygon, previous: Int, next: Int, t: Float, axis: Int, boundary: Float) {
            for (component in 0..3) {
                val a = source.vertices[previous * 4 + component]
                val b = source.vertices[next * 4 + component]
                vertices[size * 4 + component] = a + (b - a) * t
            }
            vertices[size * 4 + axis] = boundary
            var blended = 0
            for (shift in 0..24 step 8) {
                val a = (source.colors[previous] ushr shift) and 255
                val b = (source.colors[next] ushr shift) and 255
                blended = blended or ((a + (b - a) * t).toInt().coerceIn(0, 255) shl shift)
            }
            colors[size++] = blended
        }
    }

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
     * Clamps one axis of a NanoVG corner. X and Y radii are independent, so a
     * narrow rectangle can have elliptical corners.
     */
    private fun clampRadius(radius: Float, dimension: Float): Float =
        min(max(0f, radius), abs(dimension) * 0.5f)

    /**
     * Outline of a rounded rectangle as x,y pairs in clockwise order starting from the
     * left edge of the top-left corner. Sharp rectangles produce 4 points.
     * Corner order of the parameters matches the renderer API: tl, bl, br, tr.
     */
    fun roundedRectOutline(
        x: Float, y: Float, w: Float, h: Float,
        tl: Float, bl: Float, br: Float, tr: Float,
    ): FloatArray {
        return roundedRectOutlineFixedSegments(x, y, w, h, tl, bl, br, tr, intArrayOf(
            segmentsFor(tl, w, h), segmentsFor(tr, w, h),
            segmentsFor(br, w, h), segmentsFor(bl, w, h),
        ))
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

    /** Removes coincident consecutive points from a closed outline, incl. the wraparound. */
    fun dedupeClosedOutline(outline: FloatArray): FloatArray {
        val pointCount = outline.size / 2
        if (pointCount < 2) return outline
        val out = FloatArray(pointCount * 2)
        var size = 0
        for (i in 0 until pointCount) {
            val x = outline[i * 2]
            val y = outline[i * 2 + 1]
            if (size >= 2 && abs(out[size - 2] - x) < 1e-4f && abs(out[size - 1] - y) < 1e-4f) continue
            out[size++] = x
            out[size++] = y
        }
        if (size >= 4 && abs(out[0] - out[size - 2]) < 1e-4f && abs(out[1] - out[size - 1]) < 1e-4f) {
            size -= 2
        }
        return if (size == out.size) out else out.copyOf(size)
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
        // Lines use the GUI quad winding, while rounded outlines use the opposite
        // traversal. Keep both inner-colour vertices first and choose the edge direction
        // that makes the band face the camera in either case.
        var area = 0f
        for (i in 0 until pointCount) {
            val j = (i + 1) % pointCount
            area += outerOutline[i * 2] * outerOutline[j * 2 + 1] - outerOutline[j * 2] * outerOutline[i * 2 + 1]
        }
        var o = 0
        for (i in 0 until pointCount) {
            val j = (i + (if (area < 0f) pointCount - 1 else 1)) % pointCount
            out[o++] = innerOutline[i * 2]; out[o++] = innerOutline[i * 2 + 1]
            out[o++] = innerOutline[j * 2]; out[o++] = innerOutline[j * 2 + 1]
            out[o++] = outerOutline[j * 2]; out[o++] = outerOutline[j * 2 + 1]
            out[o++] = outerOutline[i * 2]; out[o++] = outerOutline[i * 2 + 1]
        }
        return out
    }

    fun segmentsFor(radius: Float, w: Float, h: Float): Int {
        val r = max(clampRadius(radius, w), clampRadius(radius, h))
        return if (r <= 0f) 0 else arcSegments(r)
    }

    /** Like [roundedRectOutline] but with externally fixed per-corner segment counts (tl, tr, br, bl). */
    fun roundedRectOutlineFixedSegments(
        x: Float, y: Float, w: Float, h: Float,
        tl: Float, bl: Float, br: Float, tr: Float,
        segments: IntArray,
    ): FloatArray {
        val rxTL = clampRadius(tl, w); val ryTL = clampRadius(tl, h)
        val rxBL = clampRadius(bl, w); val ryBL = clampRadius(bl, h)
        val rxBR = clampRadius(br, w); val ryBR = clampRadius(br, h)
        val rxTR = clampRadius(tr, w); val ryTR = clampRadius(tr, h)

        val pointCount = (0..3).sumOf { max(0, segments[it]) + 1 }
        val points = FloatArray(pointCount * 2)
        var offset = cornerFixed(points, 0, x + rxTL, y + ryTL, rxTL, ryTL, 180f, segments[0])
        offset = cornerFixed(points, offset, x + w - rxTR, y + ryTR, rxTR, ryTR, 270f, segments[1])
        offset = cornerFixed(points, offset, x + w - rxBR, y + h - ryBR, rxBR, ryBR, 0f, segments[2])
        cornerFixed(points, offset, x + rxBL, y + h - ryBL, rxBL, ryBL, 90f, segments[3])
        return points
    }

    private fun cornerFixed(out: FloatArray, offset: Int, cx: Float, cy: Float, rx: Float, ry: Float, startDeg: Float, segments: Int): Int {
        if (segments <= 0) {
            // sharp corner: emit the corner point once; offset centre back to the corner
            val sx = when (startDeg) {
                180f -> cx - rx; 90f -> cx - rx; else -> cx + rx
            }
            val sy = when (startDeg) {
                180f -> cy - ry; 270f -> cy - ry; else -> cy + ry
            }
            out[offset] = sx; out[offset + 1] = sy
            return offset + 2
        }
        var offset = offset
        val directions = cornerDirections.getOrNull(segments)
        val cornerOffset = startDeg.toInt() / 90 * (segments + 1) * 2
        for (i in 0..segments) {
            val angle = if (directions == null) Math.toRadians(startDeg + 90.0 * i / segments) else 0.0
            out[offset++] = cx + (rx * (directions?.get(cornerOffset + i * 2) ?: cos(angle))).toFloat()
            out[offset++] = cy + (ry * (directions?.get(cornerOffset + i * 2 + 1) ?: sin(angle))).toFloat()
        }
        return offset
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
