package quoi.utils.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.world.phys.Vec3
import quoi.api.colour.Colour
import kotlin.math.hypot
import kotlin.math.tan

internal class LineGeometry(
    private val cameraPos: Vec3,
    private val forward: Vec3,
    private val left: Vec3,
    private val up: Vec3,
    fovDegrees: Double,
    private val windowHeight: Double
) {
    private val tanHalfFov = tan(Math.toRadians(fovDegrees) / 2.0)

    fun addSegment(
        buffer: VertexConsumer,
        pose: PoseStack.Pose,
        start: Vec3,
        end: Vec3,
        colour: Colour,
        thickness: Float
    ) {
        if (thickness <= 0f || windowHeight <= 0.0) return
        var a = start.subtract(cameraPos)
        var b = end.subtract(cameraPos)
        var startDepth = a.dot(forward)
        var endDepth = b.dot(forward)
        val near = 0.05
        if (startDepth < near && endDepth < near) return

        if (startDepth < near) {
            a = a.lerp(b, (near - startDepth) / (endDepth - startDepth))
            startDepth = near
        } else if (endDepth < near) {
            b = b.lerp(a, (near - endDepth) / (startDepth - endDepth))
            endDepth = near
        }

        val dx = b.dot(left) / endDepth - a.dot(left) / startDepth
        val dy = b.dot(up) / endDepth - a.dot(up) / startDepth
        val length = hypot(dx, dy)
        if (length <= 1.0E-9) return
        val perpendicular = left.scale(-dy / length).add(up.scale(dx / length))
        val pixelWidth = thickness * tanHalfFov / windowHeight
        addQuad(
            buffer,
            pose,
            a,
            b,
            perpendicular.scale(startDepth * pixelWidth),
            perpendicular.scale(endDepth * pixelWidth),
            colour
        )
    }

    private fun addQuad(
        buffer: VertexConsumer,
        pose: PoseStack.Pose,
        start: Vec3,
        end: Vec3,
        startOffset: Vec3,
        endOffset: Vec3,
        colour: Colour
    ) {
        buffer.addVertex(
            pose,
            (start.x - startOffset.x).toFloat(),
            (start.y - startOffset.y).toFloat(),
            (start.z - startOffset.z).toFloat()
        ).setColor(colour.rgb)
        buffer.addVertex(
            pose,
            (start.x + startOffset.x).toFloat(),
            (start.y + startOffset.y).toFloat(),
            (start.z + startOffset.z).toFloat()
        ).setColor(colour.rgb)
        buffer.addVertex(
            pose,
            (end.x + endOffset.x).toFloat(),
            (end.y + endOffset.y).toFloat(),
            (end.z + endOffset.z).toFloat()
        ).setColor(colour.rgb)
        buffer.addVertex(
            pose,
            (end.x - endOffset.x).toFloat(),
            (end.y - endOffset.y).toFloat(),
            (end.z - endOffset.z).toFloat()
        ).setColor(colour.rgb)
    }
}
