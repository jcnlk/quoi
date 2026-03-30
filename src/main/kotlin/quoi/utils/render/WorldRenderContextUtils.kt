package quoi.utils.render

import com.mojang.blaze3d.vertex.ByteBufferBuilder
import com.mojang.blaze3d.vertex.VertexConsumer
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.ShapeRenderer
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.Shapes
import org.joml.Vector3f
import quoi.QuoiMod.mc
import quoi.api.colour.*
import quoi.utils.EntityUtils.renderPos
import quoi.utils.unaryMinus
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * from OdinFabric (BSD 3-Clause)
 * copyright (c) 2025-2026 odtheking
 * original: no longer exists, fuck off
 */
private val ALLOCATOR = ByteBufferBuilder(1536)

private fun camera() = mc.gameRenderer.mainCamera

private fun VertexConsumer.addLine(
    pose: com.mojang.blaze3d.vertex.PoseStack.Pose,
    start: Vec3,
    end: Vec3,
    colour: Colour,
    thickness: Float
) {
    val dx = (end.x - start.x).toFloat()
    val dy = (end.y - start.y).toFloat()
    val dz = (end.z - start.z).toFloat()
    val len = sqrt(dx * dx + dy * dy + dz * dz).takeIf { it > 0f } ?: 1f
    val nx = dx / len
    val ny = dy / len
    val nz = dz / len

    addVertex(pose, start.x.toFloat(), start.y.toFloat(), start.z.toFloat())
        .setColor(colour.rgb)
        .setNormal(pose, nx, ny, nz)
        .setLineWidth(thickness)
    addVertex(pose, end.x.toFloat(), end.y.toFloat(), end.z.toFloat())
        .setColor(colour.rgb)
        .setNormal(pose, nx, ny, nz)
        .setLineWidth(thickness)
}

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

fun WorldRenderContext.drawLine(points: Collection<Vec3>, colour: Colour, depth: Boolean, thickness: Float = 3f) {
    if (points.size < 2) return
    val matrix = matrices() ?: return
    val bufferSource = consumers() as? MultiBufferSource.BufferSource ?: return
    val layer = if (depth) CustomRenderLayer.LINE_LIST else CustomRenderLayer.LINE_LIST_ESP
    val cameraPos = camera().position()
    val pose = matrix.last()
    val buffer = bufferSource.getBuffer(layer)
    val pointList = points.toList()

    for (i in 0 until pointList.size - 1) {
        val start = pointList[i].subtract(cameraPos)
        val end = pointList[i + 1].subtract(cameraPos)
        buffer.addLine(pose, start, end, colour, thickness)
    }

    bufferSource.endBatch(layer)
}

fun WorldRenderContext.drawTracer(to: Vec3, colour: Colour, thickness: Float = 6f, depth: Boolean = false) {
    val from = mc.player?.let { player ->
        player.renderPos.add(player.forward.add(0.0, player.eyeHeight.toDouble(), 0.0))
    } ?: return
    drawLine(listOf(from, to), colour, depth, thickness)
}

fun WorldRenderContext.drawWireFrameBox(aabb: AABB, colour: Colour, thickness: Float = 6f, depth: Boolean = false) {
    val matrix = matrices() ?: return
    val bufferSource = consumers() as? MultiBufferSource.BufferSource ?: return
    val layer = if (depth) CustomRenderLayer.LINE_LIST else CustomRenderLayer.LINE_LIST_ESP
    val cameraPos = camera().position()

    ShapeRenderer.renderShape(
        matrix,
        bufferSource.getBuffer(layer),
        Shapes.create(aabb),
        -cameraPos.x,
        -cameraPos.y,
        -cameraPos.z,
        colour.rgb,
        colour.alphaFloat
    )

    bufferSource.endBatch(layer)
}

fun WorldRenderContext.drawFilledBox(box: AABB, colour: Colour, depth: Boolean = false) {
    val matrix = matrices() ?: return
    val bufferSource = consumers() as? MultiBufferSource.BufferSource ?: return
    val layer = if (depth) CustomRenderLayer.TRIANGLE_STRIP else CustomRenderLayer.TRIANGLE_STRIP_ESP
    val cameraPos = camera().position()
    val pose = matrix.last()
    val buffer = bufferSource.getBuffer(layer)

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

    bufferSource.endBatch(layer)
}

fun WorldRenderContext.drawStyledBox(style: String, box: AABB, colour: Colour, fillColour: Colour = colour, thickness: Float = 2.0f, depth: Boolean = false) {
    when (style) {
        "Box" -> drawWireFrameBox(box, colour, thickness, depth)
        "Filled box" -> {
            drawFilledBox(box, fillColour, depth)
            drawWireFrameBox(box, colour, thickness, depth)
        }
    }
}

fun WorldRenderContext.drawText(text: Component, pos: Vec3, colour: Colour = Colour.TRANSPARENT, shadow: Boolean = true, scale: Float = 0.5f, depth: Boolean = false) {
    val stack = matrices() ?: return

    stack.pushPose()
    val matrix = stack.last().pose()
    with(scale * 0.025f) {
        val cameraPos = -camera().position()
        matrix.translate(pos.toVector3f()).translate(cameraPos.x.toFloat(), cameraPos.y.toFloat(), cameraPos.z.toFloat()).rotate(camera().rotation()).scale(this, -this, this)
    }

    val consumers = MultiBufferSource.immediate(ALLOCATOR)

    mc.font?.let {
        it.drawInBatch(
            text, -it.width(text) / 2f, 0f, -1, shadow, matrix, consumers,
            if (depth) Font.DisplayMode.NORMAL else Font.DisplayMode.SEE_THROUGH,
            colour.rgb, LightTexture.FULL_BRIGHT
        )
    }

    consumers.endBatch()
    stack.popPose()
}

fun WorldRenderContext.drawCylinder(
    center: Vec3,
    radius: Float,
    height: Float,
    colour: Colour,
    segments: Int = 32,
    thickness: Float = 5f,
    depth: Boolean = false
) {
    val matrix = matrices() ?: return
    val bufferSource = consumers() as? MultiBufferSource.BufferSource ?: return
    val layer = if (depth) CustomRenderLayer.LINE_LIST else CustomRenderLayer.LINE_LIST_ESP
    val cameraPos = camera().position()
    val pose = matrix.last()
    val buffer = bufferSource.getBuffer(layer)
    val translatedCenter = center.subtract(cameraPos)
    val angleStep = 2.0 * Math.PI / segments

    for (i in 0 until segments) {
        val angle1 = i * angleStep
        val angle2 = (i + 1) * angleStep

        val x1 = translatedCenter.x + radius * kotlin.math.cos(angle1)
        val z1 = translatedCenter.z + radius * kotlin.math.sin(angle1)
        val x2 = translatedCenter.x + radius * kotlin.math.cos(angle2)
        val z2 = translatedCenter.z + radius * kotlin.math.sin(angle2)
        val topY = translatedCenter.y + height

        val topStart = Vec3(x1, topY, z1)
        val topEnd = Vec3(x2, topY, z2)
        val bottomStart = Vec3(x1, translatedCenter.y, z1)
        val bottomEnd = Vec3(x2, translatedCenter.y, z2)

        buffer.addLine(pose, topStart, topEnd, colour, thickness)
        buffer.addLine(pose, bottomStart, bottomEnd, colour, thickness)
        buffer.addLine(pose, bottomStart, topStart, colour, thickness)
    }

    bufferSource.endBatch(layer)
}
