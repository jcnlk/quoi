//~ desktop_platform
//~ texture_resources
//~ gui_extraction

package quoi.ui

import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.SharedConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractButton
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.ConnectScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen
import net.minecraft.client.gui.screens.options.OptionsScreen
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen
import net.minecraft.client.input.InputWithModifiers
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.resolver.ServerAddress
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import quoi.QuoiMod
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.module.impl.render.CustomMainMenu
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.ThemeManager.theme
import quoi.utils.render.DrawContextUtils.drawImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import javax.imageio.ImageIO
import kotlin.math.max

import net.minecraft.util.Util

class CustomMainMenuScreen(
    private val externalTitleButtons: List<AbstractButton> = emptyList(),
) : Screen(Component.literal("Quoi Main Menu")) {
    private var loadedSource: Pair<File, Long>? = null
    private var loadedImage: DynamicTexture? = null
    private var loadedImageWidth = 256
    private var loadedImageHeight = 256
    private var sampledMenuColour: Colour? = null

    override fun init() {
        loadBackground()
        val x = 10.coerceAtMost(width - 40)
        var y = 10.coerceAtMost(height - 40)
        val buttonWidth = 200.coerceAtMost(width - x - 10)

        fun addButton(label: String, active: Boolean = true, action: (InputWithModifiers) -> Unit) {
            addRenderableWidget(CustomMenuButton(x, y, buttonWidth, label, action).apply { this.active = active })
            y += BUTTON_HEIGHT + BUTTON_GAP
        }

        val multiplayerAllowed = minecraft.allowsMultiplayer() && !minecraft.isDemo
        addButton("Singleplayer") { minecraft.setScreen(SelectWorldScreen(this)) }
        addButton("Multiplayer", multiplayerAllowed) { minecraft.setScreen(JoinMultiplayerScreen(this)) }
        if (CustomMainMenu.showHypixelButton) addButton("Join Hypixel", multiplayerAllowed) { joinServer("Hypixel", "mc.hypixel.net") }
        if (CustomMainMenu.showP3SimButton) addButton("Join P3Sim", multiplayerAllowed) { joinServer("P3Sim", "p3sim.net") }
        //~ if >=26.3 'minecraft.options, false)' -> 'minecraft.options)'
        addButton("Options") { minecraft.setScreen(OptionsScreen(this, minecraft.options, false)) }
        externalTitleButtons.forEach { button ->
            addButton(button.message.string.noControlCodes.trim(), button.active) { input ->
                if (button.active && button.visible) button.onPress(input)
            }
        }

        addRenderableWidget(
            CustomMenuButton(x, height - BUTTON_HEIGHT - 10, buttonWidth.coerceAtMost(width - 216 - x), "Quit") { minecraft.stop() }
        )

        listOf("GitHub" to "https://github.com/jcnlk/quoi", "Discord" to "https://discord.com/invite/QCWgrQ57pN")
            .forEachIndexed { index, (label, url) ->
                addRenderableWidget(CustomMenuButton(width - 206 + index * 100, height - BUTTON_HEIGHT - 10, 92, label) {
                    Util.getPlatform().openUri(URI(url))
                })
            }
    }

    override fun extractRenderState(guiGraphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        extractBackground(guiGraphics, mouseX, mouseY, deltaTicks)
        guiGraphics.text(font, "${SharedConstants.getCurrentVersion().name()} - Fabric",
            12, height - BUTTON_HEIGHT - 27, theme.onSurface.withAlpha(220).rgb, true)
        super.extractRenderState(guiGraphics, mouseX, mouseY, deltaTicks)
    }

    override fun extractBackground(guiGraphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        val background = loadedImage
        if (background != null) drawCover(guiGraphics, BACKGROUND_ID)
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
        guiGraphics.drawImage(image, x, y, drawWidth, drawHeight)
    }

    private fun loadBackground() {
        val file = CustomMainMenu.selectedBackgroundFile()
        val source = file?.let { it to it.lastModified() }
        if (source == loadedSource && (file == null || loadedImage != null && minecraft.textureManager.getTexture(BACKGROUND_ID) === loadedImage)) return
        loadedSource = source
        sampledMenuColour = null
        loadedImage = file?.let {
            runCatching {
                val image = loadNativeImage(file)
                loadedImageWidth = image.width
                loadedImageHeight = image.height
                sampledMenuColour = sampleColour(image)
                DynamicTexture({ "quoi custom main menu background" }, image).also {
                    minecraft.textureManager.register(BACKGROUND_ID, it)
                }
            }.onFailure { error ->
                QuoiMod.logger.error("Failed to load custom main menu background: ${file.absolutePath}", error)
            }.getOrNull()
        }
    }

    private fun loadNativeImage(file: File): NativeImage {
        if (file.extension.equals("png", ignoreCase = true)) {
            return file.inputStream().use { NativeImage.read(it) }
        }

        val image = ImageIO.read(file) ?: error("Unsupported background image format: ${file.extension}")
        val png = ByteArrayOutputStream()
        ImageIO.write(image, "png", png)
        return NativeImage.read(png.toByteArray())
    }

    private fun sampleColour(image: NativeImage): Colour {
        val pixels = (0 until image.width step max(1, image.width / 24)).flatMap { x ->
            (0 until image.height step max(1, image.height / 24)).map { y -> image.getPixel(x, y) }
        }
        fun channel(shift: Int) = pixels.map { it ushr shift and 0xFF }.average().toInt().coerceIn(48, 255)
        return Colour.RGB(channel(0), channel(8), channel(16))
    }

    private companion object {
        val BACKGROUND_ID = Identifier.fromNamespaceAndPath(QuoiMod.MOD_ID, "custom_main_menu_background")
        const val BUTTON_HEIGHT = 20
        const val BUTTON_GAP = 5
    }

    private inner class CustomMenuButton(
        x: Int,
        y: Int,
        width: Int,
        label: String,
        private val action: (InputWithModifiers) -> Unit,
    ) : Button(x, y, width, BUTTON_HEIGHT, Component.literal(label), {}, DEFAULT_NARRATION) {
        override fun extractContents(guiGraphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, deltaTicks: Float) {
            val accent = if (CustomMainMenu.autoMenuColour) sampledMenuColour ?: theme.primary else CustomMainMenu.menuColour
            val fill = if (isHoveredOrFocused) accent.withAlpha(60).rgb else theme.surface.withAlpha(35).rgb
            val outline = if (isHoveredOrFocused) accent.rgb else accent.withAlpha(210).rgb
            guiGraphics.fill(x, y, x + width, y + height, fill)
            guiGraphics.outline(x, y, width, height, outline)
            if (isHoveredOrFocused) guiGraphics.fill(x + 2, y + 2, x + 4, y + height - 2, outline)

            guiGraphics.text(font, message, x + (width - font.width(message)) / 2,
                y + (height - 8) / 2, theme.onSurface.rgb, true)
        }

        override fun onPress(input: InputWithModifiers) = action(input)

        // Preserve the original menu cursor.
        override fun handleCursor(guiGraphics: GuiGraphicsExtractor) {}
    }
}
