package quoi.module.impl.general

import quoi.api.input.Keybinds
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.impl.KeybindComponent
import quoi.utils.ChatUtils.modMessage
import quoi.utils.skyblock.player.container.ContainerSettings
import quoi.utils.skyblock.player.container.task.containerTask
import quoi.utils.skyblock.player.container.task.presets.equipLoadout

object AutoLoadout : Module(
    "Auto Loadout",
) {
    private val containerSettings = ContainerSettings(this)
    private val keybinds by text("Keybinds")

    @Suppress("unused")
    private val loadoutKeys = (1..12).map { i ->
        register(
            KeybindComponent("Slot $i", Keybinds.KEY_NONE, "Equips loadout slot $i.")
                .childOf(::keybinds)
                .onPress { onLoadoutKey(i) }
        )
    }

    private fun onLoadoutKey(slot: Int) {
        if (!enabled || mc.gui.screen() != null) return
        containerTask(name = "Loadout $slot", settings = containerSettings) {
            equipLoadout(slot)
            onComplete { modMessage("&aEquipped loadout &f$slot") }
        }.run()
    }
}
