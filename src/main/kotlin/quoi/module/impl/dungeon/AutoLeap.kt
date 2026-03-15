package quoi.module.impl.dungeon

import quoi.api.events.DungeonEvent
import quoi.api.events.MouseEvent
import quoi.api.skyblock.Island
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.DungeonClass
import quoi.api.skyblock.dungeon.P3Section
import quoi.module.Module
import quoi.module.settings.Setting.Companion.json
import quoi.module.settings.Setting.Companion.withDependency
import quoi.module.settings.impl.BooleanSetting
import quoi.module.settings.impl.NumberSetting
import quoi.module.settings.impl.SelectorSetting
import quoi.module.settings.impl.StringSetting
import quoi.utils.skyblock.player.LeapManager

// Kyleen (maybe)
object AutoLeap : Module(
    "Auto Leap",
    desc = "Automatically leaps to predefined targets.",
    area = Island.Dungeon
) {
    private val fastLeap by BooleanSetting("Fast leap", desc = "Leaps to a set player on infinileap left click.")
    private val fastDelay by NumberSetting("Delay", 250L, 100L, 500L, 50L).withDependency { fastLeap } // to not pull bko
    private val autoLeap by BooleanSetting("Auto leap", desc = "Automatically leaps when a section is finished.")
    private val whenBlown by BooleanSetting("Only when gate blown", desc = "Only leaps when gate is blown").withDependency { autoLeap }
    private val leapMode by SelectorSetting("Leap mode", "Name", listOf("Name", "Class"), "Leap mode for the module.")

    private val clearName by StringSetting("Clear leap", "Clear").withDependency { leapMode.selected == "Name" }
    private val s1Name by StringSetting("S1 leap", "S1").withDependency { leapMode.selected == "Name" }
    private val s2Name by StringSetting("S2 leap", "S2").withDependency { leapMode.selected == "Name" }
    private val s3Name by StringSetting("S3 leap", "S3").withDependency { leapMode.selected == "Name" }
    private val s4Name by StringSetting("S4 leap", "S4").withDependency { leapMode.selected == "Name" }

    private val clearClass by SelectorSetting("Clear leap", DungeonClass.Unknown).json("Clear leap class").withDependency { leapMode.selected == "Class" }
    private val s1Class by SelectorSetting("S1 leap", DungeonClass.Healer).json("S1 leap class").withDependency { leapMode.selected == "Class" }
    private val s2Class by SelectorSetting("S2 leap", DungeonClass.Archer).json("S2 leap class").withDependency { leapMode.selected == "Class" }
    private val s3Class by SelectorSetting("S3 leap", DungeonClass.Mage).json("S3 leap class").withDependency { leapMode.selected == "Class" }
    private val s4Class by SelectorSetting("S4 leap", DungeonClass.Mage).json("S4 leap class").withDependency { leapMode.selected == "Class" }

    private var lastClick = 0L

    init {
        on<DungeonEvent.SectionComplete> {
            if (!autoLeap || !Dungeon.inP3) return@on
            if (whenBlown) return@on
            handleLeap()
        }

        on<DungeonEvent.SectionComplete.Full> {
            if (!autoLeap || !Dungeon.inP3) return@on
            if (!whenBlown) return@on
            handleLeap()
        }

        on<MouseEvent.Click> {
            if (!fastLeap || button != 0 || state) return@on
//            if (player.mainHandItem.skyblockId != "INFINITE_SPIRIT_LEAP") return@on
            if (!player.mainHandItem.displayName.string.contains("InfiniLeap", true)) return@on

            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClick < fastDelay && LeapManager.leapCD < 0) return@on
            handleLeap()
            lastClick = currentTime
        }
    }

    private fun handleLeap() {
        val section = Dungeon.getP3Section()

        val (name, clazz) = if (Dungeon.inClear) {
            clearName to clearClass.selected
        } else {
            when (section) {
                P3Section.S1 -> s1Name to s1Class.selected
                P3Section.S2 -> s2Name to s2Class.selected
                P3Section.S3 -> s3Name to s3Class.selected
                P3Section.S4 -> s4Name to s4Class.selected
                else -> return
            }
        }

        when (leapMode.selected) {
            "Name" -> LeapManager.leap(name)
            "Class" -> LeapManager.leap(clazz)
        }
    }
}