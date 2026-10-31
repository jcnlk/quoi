package quoi.module.impl.floor7.autoleap

import com.google.gson.JsonObject
import quoi.api.events.PartyEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.on
import quoi.api.skyblock.dungeon.Dungeon.dungeonTeammatesNoSelf
import quoi.api.skyblock.dungeon.enums.DungeonClass
import quoi.api.skyblock.location.Island
import quoi.config.configMap
import quoi.module.Module
import quoi.module.impl.floor7.autoleap.impl.AutoLeap
import quoi.module.impl.floor7.autoleap.impl.FastLeap
import quoi.module.settings.Saving
import quoi.utils.ChatUtils.modMessage
import quoi.utils.skyblock.player.LeapManager
import quoi.utils.skyblock.player.container.ContainerSettings

object Leaps : Module(
    "Auto Leap",
    desc = "Configure automatic triggers and left-click Spirit Leaps independently.",
    area = Island.Dungeon
) {
    private val presets by configMap<String, JsonObject>("leap_presets.json")
    private val leapMode by selector("Leap mode", LeapMode.Class, "Choose boss leap targets by player name or class.").open()
    private val containerSettings = ContainerSettings(this)
    private val swapBack by switch("Swap back", desc = "Restores your previous hotbar slot after leaping.")
    val byName get() = leapMode.selected == LeapMode.Name

    private var presetName by textInput("Preset name", length = 32, desc = "Preset to save or load.", placeholder = "My preset").suggests { presets.keys.sorted() }

    init {
        +button("Save preset", desc = "Saves current settings. Overwrites presets with the same name.") { preset(save = true) }
        +button("Load preset", desc = "Loads the selected preset.") { preset(save = false) }

        FastLeap(this)
    }

    var melodyTarget: String? = null
        private set
    private val melodyProgress = setOf("1/3", "2/3", "33%", "66%", "67%")

    private val autoLeap = AutoLeap(this)

    override fun onDisable() = reset()

    init {
        command.sub("leap") { target: String ->
            DungeonClass.entries
                .firstOrNull { it != DungeonClass.Unknown && it.name.equals(target, true) }
                ?.let { leap(it) } ?: leap(target)
        }.description("Leaps to a dungeon teammate by name or class.")
            .suggests("target") {
                dungeonTeammatesNoSelf.map { it.name } + (DungeonClass.entries - DungeonClass.Unknown).map { it.name }
            }

        on<WorldEvent.Change> { reset() }
        on<PartyEvent.Message> {
            if (!content.endsWith(')') && melodyProgress.any { it in content }) melodyTarget = sender
        }
    }

    fun leap(name: String) = LeapManager.leap(name, settings = containerSettings, swapBack = swapBack)
    fun leap(clazz: DungeonClass) = LeapManager.leap(clazz, settings = containerSettings, swapBack = swapBack)

    private fun preset(save: Boolean) {
        val name = presetName.trim()
        if (name.isEmpty()) return modMessage("&cEnter a preset name first.")
        val key = presets.keys.firstOrNull { it.equals(name, ignoreCase = true) } ?: name
        if (save) {
            presets[key] = JsonObject().apply {
                settings.forEach { setting ->
                    if (setting is Saving && setting.jsonName != "Preset name") add(setting.jsonName, setting.write())
                }
            }
        } else {
            val preset = presets[key] ?: return modMessage("&cAuto Leap preset &e$name &cdoesn't exist.")
            preset.entrySet().forEach { (settingName, value) -> (getSettingByName(settingName) as? Saving)?.read(value) }
        }
        presetName = key
        modMessage("&a${if (save) "Saved" else "Loaded"} Auto Leap preset &e$key&a.")
    }

    private fun reset() {
        melodyTarget = null
        autoLeap.reset()
    }

    @Suppress("unused")
    private enum class LeapMode { Name, Class }
}
