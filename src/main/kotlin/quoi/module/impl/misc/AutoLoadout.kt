package quoi.module.impl.misc

import quoi.api.abobaui.dsl.px
import quoi.api.abobaui.elements.impl.Text.Companion.shadow
import quoi.api.abobaui.elements.impl.Text.Companion.textSupplied
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
    private val keybinds by text("Keybinds")
    @Suppress("unused")
    private val loadoutKeys = (1..12).map { i ->
        register(
            KeybindComponent("Slot $i", CatKeys.KEY_NONE, "Equips loadout slot $i.")
                .childOf(::keybinds)
                .onPress { onLoadoutKey(i) }
        )
    }

    @Suppress("unused")
    private val hud by textHud("Loadout hud") {
        visibleIf { this@AutoLoadout.enabled && (preview || LoadoutUtils.isBusy()) }
        column {
            textSupplied(
                supplier = { LoadoutUtils.equippingSlot?.let { "Equipping §7[§c$it§7]" } ?: "Equipping §7[§c1§7]" },
                colour = colour,
                font = font,
                size = 18.px,
            ).shadow = shadow
        }
    }.setting()

    private fun onLoadoutKey(slot: Int) {
        if (!enabled || mc.screen != null) return
        LoadoutUtils.equip(slot, preventMove = preventMoving, blockInputs = blockInputs)
    }
}
