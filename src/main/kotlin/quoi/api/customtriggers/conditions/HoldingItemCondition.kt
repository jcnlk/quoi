package quoi.api.customtriggers.conditions

import quoi.api.abobaui.constraints.impl.size.Copying
import quoi.api.abobaui.dsl.*
import quoi.api.abobaui.elements.ElementScope
import quoi.api.customtriggers.*
import quoi.config.TypeName
import quoi.QuoiMod.mc
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.skyblock.item.ItemUtils.skyblockId

@TypeName("holding_item")
class HoldingItemCondition(
    var field: Field = Field.NAME,
    var matcher: TextMatcher = TextMatcher()
) : TriggerCondition {
    enum class Field(val label: String) { NAME("Item name"), SKYBLOCK_ID("SkyBlock ID") }
    override fun validationError() = matcher.validationError()
    fun matchesItem(name: String, id: String) = matcher.matches(if (field == Field.NAME) name else id)
    override fun matches(ctx: TriggerContext): Boolean {
        val stack = mc.player?.mainHandItem ?: return false
        return !stack.isEmpty && matchesItem(stack.hoverName.string.noControlCodes, stack.skyblockId.orEmpty())
    }
    override fun displayString() = "Holding ${matcher.pattern}"
    override fun ElementScope<*>.draw() = column(size(w = Copying), gap = 8.px) {
        fieldRow(
            { choiceField("Compare", { field }, Field.entries, { it.label }) { field = it } },
            { choiceField("Match", { matcher.mode }, TextMatchMode.entries, { if (it == TextMatchMode.INCLUDES) "Contains" else it.label }) { matcher.mode = it } }
        )
        textField("Value", matcher.pattern) { matcher.pattern = it }
    }
}
