package quoi.api.customtriggers.actions

import quoi.api.abobaui.elements.ElementScope
import quoi.api.customtriggers.*
import quoi.config.TypeName
import quoi.utils.skyblock.player.container.CONTAINER_MENU
import quoi.utils.skyblock.player.container.IContainerSettings
import quoi.utils.skyblock.player.container.task.containerTask
import quoi.utils.skyblock.player.container.task.presets.equipLoadout

@TypeName("loadout")
class LoadoutAction(
    var slot: Int = 1,
    var preventMove: Boolean = true,
    var blockInput: Boolean = false,
) : TriggerAction, ContainerTaskAction {
    override fun validationError() = if (slot !in 1..12) "Loadout slot must be between 1 and 12." else null

    override fun execute(ctx: TriggerContext) {
        createTask(ctx).run()
    }

    override fun createTask(ctx: TriggerContext) =
        containerTask(
            name = "Loadout $slot",
            reportErrors = false,
            settings = object : IContainerSettings by CONTAINER_MENU {
                override val invWalk = !preventMove
                override val blockInput = this@LoadoutAction.blockInput
            },
        ) {
            equipLoadout(slot)
        }

    override fun displayString() = "Equip loadout slot $slot"

    override fun ElementScope<*>.draw() = settingRow(
        { intSliderField("Slot", ::slot, 1, 12) },
        { toggleField("Block inputs", ::blockInput) },
        { toggleField("Prevent moving", ::preventMove) },
    )
}
