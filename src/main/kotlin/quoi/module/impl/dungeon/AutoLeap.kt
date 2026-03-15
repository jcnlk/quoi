package quoi.module.impl.dungeon

import net.minecraft.world.InteractionHand
import quoi.api.events.ChatEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.EventPriority
import quoi.api.skyblock.Island
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.Dungeon.p3Section
import quoi.api.skyblock.dungeon.DungeonClass
import quoi.api.skyblock.dungeon.P3Section
import quoi.module.Module
import quoi.module.settings.Setting.Companion.withDependency
import quoi.module.settings.impl.BooleanSetting
import quoi.module.settings.impl.SelectorSetting
import quoi.module.settings.impl.StringSetting
import quoi.utils.ChatUtils.modMessage
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.skyblock.player.LeapManager

// Kyleen
object AutoLeap : Module(
    "Auto Leap",
    desc = "Automatically leaps to predefined targets.",
    area = Island.Dungeon
) {
    private val fastLeap by BooleanSetting("Fast leap", desc = "Leaps to a set player on infinileap left click.")
    private val autoLeap by BooleanSetting("Auto leap", desc = "Automatically leaps when a section is finished.")
    private val whenBlown by BooleanSetting("Only when gate blown", desc = "Only leaps when gate is blown").withDependency { autoLeap }
    private val leapMode by SelectorSetting("Leap mode", "Name", listOf("Name", "Class"), "Leap mode for the module.")

    private val clearLeapName by StringSetting("Clear leap", "Clear").withDependency { leapMode.selected == "Name" }
    private val s1LeapName by StringSetting("S1 leap", "S1").withDependency { leapMode.selected == "Name" }
    private val s2LeapName by StringSetting("S2 leap", "S2").withDependency { leapMode.selected == "Name" }
    private val s3LeapName by StringSetting("S3 leap", "S3").withDependency { leapMode.selected == "Name" }
    private val s4LeapName by StringSetting("S4 leap", "S4").withDependency { leapMode.selected == "Name" }
    private val coreLeapName by StringSetting("Core leap", "Core").withDependency { leapMode.selected == "Name" }

    private val clearLeapClass by SelectorSetting("Clear leap class", DungeonClass.Unknown).withDependency { leapMode.selected == "Class" }
    private val s1LeapClass by SelectorSetting("S1 leap class", DungeonClass.Healer).withDependency { leapMode.selected == "Class" }
    private val s2LeapClass by SelectorSetting("S2 leap class", DungeonClass.Archer).withDependency { leapMode.selected == "Class" }
    private val s3LeapClass by SelectorSetting("S3 leap class", DungeonClass.Mage).withDependency { leapMode.selected == "Class" }
    private val s4LeapClass by SelectorSetting("S4 leap class", DungeonClass.Mage).withDependency { leapMode.selected == "Class" }
    private val coreLeapClass by SelectorSetting("Core leap class", DungeonClass.Mage).withDependency { leapMode.selected == "Class" }

    private var isQueued = false
    private var wasLeftPressed = false

    private val REGEX_TERM_COMPLETED = Regex("^(.{1,16}) (activated|completed) a (terminal|lever|device)! \\((\\d)/(\\d)\\)$")

    init {
        on<WorldEvent.Change> {
            isQueued = false
            wasLeftPressed = false
        }

        on<ChatEvent.Packet>(EventPriority.MEDIUM) {
            if (!autoLeap) return@on
            val msg = message.noControlCodes

//            Note: Supposed to be S1
//            if (msg == "[BOSS] Storm: I should have known that I stood no chance.") {
//                handleLeap()
//                return@on
//            }

            if (!Dungeon.inP3) return@on

            if (whenBlown && !p3Section.gate) return@on

            val termMatch = REGEX_TERM_COMPLETED.find(msg)
            if (termMatch != null) {
                val type = termMatch.groupValues[3]
                val completed = termMatch.groupValues[4].toIntOrNull() ?: 0
                val total = termMatch.groupValues[5].toIntOrNull() ?: 0

                if (completed == total) {
                    if (type == "device" && AutoSS.enabled && AutoSS.leapWhenDone && AutoSS.doneSS) {
                        modMessage("Debug")
                        return@on
                    }

                    if (whenBlown && !p3Section.gate) return@on

                    handleLeap()
                }
            }
        }

        on<TickEvent.Start> {
            if (mc.player == null) return@on

            if (isQueued && mc.screen == null) {
                isQueued = false
                handleLeap()
            }

            val isLeftPressed = mc.mouseHandler.isLeftPressed
            if (fastLeap && isLeftPressed && !wasLeftPressed && mc.screen == null) {
                val heldItem = mc.player?.getItemInHand(InteractionHand.MAIN_HAND)
                val name = heldItem?.displayName?.string?.noControlCodes ?: ""

                if (name.contains("Infinileap", ignoreCase = true) || name.contains("Spirit Leap", ignoreCase = true)) {
                    handleLeap()
                }
            }
            wasLeftPressed = isLeftPressed
        }
    }

    private fun handleLeap() {
        //Note: Lowkey idk if this works properly
        if (mc.screen != null) {
            isQueued = true
            modMessage("§aQueued leap")
            return
        }

        val mode = leapMode.selected
        var targetName: String
        var targetClass: DungeonClass

        if (!Dungeon.inBoss) {
            targetName = clearLeapName
            targetClass = clearLeapClass.selected
        } else {
            when (p3Section) {
                P3Section.S1 -> { targetName = s1LeapName; targetClass = s1LeapClass.selected }
                P3Section.S2 -> { targetName = s2LeapName; targetClass = s2LeapClass.selected }
                P3Section.S3 -> { targetName = s3LeapName; targetClass = s3LeapClass.selected }
                P3Section.S4 -> { targetName = s4LeapName; targetClass = s4LeapClass.selected }
                //Fuck you phrog
                //P3Section.CORE -> { targetName = coreLeapName; targetClass = coreLeapClass.selected }
                else -> return
            }
        }

        if (mode == "Name" && targetName.isNotEmpty()) {
            LeapManager.leap(targetName)
        } else if (mode == "Class" && targetClass != DungeonClass.Unknown) {
            LeapManager.leap(targetClass)
        }
    }
}