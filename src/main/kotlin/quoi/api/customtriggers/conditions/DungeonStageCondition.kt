package quoi.api.customtriggers.conditions

import quoi.api.abobaui.elements.ElementScope
import quoi.api.customtriggers.TriggerContext
import quoi.api.customtriggers.choiceField
import quoi.api.customtriggers.settingRow
import quoi.api.skyblock.dungeon.Floor7
import quoi.api.skyblock.dungeon.enums.Stage
import quoi.config.TypeName

@TypeName("dungeon_stage")
class DungeonStageCondition(var stage: Stage = Stage.S1) : TriggerCondition() {
    var mode: DungeonCheckMode = DungeonCheckMode.STATE

    override fun matches(ctx: TriggerContext) = when (mode) {
        DungeonCheckMode.STATE -> Floor7.inStage(stage)
        DungeonCheckMode.POSITION -> Floor7.getStageAt() == stage
    }

    override fun displayString() = "${mode.label}: stage ${stage.name}"
    override fun invertedString() = "${mode.label}: not stage ${stage.name}"
    override fun ElementScope<*>.draw() = settingRow(
        { choiceField("Check", { mode }, DungeonCheckMode.entries, { it.label }) { mode = it } },
        { choiceField("Stage", { stage }, Stage.entries.filter { it != Stage.Unknown }) { stage = it } }
    )
}
