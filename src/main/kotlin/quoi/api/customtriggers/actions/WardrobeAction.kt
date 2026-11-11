package quoi.api.customtriggers.actions

import quoi.api.abobaui.elements.ElementScope
import quoi.api.customtriggers.*
import quoi.config.TypeName
import quoi.utils.skyblock.player.container.CONTAINER_MENU
import quoi.utils.skyblock.player.container.IContainerSettings
import quoi.utils.skyblock.player.container.task.containerTask
import quoi.utils.skyblock.player.container.task.presets.equipWardrobe

@TypeName("wardrobe")
class WardrobeAction(
    var slot: Int = 1,
    var preventMove: Boolean = true,
    var blockInput: Boolean = false,
    var disableUnequip: Boolean = true,
) : TriggerAction, ContainerTaskAction {
    override fun validationError() = if (slot !in 1..9) "Wardrobe slot must be between 1 and 9." else null

    override fun execute(ctx: TriggerContext) {
        createTask(ctx).run()
    }

    override fun createTask(ctx: TriggerContext) =
        containerTask(
            name = "Wardrobe $slot",
            reportErrors = false,
            settings = object : IContainerSettings by CONTAINER_MENU {
                override val invWalk = !preventMove
                override val blockInput = this@WardrobeAction.blockInput
            },
        ) {
            equipWardrobe(slot, toggle = !disableUnequip)
        }

    override fun displayString() = "Equip wardrobe slot $slot"

    override fun ElementScope<*>.draw() = settingRow(
        { intSliderField("Slot", ::slot, 1, 9) },
        { toggleField("Block inputs", ::blockInput) },
        { toggleField("Prevent moving", ::preventMove) },
        { toggleField("Disable unequip", ::disableUnequip) },
    )
}
