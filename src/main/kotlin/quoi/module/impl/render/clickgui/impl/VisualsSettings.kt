package quoi.module.impl.render.clickgui.impl

import quoi.api.colour.Colour
import quoi.module.Module
import quoi.module.impl.render.clickgui.ClickGui
import quoi.module.impl.render.clickgui.ClickGui.reopen
import quoi.module.settings.group.SettingGroup
import quoi.module.settings.impl.SelectorComponent
import quoi.utils.ui.rendering.NVGRenderer

object VisualsSettings : SettingGroup(
    ClickGui,
    SelectorComponent("Visuals", "Dark", arrayListOf("Light", "Dark", "Onyx"))//.open()
        .onValueChanged { _, _ ->
            reopen()
        }
) {
    val selectedTheme get() = (component as SelectorComponent<*>).selected
    val colour by colourPicker("Colour", Colour.CYAN).asParent()
    val moduleSorting by selector("Module sorting", ModuleSorting.Longest).onValueChanged { _, _ -> reopen() }

    var rainbowSpeed by slider("Rainbow colour speed", 0.5f, 0.05f, 5.0f, 0.05f)

    @Suppress("unused")
    enum class ModuleSorting(val comparator: Comparator<Module>) {
        Longest(compareByDescending<Module> { NVGRenderer.textWidth(it.name, 18f, NVGRenderer.defaultFont) }.thenBy { it.name.lowercase() }),
        Shortest(compareBy<Module> { NVGRenderer.textWidth(it.name, 18f, NVGRenderer.defaultFont) }.thenBy { it.name.lowercase() }),
        Alphabetical(compareBy<Module> { it.name.lowercase() });
    }
}