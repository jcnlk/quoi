package quoi.module.impl.general

import quoi.api.input.CatKeys
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.impl.KeybindComponent
import quoi.utils.skyblock.player.WardrobeUtils

object AutoWardrobe : Module(
    "Auto Wardrobe",
    desc = "Automatically equips wardrobe slots through /quoi wardrobe."
) {
    private val disableUnequip by switch("Disable unequip", desc = "Prevents clicking the currently equipped wardrobe slot.")
    private val preventMoving by switch("Prevent moving", desc = "Stops your movement while a wardrobe equip is in progress.")
    private val blockInputs by switch("Block inputs", desc = "Blocks keyboard and mouse input while a wardrobe equip is in progress.")
    private val fastMode by switch("Fast mode", desc = "Blocks movement and input only from the menu opening until the target click.")
    private val keybinds by text("Keybinds")
    private val wardrobeKeys = (1..9).map { i ->
        register(
            KeybindComponent("Slot $i", CatKeys.KEY_NONE, "Equips wardrobe slot $i.")
                .childOf(::keybinds)
                .onPress { onWardrobeKey(i) }
        )
    }
    private fun onWardrobeKey(slot: Int) {
        if (!enabled || mc.screen != null) return
        WardrobeUtils.equip(
            slot,
            preventMove = preventMoving,
            blockInput = blockInputs,
            disableUnequip = disableUnequip,
            fastMode = fastMode,
        )
    }
}
