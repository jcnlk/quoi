package quoi.utils.render

import com.mojang.blaze3d.vertex.VertexConsumer
import kotlin.math.max
import kotlin.math.pow
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.gui.Font
import net.minecraft.network.chat.Component
import net.minecraft.util.LightCoordsUtil
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Vector3fc
import quoi.QuoiMod.mc
import quoi.api.colour.Colour
import quoi.utils.EntityUtils.renderPos
import quoi.utils.player
import quoi.utils.skyblock.player.PlayerUtils.eyeHeight
import quoi.utils.unaryMinus

private fun camera() = mc.gameRenderer.mainCamera()

private fun VertexConsumer.addQuad(
    pose: com.mojang.blaze3d.vertex.PoseStack.Pose,
    a: Vec3,
    b: Vec3,
    c: Vec3,
    d: Vec3,
    colour: Colour
) {
    addVertex(pose, a.x.toFloat(), a.y.toFloat(), a.z.toFloat()).setColor(colour.rgb)
    addVertex(pose, b.x.toFloat(), b.y.toFloat(), b.z.toFloat()).setColor(colour.rgb)
    addVertex(pose, c.x.toFloat(), c.y.toFloat(), c.z.toFloat()).setColor(colour.rgb)
    addVertex(pose, d.x.toFloat(), d.y.toFloat(), d.z.toFloat()).setColor(colour.rgb)
}

private fun Vector3fc.toVec3() = Vec3(x().toDouble(), y().toDouble(), z().toDouble())

private fun lineGeometry(cameraPos: Vec3): LineGeometry {
    val camera = camera()
    return LineGeometry(
        cameraPos, camera.forwardVector().toVec3(), camera.leftVector().toVec3(), camera.upVector().toVec3(),
        camera.fov.toDouble(), mc.window.height.toDouble()
    )
}

private fun boxEdges(box: AABB): List<Pair<Vec3, Vec3>> {
    val minX = box.minX
    val minY = box.minY
    val minZ = box.minZ
    val maxX = box.maxX
    val maxY = box.maxY
    val maxZ = box.maxZ

    val x0y0z0 = Vec3(minX, minY, minZ)
    val x0y0z1 = Vec3(minX, minY, maxZ)
    val x0y1z0 = Vec3(minX, maxY, minZ)
    val x0y1z1 = Vec3(minX, maxY, maxZ)
    val x1y0z0 = Vec3(maxX, minY, minZ)
    val x1y0z1 = Vec3(maxX, minY, maxZ)
    val x1y1z0 = Vec3(maxX, maxY, minZ)
    val x1y1z1 = Vec3(maxX, maxY, maxZ)

    return listOf(
        x0y0z0 to x1y0z0,
        x0y0z1 to x1y0z1,
        x0y1z0 to x1y1z0,
        x0y1z1 to x1y1z1,
        x0y0z0 to x0y1z0,
        x1y0z0 to x1y1z0,
        x0y0z1 to x0y1z1,
        x1y0z1 to x1y1z1,
        x0y0z0 to x0y0z1,
        x1y0z0 to x1y0z1,
        x0y1z0 to x0y1z1,
        x1y1z0 to x1y1z1
    )
}

private fun legacyDistanceScaledThickness(cameraPos: Vec3, reference: Vec3, thickness: Float): Float {
    val distanceSqr = max(cameraPos.distanceToSqr(reference), 1.0)
    return (thickness / distanceSqr.pow(0.15)).toFloat()
}

private fun addFilledBox(buffer: VertexConsumer, pose: com.mojang.blaze3d.vertex.PoseStack.Pose, box: AABB, cameraPos: Vec3, colour: Colour) {
    val minX = box.minX - cameraPos.x
    val minY = box.minY - cameraPos.y
    val minZ = box.minZ - cameraPos.z
    val maxX = box.maxX - cameraPos.x
    val maxY = box.maxY - cameraPos.y
    val maxZ = box.maxZ - cameraPos.z

    val x0y0z0 = Vec3(minX, minY, minZ)
    val x0y0z1 = Vec3(minX, minY, maxZ)
    val x0y1z0 = Vec3(minX, maxY, minZ)
    val x0y1z1 = Vec3(minX, maxY, maxZ)
    val x1y0z0 = Vec3(maxX, minY, minZ)
    val x1y0z1 = Vec3(maxX, minY, maxZ)
    val x1y1z0 = Vec3(maxX, maxY, minZ)
    val x1y1z1 = Vec3(maxX, maxY, maxZ)

    buffer.addQuad(pose, x0y0z0, x0y1z0, x1y1z0, x1y0z0, colour)
    buffer.addQuad(pose, x1y0z1, x1y1z1, x0y1z1, x0y0z1, colour)
    buffer.addQuad(pose, x0y0z1, x0y1z1, x0y1z0, x0y0z0, colour)
    buffer.addQuad(pose, x1y0z0, x1y1z0, x1y1z1, x1y0z1, colour)
    buffer.addQuad(pose, x0y1z0, x0y1z1, x1y1z1, x1y1z0, colour)
    buffer.addQuad(pose, x0y0z1, x0y0z0, x1y0z0, x1y0z1, colour)
}

