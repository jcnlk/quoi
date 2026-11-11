package quoi.api.customtriggers.actions

import quoi.api.abobaui.elements.ElementScope
import quoi.api.customtriggers.*
import quoi.config.TypeName
import quoi.utils.skyblock.player.container.CONTAINER_MENU
import quoi.utils.skyblock.player.container.IContainerSettings
import quoi.utils.skyblock.player.container.task.containerTask
import quoi.utils.skyblock.player.container.task.presets.equipPet

@TypeName("pet")
class PetAction(
    var name: String = "",
    var item: String = "",
    var blockInput: Boolean = false,
) : TriggerAction, ContainerTaskAction {
    override fun validationError() = if (name.isBlank()) "Enter a pet name." else null

    override fun execute(ctx: TriggerContext) {
        createTask(ctx).run()
    }

    override fun createTask(ctx: TriggerContext) =
        containerTask(
            name = "Pet: ${ctx.expand(name).trim()}",
            reportErrors = false,
            settings = object : IContainerSettings by CONTAINER_MENU {
                override val blockInput = this@PetAction.blockInput
            },
        ) {
            equipPet(ctx.expand(name).trim(), heldItem = ctx.expand(item).trim().takeIf { it.isNotEmpty() })
        }

    override fun displayString() = "Equip pet: $name"

    override fun ElementScope<*>.draw() = settingRow(
        { textField("Pet name", name) { name = it } },
        { textField("Held item (optional)", item) { item = it } },
        { toggleField("Block inputs", ::blockInput) },
    )
}
