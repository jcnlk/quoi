package quoi.api.customtriggers.conditions

import quoi.api.abobaui.elements.ElementScope
import quoi.api.customtriggers.TriggerContext
import quoi.api.customtriggers.choiceField
import quoi.api.customtriggers.settingRow
import quoi.api.skyblock.dungeon.Floor7
import quoi.api.skyblock.dungeon.enums.Phase
import quoi.config.TypeName

@TypeName("dungeon_phase")
class DungeonPhaseCondition(var phase: Phase = Phase.P3) : TriggerCondition() {
    var mode: DungeonCheckMode = DungeonCheckMode.STATE

    override fun matches(ctx: TriggerContext) = when (mode) {
        DungeonCheckMode.STATE -> Floor7.inPhase(phase)
        DungeonCheckMode.POSITION -> Floor7.inPhaseAt(phase)
    }

    override fun displayString() = "${mode.label}: phase ${phase.name}"
    override fun invertedString() = "${mode.label}: not phase ${phase.name}"
    override fun ElementScope<*>.draw() = settingRow(
        { choiceField("Check", { mode }, DungeonCheckMode.entries, { it.label }) { mode = it } },
        { choiceField("Phase", { phase }, Phase.entries.filter { it != Phase.Unknown }) { phase = it } }
    )
}
