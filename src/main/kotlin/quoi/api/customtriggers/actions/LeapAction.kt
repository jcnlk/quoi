package quoi.api.customtriggers.actions

import quoi.api.abobaui.constraints.impl.size.Copying
import quoi.api.abobaui.dsl.*
import quoi.api.abobaui.elements.impl.refreshableGroup
import quoi.api.abobaui.elements.ElementScope
import quoi.api.customtriggers.*
import quoi.config.TypeName
import quoi.api.skyblock.dungeon.enums.DungeonClass
import quoi.utils.skyblock.player.LeapManager
import quoi.utils.skyblock.player.container.CONTAINER_MENU
import quoi.utils.skyblock.player.container.IContainerSettings

@TypeName("leap")
class LeapAction(
    var name: String = "",
    var dungeonClass: DungeonClass = DungeonClass.Unknown,
    var blockInput: Boolean = false,
    var swapBack: Boolean = false,
) : TriggerAction {
    override fun validationError() =
        if (dungeonClass == DungeonClass.Unknown && name.isBlank()) "Enter a player name or select a class." else null

    override fun execute(ctx: TriggerContext) {
        val settings = object : IContainerSettings by CONTAINER_MENU {
            override val blockInput = this@LeapAction.blockInput
        }
        if (dungeonClass == DungeonClass.Unknown) {
            LeapManager.leap(ctx.expand(name).trim(), settings = settings, swapBack = swapBack)
        } else {
            LeapManager.leap(dungeonClass, settings = settings, swapBack = swapBack)
        }
    }

    override fun displayString() = "Leap to ${if (dungeonClass == DungeonClass.Unknown) name else dungeonClass.name}"

    override fun ElementScope<*>.draw() = column(size(w = Copying), gap = 8.px) {
        var selectedClass = dungeonClass.takeUnless { it == DungeonClass.Unknown } ?: DungeonClass.Mage
        refreshableGroup(size(w = Copying)) {
            val targetFields = element
            settingRow(
                {
                    choiceField(
                        "Target by",
                        { dungeonClass != DungeonClass.Unknown },
                        listOf(false, true),
                        { if (it) "Class" else "Name" }) { byClass ->
                        ui.unfocus()
                        dungeonClass = if (byClass) selectedClass else DungeonClass.Unknown
                        targetFields.refresh()
                    }
                },
                {
                    if (dungeonClass == DungeonClass.Unknown) {
                        textField("Player name", name) { name = it }
                    } else {
                        choiceField(
                            "Class",
                            { dungeonClass },
                            DungeonClass.entries.filter { it != DungeonClass.Unknown }) {
                            dungeonClass = it
                            selectedClass = it
                        }
                    }
                },
                { toggleField("Block inputs", ::blockInput) },
                { toggleField("Swap back", ::swapBack) },
            )
        }
    }
}
