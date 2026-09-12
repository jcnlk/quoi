package quoi.api.customtriggers.conditions

import quoi.api.abobaui.constraints.impl.size.Copying
import quoi.api.abobaui.dsl.*
import quoi.api.abobaui.elements.ElementScope
import quoi.api.customtriggers.*
import quoi.config.TypeName
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.enums.DungeonClass

@TypeName("dungeon_class")
class DungeonClassCondition(var clazz: DungeonClass = DungeonClass.Mage) : TriggerCondition {
    override fun matches(ctx: TriggerContext) = Dungeon.inDungeons && Dungeon.currentDungeonPlayer.clazz == clazz
    override fun displayString() = "Dungeon class: ${clazz.name}"
    override fun ElementScope<*>.draw() = column(size(w = Copying)) {
        choiceField("Class", { clazz }, DungeonClass.entries) { clazz = it }
    }
}
