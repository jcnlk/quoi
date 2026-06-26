package quoi.module.impl.render.clickgui.impl

import quoi.api.colour.Colour
import quoi.module.Module
import quoi.module.impl.render.clickgui.ClickGui
import quoi.module.impl.render.clickgui.ClickGui.reopen
import quoi.module.settings.group.SettingGroup
import quoi.module.settings.impl.SelectorComponent
import quoi.utils.ui.rendering.UIRenderer

object VisualsSettings : SettingGroup(
    ClickGui,
    SelectorComponent("Visuals", "Light", arrayListOf("Light", "Dark", "Onyx"))//.open()
        .onValueChanged { _, _ ->
            reopen()
        }
) {
    val selectedTheme get() = (parent as SelectorComponent<*>).selected
    val colour by colourPicker("Colour", Colour.RGB(255, 204, 134)).asParent()
    val classColors by selector(
        "Class colors",
        "Hypixel Style",
        listOf("Hypixel Style", "Noamm Style"),
        desc = "Blame Noamm for this"
    )
    val moduleSorting by selector("Module sorting", ModuleSorting.Alphabetical).onValueChanged { _, _ -> reopen() }

    var rainbowSpeed by slider("Rainbow colour speed", 0.5f, 0.05f, 5.0f, 0.05f)


    @Suppress("unused")
    enum class ModuleSorting(
        val comparator: Comparator<Module>
    ) {
        WidthDescending(
            compareByDescending<Module> { UIRenderer.textWidth(it.name, 18f, UIRenderer.defaultFont) }.thenBy { it.name.lowercase() }
        ),

        WidthAscending(
            compareBy<Module> { UIRenderer.textWidth(it.name, 18f, UIRenderer.defaultFont) }.thenBy { it.name.lowercase() }
        ),

        Alphabetical(
            compareBy<Module> { it.name.lowercase() }
        );
    }
}
