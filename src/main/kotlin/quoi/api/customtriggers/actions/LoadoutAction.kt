package quoi.api.customtriggers.actions

import quoi.api.abobaui.elements.ElementScope
import quoi.api.customtriggers.*
import quoi.config.TypeName
import quoi.utils.skyblock.player.LoadoutSwapper

@TypeName("loadout")
class LoadoutAction(
    var slot: Int = 1,
    var preventMove: Boolean = true,
    var blockInput: Boolean = false,
    var fastMode: Boolean = false,
) : TriggerAction {
    override fun validationError() = if (slot !in 1..12) "Loadout slot must be between 1 and 12." else null

    override fun execute(ctx: TriggerContext) {
        check(LoadoutSwapper.equip(slot, preventMove = preventMove, blockInput = blockInput, fastMode = fastMode)) {
            "Could not start loadout swap"
        }
    }

    override fun displayString() = "Equip loadout slot $slot"

    override fun ElementScope<*>.draw() = settingRow(
        { intSliderField("Slot", ::slot, 1, 12) },
        { toggleField("Block inputs", ::blockInput) },
        { toggleField("Fast mode", ::fastMode) },
        { toggleField("Prevent moving", ::preventMove) },
    )
}
