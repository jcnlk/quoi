package quoi.module.impl.misc

import quoi.api.input.CatKeys
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.impl.KeybindComponent
import quoi.utils.skyblock.player.LoadoutUtils

object AutoLoadout : Module(
    "Auto Loadout",
) {
    private val preventMoving by switch("Prevent moving", desc = "Stops your movement while a loadout equip is in progress.")
    private val blockInputs by switch("Block inputs", desc = "Blocks keyboard and mouse input while a loadout equip is in progress.")
    private val fastMode by switch("Fast mode", desc = "Blocks movement and input only from the menu opening until the target click.")
    private val keybinds by text("Keybinds")
    @Suppress("unused")
    private val loadoutKeys = (1..12).map { i ->
        register(
            KeybindComponent("Slot $i", CatKeys.KEY_NONE, "Equips loadout slot $i.")
                .childOf(::keybinds)
                .onPress { onLoadoutKey(i) }
        )
    }

    private fun onLoadoutKey(slot: Int) {
        if (!enabled || mc.gui.screen() != null) return
        LoadoutUtils.equip(
            slot,
            preventMove = preventMoving,
            blockInputs = blockInputs,
            fastMode = fastMode,
        )
    }
}
