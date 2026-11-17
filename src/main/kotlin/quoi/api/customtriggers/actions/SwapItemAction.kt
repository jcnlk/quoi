package quoi.api.customtriggers.actions

import quoi.api.abobaui.constraints.impl.size.Copying
import quoi.api.abobaui.dsl.*
import quoi.api.abobaui.elements.ElementScope
import quoi.api.abobaui.elements.impl.refreshableGroup
import quoi.api.customtriggers.*
import quoi.config.TypeName
import quoi.utils.skyblock.player.SwapManager
import quoi.utils.skyblock.player.SwapResult

@TypeName("swap_item")
class SwapItemAction(
    var field: Field = Field.SKYBLOCK_ID,
    var item: String = "",
    var slot: Int = 1,
) : TriggerAction {
    enum class Field(val label: String) {
        SKYBLOCK_ID("SkyBlock ID"), NAME("Item name"), LORE("Item lore"), SLOT("Hotbar slot")
    }

    override fun validationError() = when {
        field == Field.SLOT && slot !in 1..9 -> "Hotbar slot must be between 1 and 9."
        field != Field.SLOT && item.isBlank() -> "Enter an item to select."
        else -> null
    }

    override fun execute(ctx: TriggerContext) {
        val query = ctx.expand(item).trim()
        check(field == Field.SLOT || query.isNotBlank()) { "Enter an item to select" }
        val result = when (field) {
            Field.SKYBLOCK_ID -> SwapManager.swapById(query)
            Field.NAME -> SwapManager.swapByName(query)
            Field.LORE -> SwapManager.swapByLore(query)
            Field.SLOT -> SwapManager.swapToSlot(slot - 1)
        }
        when (result) {
            SwapResult.SUCCESS, SwapResult.ALREADY_SELECTED -> Unit
            SwapResult.TOO_FAST -> error("Cannot swap twice in one tick. Add a Wait action before swapping again")
            SwapResult.NOT_FOUND -> error("Could not find '$query' in the hotbar")
            SwapResult.FAILED -> error("Could not select hotbar slot $slot")
        }
    }

    override fun displayString() = if (field == Field.SLOT) "Select hotbar slot $slot" else "Swap item: $item"

    override fun ElementScope<*>.draw() = column(size(w = Copying)) {
        refreshableGroup(size(w = Copying)) {
            val fields = element
            settingRow(
                {
                    choiceField("Select by", { field }, Field.entries, { it.label }) {
                        ui.unfocus()
                        field = it
                        fields.refresh()
                    }
                },
                {
                    if (field == Field.SLOT) intField("Slot (1-9)", { slot }, 1, 9) { slot = it }
                    else textField(field.label, item) { item = it }
                },
            )
        }
    }
}
