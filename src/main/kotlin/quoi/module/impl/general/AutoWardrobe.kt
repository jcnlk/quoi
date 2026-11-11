package quoi.module.impl.general

import quoi.api.input.Keybinds
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.impl.KeybindComponent
import quoi.utils.ChatUtils.modMessage
import quoi.utils.skyblock.player.container.ContainerSettings
import quoi.utils.skyblock.player.container.task.containerTask
import quoi.utils.skyblock.player.container.task.presets.equipWardrobe

object AutoWardrobe : Module(
    "Auto Wardrobe",
    desc = "Automatically equips wardrobe slots through /quoi wardrobe."
) {
    private val disableUnequip by switch("Disable unequip", desc = "Prevents clicking the currently equipped wardrobe slot.")
    private val containerSettings = ContainerSettings(this)
    private val keybinds by text("Keybinds")

    @Suppress("unused")
    private val wardrobeKeys = (1..9).map { i ->
        register(
            KeybindComponent("Slot $i", Keybinds.KEY_NONE, "Equips wardrobe slot $i.")
                .childOf(::keybinds)
                .onPress { onWardrobeKey(i) }
        )
    }

    private fun onWardrobeKey(slot: Int) {
        if (!enabled || mc.gui.screen() != null) return
        containerTask(name = "Wardrobe $slot", settings = containerSettings) {
            equipWardrobe(slot, toggle = !disableUnequip)
            onComplete { modMessage("&aWardrobe slot &f$slot &equipped.") }
        }.run()
    }
}
