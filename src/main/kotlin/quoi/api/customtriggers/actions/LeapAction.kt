package quoi.api.customtriggers.actions

import quoi.api.abobaui.constraints.impl.size.Copying
import quoi.api.abobaui.dsl.*
import quoi.api.abobaui.elements.impl.refreshableGroup
import quoi.api.abobaui.elements.ElementScope
import quoi.api.customtriggers.*
import quoi.config.TypeName
import quoi.api.skyblock.dungeon.enums.DungeonClass
import quoi.utils.skyblock.player.LeapManager

@TypeName("leap")
class LeapAction(
    var name: String = "",
    var dungeonClass: DungeonClass = DungeonClass.Unknown,
    var blockInput: Boolean = false,
    var fastMode: Boolean = false,
    var swapBack: Boolean = false,
) : TriggerAction {
    override fun validationError() =
        if (dungeonClass == DungeonClass.Unknown && name.isBlank()) "Enter a player name or select a class." else null

    override fun execute(ctx: TriggerContext) {
        if (dungeonClass == DungeonClass.Unknown) {
            LeapManager.leap(ctx.expand(name).trim(), blockInput = blockInput, fastMode = fastMode, swapBack = swapBack)
        } else {
            LeapManager.leap(dungeonClass, blockInput = blockInput, fastMode = fastMode, swapBack = swapBack)
        }
    }

    override fun displayString() = "Leap to ${if (dungeonClass == DungeonClass.Unknown) name else dungeonClass.name}"

    override fun ElementScope<*>.draw() = column(size(w = Copying), gap = 8.px) {
        var selectedClass = dungeonClass.takeUnless { it == DungeonClass.Unknown } ?: DungeonClass.Mage
        refreshableGroup(size(w = Copying)) {
            val targetFields = element
            settingRow(
                {
                    choiceField("Target by", { dungeonClass != DungeonClass.Unknown }, listOf(false, true), { if (it) "Class" else "Name" }) { byClass ->
                        ui.unfocus()
                        dungeonClass = if (byClass) selectedClass else DungeonClass.Unknown
                        targetFields.refresh()
                    }
                },
                {
                    if (dungeonClass == DungeonClass.Unknown) {
                        textField("Player name", name) { name = it }
                    } else {
                        choiceField("Class", { dungeonClass }, DungeonClass.entries.filter { it != DungeonClass.Unknown }) {
                            dungeonClass = it
                            selectedClass = it
                        }
                    }
                },
                { toggleField("Block inputs", ::blockInput) },
                { toggleField("Fast mode", ::fastMode) },
                { toggleField("Swap back", ::swapBack) },
            )
        }
    }
}
