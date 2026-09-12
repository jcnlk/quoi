package quoi.api.customtriggers.actions

import quoi.api.abobaui.elements.ElementScope
import quoi.api.customtriggers.*
import quoi.config.TypeName
import quoi.utils.skyblock.player.PetSwitcher

@TypeName("pet")
class PetAction(
    var name: String = "",
    var item: String = "",
    var blockInput: Boolean = false,
    var fastMode: Boolean = false,
) : TriggerAction {
    override fun validationError() = if (name.isBlank()) "Enter a pet name." else null

    override fun execute(ctx: TriggerContext) {
        check(PetSwitcher.switchPet(
            ctx.expand(name).trim(),
            item = ctx.expand(item).trim().takeIf { it.isNotEmpty() },
            blockInput = blockInput,
            fastMode = fastMode,
        )) { "Could not start pet swap" }
    }

    override fun displayString() = "Equip pet: $name"

    override fun ElementScope<*>.draw() = settingRow(
        { textField("Pet name", name) { name = it } },
        { textField("Held item (optional)", item) { item = it } },
        { toggleField("Block inputs", ::blockInput) },
        { toggleField("Fast mode", ::fastMode) },
    )
}
