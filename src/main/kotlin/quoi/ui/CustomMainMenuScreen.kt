//~ desktop_platform
//~ texture_resources
//~ gui_extraction
//~ gui_pipeline

package quoi.ui

import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.SharedConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.screens.ConnectScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen
import net.minecraft.client.gui.screens.options.OptionsScreen
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.MouseButtonInfo
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.resolver.ServerAddress
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import quoi.QuoiMod
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.module.impl.render.CustomMainMenu
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.ThemeManager.theme
import quoi.utils.render.DrawContextUtils.drawText
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import javax.imageio.ImageIO
import kotlin.math.max

import net.minecraft.util.Util
//? if >=26.3
//import org.lwjgl.sdl.SDLMouse

class CustomMainMenuScreen(
    private val externalTitleButtons: List<AbstractWidget> = emptyList(),
) : Screen(Component.literal("Quoi Main Menu")) {
    private var loadedImagePath = ""
    private var loadedImage: Identifier? = null
    private var loadedImageWidth = 256
    private var loadedImageHeight = 256
    private var sampledMenuColour: Colour? = null

    override fun init() {
        val x = 10.coerceAtMost(width - 40)
        var y = 10.coerceAtMost(height - 40)
        val buttonWidth = 200.coerceAtMost(width - x - 10)

        fun addButton(label: String, action: () -> Unit) {
            addRenderableWidget(CustomMenuButton(x, y, buttonWidth, label, action))
            y += BUTTON_HEIGHT + BUTTON_GAP
        }

        addButton("Singleplayer") { minecraft.setScreen(SelectWorldScreen(this)) }
        addButton("Multiplayer") { minecraft.setScreen(JoinMultiplayerScreen(this)) }
        if (CustomMainMenu.showHypixelButton) addButton("Join Hypixel") { joinServer("Hypixel", "mc.hypixel.net") }
        if (CustomMainMenu.showP3SimButton) addButton("Join P3Sim") { joinServer("P3Sim", "p3sim.net") }
        //~ if >=26.3 'minecraft.options, false)' -> 'minecraft.options)'
        addButton("Options") { minecraft.setScreen(OptionsScreen(this, minecraft.options, false)) }
        externalTitleButtons.forEach { button ->
            addButton(button.message.string.noControlCodes.trim()) {
                button.mouseClicked(
                    MouseButtonEvent(
                        button.x + button.width / 2.0,
                        button.y + button.height / 2.0,
                        //~ if >=26.3 'MouseButtonInfo(0, 0)' -> 'MouseButtonInfo(SDLMouse.SDL_BUTTON_LEFT, 0)'
                        MouseButtonInfo(0, 0),
                    ),
                    false,
                )
            }
        }

        addRenderableWidget(
            CustomMenuButton(x, height - BUTTON_HEIGHT - 10, buttonWidth, "Quit") { minecraft.stop() }
        )

        addRenderableWidget(
            CustomMenuButton(width - 206, height - BUTTON_HEIGHT - 10, 92, "GitHub") {
                Util.getPlatform().openUri(URI("https://github.com/jcnlk/quoi"))
            }
        )
        addRenderableWidget(
            CustomMenuButton(width - 106, height - BUTTON_HEIGHT - 10, 92, "Discord") {
                Util.getPlatform().openUri(URI("https://discord.com/invite/QCWgrQ57pN"))
            }
        )
    }

    override fun extractRenderState(guiGraphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        extractBackground(guiGraphics, mouseX, mouseY, deltaTicks)
        guiGraphics.drawText(
            "${SharedConstants.getCurrentVersion().name()} - Fabric",
            12,
            height - BUTTON_HEIGHT - 27,
            theme.onSurface.withAlpha(220).rgb,
            shadow = true
        )
        super.extractRenderState(guiGraphics, mouseX, mouseY, deltaTicks)
    }

    override fun extractBackground(guiGraphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        val background = background()
        if (background != null) drawCover(guiGraphics, background)
        //~ if >=26.2 'extractPanorama(guiGraphics, deltaTicks)' -> 'minecraft.gameRenderer.panorama().extractRenderState(guiGraphics, width, height)'
        else extractPanorama(guiGraphics, deltaTicks)

        val dim = (CustomMainMenu.dimAmount.coerceIn(0f, 1f) * 255).toInt() shl 24
        guiGraphics.fill(0, 0, width, height, dim)
    }

    private fun joinServer(name: String, address: String) {
        ConnectScreen.startConnecting(
            this,
            minecraft,
            ServerAddress.parseString(address),
            ServerData(name, address, ServerData.Type.OTHER),
            false,
            null,
        )
    }

    private fun drawCover(guiGraphics: GuiGraphicsExtractor, image: Identifier) {
        val scale = max(width / loadedImageWidth.toFloat(), height / loadedImageHeight.toFloat())
        val drawWidth = (loadedImageWidth * scale).toInt()
        val drawHeight = (loadedImageHeight * scale).toInt()
        val x = (width - drawWidth) / 2
        val y = (height - drawHeight) / 2
        guiGraphics.blit(
            RenderPipelines.GUI_TEXTURED,
            image,
            x,
            y,
            0f,
            0f,
            drawWidth,
            drawHeight,
            loadedImageWidth,
            loadedImageHeight,
            loadedImageWidth,
            loadedImageHeight,
        )
    }

    private fun background(): Identifier? {
        val file = CustomMainMenu.selectedBackgroundFile() ?: return null
        val image = file.absolutePath

        if (loadedImagePath == image) return loadedImage
        loadedImagePath = image
        sampledMenuColour = null
        loadedImage = runCatching {
            val nativeImage = loadNativeImage(file)
            loadedImageWidth = nativeImage.width
            loadedImageHeight = nativeImage.height
            sampledMenuColour = sampleColour(nativeImage)
            val id = Identifier.fromNamespaceAndPath(QuoiMod.MOD_ID, "custom_main_menu_background")
            QuoiMod.mc.textureManager.register(id, DynamicTexture({ "quoi custom main menu background" }, nativeImage))
            QuoiMod.logger.info("Loaded custom main menu background: ${file.absolutePath} ($loadedImageWidth x $loadedImageHeight)")
            id
        }.onFailure { error ->
            loadedImagePath = ""
            loadedImageWidth = 256
            loadedImageHeight = 256
            QuoiMod.logger.error("Failed to load custom main menu background: ${file.absolutePath}", error)
        }.getOrNull()
        return loadedImage
    }

    private fun loadNativeImage(file: File): NativeImage {
        if (file.extension.equals("png", ignoreCase = true)) {
            return file.inputStream().use { NativeImage.read(it) }
        }

        val bufferedImage = ImageIO.read(file) ?: error("Unsupported background image format: ${file.extension}")
        val output = ByteArrayOutputStream()
        ImageIO.write(bufferedImage, "png", output)
        return ByteArrayInputStream(output.toByteArray()).use(NativeImage::read)
    }

    private fun sampleColour(image: NativeImage): Colour {
        var r = 0L
        var g = 0L
        var b = 0L
        var samples = 0L
        val stepX = (image.width / 24).coerceAtLeast(1)
        val stepY = (image.height / 24).coerceAtLeast(1)

        for (x in 0 until image.width step stepX) {
            for (y in 0 until image.height step stepY) {
                val pixel = image.getPixel(x, y)
                r += pixel and 0xFF
                g += (pixel shr 8) and 0xFF
                b += (pixel shr 16) and 0xFF
                samples++
            }
        }

        if (samples == 0L) return theme.primary
        return Colour.RGB(
            (r / samples).toInt().coerceIn(48, 255),
            (g / samples).toInt().coerceIn(48, 255),
            (b / samples).toInt().coerceIn(48, 255),
        )
    }

    private fun menuColour(): Colour =
        if (CustomMainMenu.autoMenuColour) sampledMenuColour ?: theme.primary else CustomMainMenu.menuColour

    private companion object {
        const val BUTTON_HEIGHT = 20
        const val BUTTON_GAP = 5
    }

    private inner class CustomMenuButton(
        x: Int,
        y: Int,
        width: Int,
        label: String,
        private val action: () -> Unit,
    ) : AbstractWidget(x, y, width, BUTTON_HEIGHT, Component.literal(label)) {
        override fun extractWidgetRenderState(guiGraphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, deltaTicks: Float) {
            val accent = menuColour()
            val fill = if (isHoveredOrFocused) accent.withAlpha(60).rgb else theme.surface.withAlpha(35).rgb
            val outline = if (isHoveredOrFocused) accent.rgb else accent.withAlpha(210).rgb
            guiGraphics.fill(x, y, x + width, y + height, fill)
            guiGraphics.outline(x, y, width, height, outline)
            if (isHoveredOrFocused) guiGraphics.fill(x + 2, y + 2, x + 4, y + height - 2, outline)

            val label = message.string
            val textX = x + (width - font.width(label)) / 2
            val textY = y + (height - 8) / 2
            guiGraphics.drawText(label, textX, textY, theme.onSurface.rgb, shadow = true)
        }

        override fun onClick(mouseButtonEvent: MouseButtonEvent, doubleClick: Boolean) = action()

        override fun updateWidgetNarration(narrationElementOutput: NarrationElementOutput) {
            defaultButtonNarrationText(narrationElementOutput)
        }
    }
}
