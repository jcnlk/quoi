package quoi.utils.ui.rendering

import com.mojang.blaze3d.opengl.GlTextureView
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState
import quoi.QuoiMod.mc
import quoi.mixins.accessors.GuiGraphicsExtractorAccessor
import org.joml.Matrix3x2f
import org.lwjgl.opengl.GL11C
import org.lwjgl.opengl.GL30C
import org.lwjgl.opengl.GL33C

/**
 * Submits NanoVG content through Minecraft's 26.2 GUI extraction/render split.
 *
 * This renderer intentionally supports the deprecated OpenGL backend only. With Vulkan active,
 * NanoVG's GL renderer cannot draw into Minecraft's GPU textures.
 */
class NVGSpecialRenderer : PictureInPictureRenderer<NVGSpecialRenderer.NVGRenderState>() {

    override fun renderToTexture(state: NVGRenderState, poseStack: PoseStack, submitNodeCollector: SubmitNodeCollector) {
        val colorView = RenderSystem.outputColorTextureOverride as? GlTextureView ?: return
        val depthView = RenderSystem.outputDepthTextureOverride as? GlTextureView ?: return

        val previousFramebuffer = GL11C.glGetInteger(GL30C.GL_FRAMEBUFFER_BINDING)
        val framebuffer = GL30C.glGenFramebuffers()
        GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, framebuffer)
        GL30C.glFramebufferTexture2D(
            GL30C.GL_FRAMEBUFFER,
            GL30C.GL_COLOR_ATTACHMENT0,
            GL11C.GL_TEXTURE_2D,
            colorView.glId(),
            colorView.fboMipLevel()
        )
        GL30C.glFramebufferTexture2D(
            GL30C.GL_FRAMEBUFFER,
            GL30C.GL_DEPTH_ATTACHMENT,
            GL11C.GL_TEXTURE_2D,
            depthView.glId(),
            depthView.fboMipLevel()
        )
        GL30C.glViewport(0, 0, colorView.getWidth(0), colorView.getHeight(0))
        GL33C.glBindSampler(0, 0)

        NVGRenderer.beginFrame(mc.window.width.toFloat(), mc.window.height.toFloat())
        state.renderContent()
        NVGRenderer.endFrame()

        GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, previousFramebuffer)
        GL30C.glDeleteFramebuffers(framebuffer)
    }

    override fun getRenderStateClass(): Class<NVGRenderState> = NVGRenderState::class.java

    override fun getTextureLabel(): String = "quoi_nvg_renderer"

    data class NVGRenderState(
        private val x: Int,
        private val y: Int,
        private val width: Int,
        private val height: Int,
        private val poseMatrix: Matrix3x2f,
        private val scissor: ScreenRectangle?,
        private val bounds: ScreenRectangle?,
        val renderContent: () -> Unit
    ) : PictureInPictureRenderState {

        override fun scale(): Float = 1f
        override fun x0(): Int = x
        override fun y0(): Int = y
        override fun x1(): Int = x + width
        override fun y1(): Int = y + height
        override fun pose(): Matrix3x2f = poseMatrix
        override fun scissorArea(): ScreenRectangle? = scissor
        override fun bounds(): ScreenRectangle? = bounds
    }

    companion object {
        fun register() {
            PictureInPictureRendererRegistry.register { NVGSpecialRenderer() }
        }

        fun draw(
            context: GuiGraphicsExtractor,
            x: Int,
            y: Int,
            width: Int,
            height: Int,
            renderContent: () -> Unit
        ) {
            val pose = Matrix3x2f(context.pose())
            val bounds = createBounds(x, y, x + width, y + height, pose, null)

            (context as GuiGraphicsExtractorAccessor).`quoi$getGuiRenderState`().addPicturesInPictureState(
                NVGRenderState(x, y, width, height, pose, null, bounds, renderContent)
            )
        }

        private fun createBounds(
            x0: Int,
            y0: Int,
            x1: Int,
            y1: Int,
            pose: Matrix3x2f,
            scissorArea: ScreenRectangle?
        ): ScreenRectangle? {
            val screenRect = ScreenRectangle(x0, y0, x1 - x0, y1 - y0).transformMaxBounds(pose)
            return scissorArea?.intersection(screenRect) ?: screenRect
        }
    }
}
