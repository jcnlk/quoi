package quoi.api.customtriggers.actions

import quoi.api.abobaui.elements.ElementScope
import quoi.api.customtriggers.*
import quoi.config.TypeName
import quoi.utils.skyblock.player.WardrobeUtils

@TypeName("wardrobe")
class WardrobeAction(
    var slot: Int = 1,
    var preventMove: Boolean = true,
    var blockInput: Boolean = false,
    var fastMode: Boolean = false,
    var disableUnequip: Boolean = true,
) : TriggerAction {
    override fun validationError() = if (slot !in 1..9) "Wardrobe slot must be between 1 and 9." else null

    override fun execute(ctx: TriggerContext) {
        check(WardrobeUtils.equip(slot, preventMove = preventMove, blockInput = blockInput, fastMode = fastMode, disableUnequip = disableUnequip)) {
            "Could not start wardrobe swap"
        }
    }

    override fun displayString() = "Equip wardrobe slot $slot"

    override fun ElementScope<*>.draw() = settingRow(
        { intSliderField("Slot", ::slot, 1, 9) },
        { toggleField("Block inputs", ::blockInput) },
        { toggleField("Fast mode", ::fastMode) },
        { toggleField("Prevent moving", ::preventMove) },
        { toggleField("Disable unequip", ::disableUnequip) },
    )
}
