package quoi.module.impl.render

import quoi.api.events.core.on
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.screens.TitleScreen
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.events.GuiEvent
import quoi.api.events.TickEvent
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.impl.SelectorComponent
import quoi.ui.CustomMainMenuScreen
import quoi.utils.StringUtils.noControlCodes
import java.io.File
import net.minecraft.util.Util

object CustomMainMenu : Module(
    "Custom Main Menu",
    desc = "Replaces the vanilla main menu with a Quoi-styled one."
) {
    private const val VANILLA_BACKGROUND = "Vanilla"
    private val SUPPORTED_BACKGROUND_EXTENSIONS = setOf("png", "jpg", "jpeg")
    private val NON_CUSTOM_TITLE_BUTTONS = setOf(
        "settings",
        "setting",
        "options",
        "option",
        "config",
        "configuration",
        "configure",
        "mod settings",
        "mod options",
    )
    private val VANILLA_TITLE_BUTTONS = setOf(
        "Singleplayer",
        "Multiplayer",
        "Minecraft Realms",
        "Options...",
        "Options",
        "Quit Game",
        "Quit",
        "Language",
        "Accessibility",
        "Copyright Mojang AB. Do not distribute!",
    )

    val backgroundFolder = File(mc.gameDirectory, "config/quoi!/images").apply { mkdirs() }
    private val backgroundOptions = mutableListOf(VANILLA_BACKGROUND)
    private var backgroundSnapshot = emptyList<String>()
    private var refreshTicks = 0

    val backgroundImage: SelectorComponent<String> by selector(
        "Background image",
        VANILLA_BACKGROUND,
        backgroundOptions,
        desc = "Images from config/quoi!/images."
    )
    val autoMenuColour by switch("Auto menu colour", true, desc = "Picks the menu colour from the custom background image.")
    val menuColour by colourPicker(
        "Menu colour",
        Colour.RGB(255, 204, 134).withAlpha(255),
        desc = "Accent colour for custom main menu buttons and overlays."
    )
    private val openBackgroundsFolder by button("Open backgrounds folder") {
        runCatching { Util.getPlatform().openPath(backgroundFolder.toPath()) }
    }
    val dimAmount by slider("Background dim", 0.2f, 0f, 0.95f, 0.05f)
    val showHypixelButton by switch("Hypixel button", true, desc = "Shows a quick join button for Hypixel.")
    val showP3SimButton by switch("P3Sim button", true, desc = "Shows a quick join button for p3sim.net.")
    val useEuP3SimServer by switch("Use EU server", false, desc = "Uses eu.p3sim.net for the P3Sim quick join button.")
        .childOf(::showP3SimButton)

    init {
        refreshBackgroundOptions(force = true)

        on<TickEvent.End> {
            if (++refreshTicks >= 20) {
                refreshTicks = 0
                refreshBackgroundOptions()
            }
        }

        on<GuiEvent.Draw> {
            if (screen !is TitleScreen) return@on

            cancel()
            if (mc.gui.screen() !is CustomMainMenuScreen) mc.gui.setScreen(CustomMainMenuScreen(emptyList()))
        }
    }

    fun selectedBackgroundFile(): File? {
        refreshBackgroundOptions()
        val selected = backgroundImage.selected
        if (selected == VANILLA_BACKGROUND) return null
        return File(backgroundFolder, selected).takeIf { it.isFile }
    }

    private fun refreshBackgroundOptions(force: Boolean = false) {
        val files = backgroundFolder.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in SUPPORTED_BACKGROUND_EXTENSIONS }
            ?.map { it.name }
            ?.sortedWith(String.CASE_INSENSITIVE_ORDER)
            ?: emptyList()
        if (!force && files == backgroundSnapshot) return

        val previous = backgroundImage.selected
        backgroundSnapshot = files
        backgroundOptions.clear()
        backgroundOptions += VANILLA_BACKGROUND
        backgroundOptions += files
        backgroundImage.options = backgroundOptions
        backgroundImage.selected = if (previous in backgroundOptions) previous else VANILLA_BACKGROUND
    }

    private fun isExternalTitleButton(widget: AbstractWidget): Boolean {
        val label = widget.message.string.noControlCodes.trim()
        val normalised = label.lowercase()
        if (label.isBlank()) return false
        if (widget.width < 80) return false
        if (normalised.contains("language") || normalised.contains("accessibility")) return false
        if (normalised in NON_CUSTOM_TITLE_BUTTONS) return false
        return label !in VANILLA_TITLE_BUTTONS
    }

}