fun LevelRenderContext.drawLine(points: Collection<Vec3>, colour: Colour, depth: Boolean = false, thickness: Float = 3f) {
    if (points.size < 2) return
    val matrix = poseStack()
    val cameraPos = camera().position()
    val pointList = points.toList()

    val lines = lineGeometry(cameraPos)
    val renderColour = Colour.RGB(colour.rgb)
    submitNodeCollector().submitOverlayGeometry(matrix, depth) { pose, buffer ->
        for (i in 0 until pointList.size - 1) {
            lines.addSegment(buffer, pose, pointList[i], pointList[i + 1], renderColour, thickness)
        }
    }
}

fun LevelRenderContext.drawTracer(to: Vec3, colour: Colour, thickness: Float = 6f, depth: Boolean = false) {
    val from = player.renderPos.add(player.forward.add(0.0, player.eyeHeight().toDouble(), 0.0))
    drawLine(listOf(from, to), colour, depth, thickness)
}

fun LevelRenderContext.drawWireFrameBox(aabb: AABB, colour: Colour, thickness: Float = 6f, depth: Boolean = false) {
    val matrix = poseStack()
    val cameraPos = camera().position()
    val legacyThickness = legacyDistanceScaledThickness(cameraPos, aabb.center, thickness)

    val lines = lineGeometry(cameraPos)
    val renderColour = Colour.RGB(colour.rgb)
    submitNodeCollector().submitOverlayGeometry(matrix, depth) { pose, buffer ->
        boxEdges(aabb).forEach { (start, end) ->
            lines.addSegment(buffer, pose, start, end, renderColour, legacyThickness)
        }
    }
}

fun LevelRenderContext.drawFilledBox(box: AABB, colour: Colour, depth: Boolean = false) {
    val matrix = poseStack()
    val cameraPos = camera().position()
    val renderColour = Colour.RGB(colour.rgb)
    submitNodeCollector().submitOverlayGeometry(matrix, depth) { pose, buffer ->
        addFilledBox(buffer, pose, box, cameraPos, renderColour)
    }
}

fun LevelRenderContext.drawStyledBox(
    style: String,
    box: AABB,
    colour: Colour,
    fillColour: Colour = colour,
    thickness: Float = 2.0f,
    depth: Boolean = false
) {
    when (style) {
        "Box" -> drawWireFrameBox(box, colour, thickness, depth)
        "Filled box" -> {
            drawFilledBox(box, fillColour, depth)
            drawWireFrameBox(box, colour, thickness, depth)
        }
        "Filled" -> drawFilledBox(box, fillColour, depth)
    }
}

fun LevelRenderContext.drawText(
    text: Component,
    pos: Vec3,
    colour: Colour = Colour.TRANSPARENT,
    shadow: Boolean = true,
    scale: Float = 0.5f,
    depth: Boolean = false
) {
    val stack = poseStack()

    stack.pushPose()
    val matrix = stack.last().pose()
    with(scale * 0.025f) {
        val cameraPos = -camera().position()
        matrix.translate(pos.add(cameraPos).toVector3f()).rotate(camera().rotation()).scale(this, -this, this)
    }

    submitNodeCollector().submitText(
        stack,
        -mc.font.width(text) / 2f,
        0f,
        text.visualOrderText,
        shadow,
        if (depth) Font.DisplayMode.NORMAL else Font.DisplayMode.SEE_THROUGH,
        LightCoordsUtil.FULL_BRIGHT,
        -1,
        colour.rgb,
        0
    )
    stack.popPose()
}

fun LevelRenderContext.drawCylinder(
    center: Vec3,
    radius: Float,
    height: Float,
    colour: Colour,
    segments: Int = 32,
    thickness: Float = 5f,
    depth: Boolean = false
) {
    val matrix = poseStack()
    val cameraPos = camera().position()
    val angleStep = 2.0 * Math.PI / segments
    val legacyThickness = legacyDistanceScaledThickness(cameraPos, center, thickness)

    val lines = lineGeometry(cameraPos)
    val renderColour = Colour.RGB(colour.rgb)
    submitNodeCollector().submitOverlayGeometry(matrix, depth) { pose, buffer ->
        for (i in 0 until segments) {
            val angle1 = i * angleStep
            val angle2 = (i + 1) * angleStep

            val x1 = center.x + radius * kotlin.math.cos(angle1)
            val z1 = center.z + radius * kotlin.math.sin(angle1)
            val x2 = center.x + radius * kotlin.math.cos(angle2)
            val z2 = center.z + radius * kotlin.math.sin(angle2)
            val topY = center.y + height

            val topStart = Vec3(x1, topY, z1)
            val topEnd = Vec3(x2, topY, z2)
            val bottomStart = Vec3(x1, center.y, z1)
            val bottomEnd = Vec3(x2, center.y, z2)

            lines.addSegment(buffer, pose, topStart, topEnd, renderColour, legacyThickness)
            lines.addSegment(buffer, pose, bottomStart, bottomEnd, renderColour, legacyThickness)
            lines.addSegment(buffer, pose, bottomStart, topStart, renderColour, legacyThickness)
        }
    }
}
