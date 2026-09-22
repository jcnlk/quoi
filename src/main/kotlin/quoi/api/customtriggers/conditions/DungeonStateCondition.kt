package quoi.api.customtriggers.conditions

import quoi.api.abobaui.elements.ElementScope
import quoi.api.customtriggers.TriggerContext
import quoi.api.customtriggers.choiceField
import quoi.api.skyblock.dungeon.Dungeon
import quoi.config.TypeName

@TypeName("dungeon_state")
class DungeonStateCondition(var state: State = State.IN_DUNGEON) : TriggerCondition {
    enum class State(val label: String) {
        IN_DUNGEON("In dungeon"), CLEAR("In clear"), BOSS("In boss"), TERMINAL("In terminal"), DEAD("Player is dead")
    }
    override fun matches(ctx: TriggerContext) = when (state) {
        State.IN_DUNGEON -> Dungeon.inDungeons
        State.CLEAR -> Dungeon.inClear
        State.BOSS -> Dungeon.inBoss
        State.TERMINAL -> Dungeon.inTerminal
        State.DEAD -> Dungeon.inDungeons && Dungeon.isDead
    }
    override fun displayString() = state.label
    override fun ElementScope<*>.draw() = choiceField("State", { state }, State.entries, { it.label }) { state = it }
}
