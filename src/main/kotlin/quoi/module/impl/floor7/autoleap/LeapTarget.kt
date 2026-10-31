package quoi.module.impl.floor7.autoleap

import quoi.api.skyblock.dungeon.Dungeon.allTeammatesNoSelf
import quoi.api.skyblock.dungeon.enums.DungeonClass
import quoi.module.settings.Setting
import quoi.module.settings.Setting.Companion.json
import quoi.module.settings.SettingsDSL
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.UIComponent.Companion.visibleIf
import quoi.module.settings.impl.SwitchComponent

class LeapTarget(
    private val group: LeapSettings,
    name: String,
    desc: String,
    autoDesc: String? = null,
    defaultName: String = "",
    parent: SwitchComponent? = null,
    private val nameOverride: () -> String? = { null },
    val matches: () -> Boolean = { true },
) : SettingsDSL() {
    private val prefix = "${group.component.jsonName}.$name"
    val toggle = parent ?: +switch(name, desc = if (group.automatic) autoDesc ?: desc else desc).json(prefix)
    private val label = if (parent == null) "Target" else name
    private val targetName by textInput(label, defaultName, length = 16, desc = "Target player name. Leave empty to skip.")
        .json("$prefix.Name").childOf(toggle).visibleIf { Leaps.byName }.suggests { allTeammatesNoSelf }
    private val targetClass by selector(label, DungeonClass.Unknown, desc = "Target class. Unknown skips this target.")
        .json("$prefix.Class").childOf(toggle).visibleIf { !Leaps.byName }

    val enabled get() = toggle.value

    override fun <K : Setting<T>, T> register(setting: K): K = group.register(setting)

    fun leap(): Boolean {
        if (!enabled || !matches()) return false
        val name = nameOverride() ?: targetName.takeIf { Leaps.byName }
        if (name != null) {
            if (name.isBlank()) return false
            Leaps.leap(name)
        } else {
            if (targetClass.selected == DungeonClass.Unknown) return false
            Leaps.leap(targetClass.selected)
        }
        return true
    }
}
