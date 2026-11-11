package quoi.api.customtriggers.actions

import quoi.api.abobaui.elements.ElementScope
import quoi.api.customtriggers.*
import quoi.config.TypeName
import quoi.utils.skyblock.player.container.CONTAINER_MENU
import quoi.utils.skyblock.player.container.IContainerSettings
import quoi.utils.skyblock.player.container.task.containerTask
import quoi.utils.skyblock.player.container.task.presets.equipItem

@TypeName("equip")
class EquipAction(
    var name: String = "",
    var blockInput: Boolean = false,
) : TriggerAction, ContainerTaskAction {
    override fun validationError() = if (name.isBlank()) "Enter an equipment name." else null

    override fun execute(ctx: TriggerContext) {
        createTask(ctx).run()
    }

    override fun createTask(ctx: TriggerContext) =
        containerTask(
            name = "Equipment",
            reportErrors = false,
            settings = object : IContainerSettings by CONTAINER_MENU {
                override val blockInput = this@EquipAction.blockInput
            },
        ) {
            equipItem(ctx.expand(name).trim())
        }

    override fun displayString() = "Equip: $name"

    override fun ElementScope<*>.draw() = settingRow(
        { textField("Equipment name", name) { name = it } },
        { toggleField("Block inputs", ::blockInput) },
    )
}
