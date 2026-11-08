//~ desktop_platform
package quoi.module.impl.render

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.minecraft.client.gui.components.AbstractButton
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.network.chat.contents.TranslatableContents
import net.minecraft.util.Util
import quoi.api.colour.Colour
import quoi.module.Module
import quoi.module.settings.impl.SelectorComponent
import quoi.ui.CustomMainMenuScreen
import quoi.utils.StringUtils.noControlCodes
import java.io.File

object CustomMainMenu : Module(
    "Custom Main Menu",
    desc = "Replaces the vanilla main menu with a custom one."
) {
    private const val VANILLA_BACKGROUND = "Vanilla"
    private val SUPPORTED_BACKGROUND_EXTENSIONS = setOf("png", "jpg", "jpeg")
    private val VANILLA_TITLE_BUTTONS = setOf(
        "menu.singleplayer", "menu.multiplayer", "menu.online", "menu.options", "menu.quit",
        "menu.playdemo", "menu.resetdemo", "menu.test", "options.language", "options.accessibility",
        "accessibility.onboarding.accessibility.button", "gui.friends.open", "title.credits",
    )
    private val backgroundFolder = File(mc.gameDirectory, "config/quoi!/images").apply { mkdirs() }

    val backgroundImage: SelectorComponent<String> by selector(
        "Background image",
        VANILLA_BACKGROUND,
        listOf(VANILLA_BACKGROUND),
        desc = "Images from config/quoi!/images."
    )
    val autoMenuColour by switch("Auto menu colour", true, desc = "Picks the menu colour from the custom background image.")
    val menuColour by colourPicker(
        "Menu colour",
        Colour.RGB(255, 204, 134),
        desc = "Accent colour for custom main menu buttons and overlays."
    )
    @Suppress("unused")
    private val openBackgroundsFolder by button("Open backgrounds folder") {
        runCatching { Util.getPlatform().openPath(backgroundFolder.toPath()) }
    }
    val dimAmount by slider("Background dim", 0.2f, 0f, 0.95f, 0.05f)
    val showHypixelButton by switch("Hypixel button", true, desc = "Shows a quick join button for Hypixel.")
    val showP3SimButton by switch("P3Sim button", true, desc = "Shows a quick join button for p3sim.net.")

    init {
        refreshBackgroundOptions()
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            if (!enabled) return@register
            refreshBackgroundOptions()
            if (screen !is TitleScreen) return@register

            // Wait for every mod's initialization before collecting buttons and replacing the screen.
            mc.schedule {
                if (enabled && mc.gui.screen() === screen) {
                    val buttons = Screens.getWidgets(screen).filterIsInstance<AbstractButton>().filter(::isExternalTitleButton)
                    mc.gui.setScreen(CustomMainMenuScreen(buttons))
                }
            }
        }
    }

    fun selectedBackgroundFile(): File? = backgroundImage.selected
        .takeUnless { it == VANILLA_BACKGROUND }
        ?.let { File(backgroundFolder, it).takeIf(File::isFile) }

    private fun refreshBackgroundOptions() {
        val previous = backgroundImage.selected
        val files = backgroundFolder.listFiles().orEmpty()
            .filter { it.isFile && it.extension.lowercase() in SUPPORTED_BACKGROUND_EXTENSIONS }
            .map { it.name }
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
        backgroundImage.options = listOf(VANILLA_BACKGROUND) + files
        backgroundImage.selected = previous
    }

    private fun isExternalTitleButton(button: AbstractButton): Boolean =
        button.visible && button.message.string.noControlCodes.isNotBlank() &&
            (button.message.contents as? TranslatableContents)?.key !in VANILLA_TITLE_BUTTONS
}
