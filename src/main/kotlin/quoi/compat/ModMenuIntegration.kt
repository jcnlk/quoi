package quoi.compat

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import quoi.module.impl.render.ClickGui
import quoi.utils.ui.screens.UIScreen

@Suppress("unused")
class ModMenuIntegration : ModMenuApi {
    override fun getModConfigScreenFactory(): ConfigScreenFactory<*> {
        return ConfigScreenFactory { UIScreen(ClickGui.clickGui) }
    }
}
