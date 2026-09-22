package quoi.api.customtriggers.conditions

import quoi.api.abobaui.elements.ElementScope
import quoi.api.customtriggers.TriggerContext
import quoi.api.customtriggers.choiceField
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.enums.Floor
import quoi.config.TypeName

@TypeName("dungeon_floor")
class DungeonFloorCondition(var floor: Floor = Floor.F7) : TriggerCondition {
    override fun matches(ctx: TriggerContext) = Dungeon.inDungeons && Dungeon.floor == floor
    override fun displayString() = "Dungeon floor: ${floor.name}"
    override fun ElementScope<*>.draw() = choiceField("Floor", { floor }, Floor.entries) { floor = it }
}
