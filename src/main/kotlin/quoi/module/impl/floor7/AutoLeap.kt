package quoi.module.impl.floor7

import com.google.gson.JsonObject
import net.minecraft.world.phys.AABB
import quoi.api.events.*
import quoi.api.events.core.on
import quoi.api.skyblock.dungeon.*
import quoi.api.skyblock.dungeon.Dungeon.allTeammatesNoSelf
import quoi.api.skyblock.dungeon.Dungeon.dungeonTeammatesNoSelf
import quoi.api.skyblock.location.Island
import quoi.config.configMap
import quoi.module.Module
import quoi.module.settings.Saving
import quoi.module.settings.Setting.Companion.json
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.utils.ChatUtils.modMessage
import quoi.utils.skyblock.item.ItemUtils.skyblockId
import quoi.utils.skyblock.player.LeapManager

/**
 * TODO:
 *  - redo auto middle leap
 */

object AutoLeap : Module(
    "Auto Leap",
    desc = "Automatically leaps to predefined targets.",
    area = Island.Dungeon
) {
    private val presets by configMap<String, JsonObject>("auto_leap_presets.json")

    private val leapMode by selector("Leap mode", LeapMode.Name, "Leap mode for the module.").open()
    private val fastLeapClickDelay by slider("Fast leap click delay", 250L, 100L, 500L, 50L)
    private val blockInputs by switch("Block inputs", desc = "Blocks keyboard and mouse input while leaping.")
    private val fastMode by switch("Fast mode", desc = "Blocks movement and input only from the leap menu opening until the target click.")
    private val swapBack by switch("Swap back", desc = "Switches back to the previously held item after leaping.")

    private var presetName by textInput("Preset name", length = 32, placeholder = "My preset").suggests { presets.keys.sorted() }

    @Suppress("unused")
    private val savePreset by button("Save preset", desc = "Saves the current Auto Leap settings under this preset name.") { savePreset() }
    @Suppress("unused")
    private val loadPreset by button("Load preset", desc = "Loads the Auto Leap settings saved under this preset name.") { loadPreset() }

    private val doorOpenerLeap by switch("Door opener leap", desc = "Outside of F7 boss, fast leap to the last wither door opener.")
    private val doorOpenerAuto by switch("Auto", desc = "Automatically leaps to a teammate when they open a Wither or Blood door.").json("Door opener auto").childOf(::doorOpenerLeap)
    private val disableAfterBloodOpen by switch("Disable after Blood Open", desc = "Disables Door Fast Leap after the Blood Room has been opened.").childOf(::doorOpenerLeap)

    private val p1Leap by switch("P1 leap", desc = "Leaps in P1.").json("Pre P2 leap")
    private val p1Auto by switch("Auto", desc = "Automatically leaps after Maxor died.").json("P1 leap auto").childOf(::p1Leap)

    private val predevLeap by switch("Predev leap", desc = "Leaps before Storm dev.")
    private val predevAuto by switch("Auto", desc = "Automatically leaps before Storm's lightning.").json("Predev leap auto").childOf(::predevLeap)

    private val greenLeap by switch("Green pad leap", desc = "Leaps on green pad.")
    private val greenAuto by switch("Auto", desc = "Automatically leaps after the first Storm crush.").json("Green pad leap auto").childOf(::greenLeap)

    private val yellowLeap by switch("Yellow pad leap", desc = "Leaps on yellow pad.")
    private val yellowAuto by switch("Auto", desc = "Automatically leaps after the second Storm crush.").json("Yellow pad leap auto").childOf(::yellowLeap)

    private val purpleLeap by switch("Purple pad leap", desc = "Leaps on purple pad.")
    private val purpleAuto by switch("Auto", desc = "Automatically leaps when Storm is enraged.").json("Purple pad leap auto").childOf(::purpleLeap)

    private val pyHealerLeap by switch("PY healer leap", desc="Leaps on PY healer wait spot.")
    private val pyHealerAuto by switch("Auto", desc="Leaps on after first Strom crush.").json("PY healer auto").childOf(::pyHealerLeap)

    private val i4Leap by switch("I4 leap", desc="Leaps on Pre4 dev.").json("Pre4 leap")
    private val i4Auto by switch("Auto", desc="Automatically leaps when Pre4 is done.").json("Pre4 leap auto").childOf(::i4Leap)
    private val i4LeapMelody by switch("Leap Melody", desc = "Leaps to the player doing Melody when Pre4 is done.").childOf(::i4Leap)

    private val p3Leap by switch("P3 leap", desc = "Leaps in terminal phase.")
    private val p3Auto by switch("Auto", desc = "Automatically leaps when a section is finished.").json("P3 leap auto").childOf(::p3Leap)
    private val whenBlown by switch("Only when gate blown", desc = "Only leaps when gate is blown").childOf(::p3Auto)

    private val middleLeap by switch("Middle leap", desc = "Leaps in middle.")
    private val middleAuto by switch("Auto", desc = "Automatically leaps when instamid would send you to middle.").json("Middle leap auto").childOf(::middleLeap)

    private val p4Leap by switch("P4 leap", desc = "Leaps at P5 start.").json("P5 leap")
    private val p4Auto by switch("Auto", desc = "Automatically leaps after Necron died.").json("P5 leap auto").childOf(::p4Leap)

    private val relicLeap by switch("Relic leap", desc = "Leaps in relic.")
    private val relicAuto by switch("Auto", desc = "Automatically leaps after picking up a relic.").json("Relic leap auto").childOf(::relicLeap)

    private val p1Name by textInput("Target", "P1", length = 16).json("P1 leap name").childOf(::p1Leap) { p1Leap && leapMode.selected == LeapMode.Name }.suggests { allTeammatesNoSelf }
    private val predevName by textInput("Target", "Predev", length = 16).json("Predev leap name").childOf(::predevLeap) { predevLeap && leapMode.selected == LeapMode.Name }.suggests { allTeammatesNoSelf }
    private val greenName by textInput("Target", "Green", length = 16).json("Green leap name").childOf(::greenLeap) { greenLeap && leapMode.selected == LeapMode.Name }.suggests { allTeammatesNoSelf }
    private val yellowName by textInput("Target", "Yellow", length = 16).json("Yellow leap name").childOf(::yellowLeap) { yellowLeap && leapMode.selected == LeapMode.Name }.suggests { allTeammatesNoSelf }
    private val purpleName by textInput("Target", "Purple", length = 16).json("Purple leap name").childOf(::purpleLeap) { purpleLeap && leapMode.selected == LeapMode.Name }.suggests { allTeammatesNoSelf }
    private val pyHealerName by textInput("Target", "PY healer", length = 16).json("PY healer leap name").childOf(::pyHealerLeap) { pyHealerLeap && leapMode.selected == LeapMode.Name }.suggests{ allTeammatesNoSelf }
    private val i4Name by textInput("Target", "Pre4", length = 16).json("Pre4 leap name").childOf(::i4Leap) { i4Leap && leapMode.selected == LeapMode.Name }.suggests { allTeammatesNoSelf }
    private val s1Name by textInput("S1 leap", "S1", length = 16).childOf(::p3Leap) { p3Leap && leapMode.selected == LeapMode.Name }.suggests { allTeammatesNoSelf }
    private val s2Name by textInput("S2 leap", "S2", length = 16).childOf(::p3Leap) { p3Leap && leapMode.selected == LeapMode.Name }.suggests { allTeammatesNoSelf }
    private val s3Name by textInput("S3 leap", "S3", length = 16).childOf(::p3Leap) { p3Leap && leapMode.selected == LeapMode.Name }.suggests { allTeammatesNoSelf }
    private val s4Name by textInput("S4 leap", "S4", length = 16).childOf(::p3Leap) { p3Leap && leapMode.selected == LeapMode.Name }.suggests { allTeammatesNoSelf }
    private val middleName by textInput("Target", "Middle", length = 16).json("Middle leap name").childOf(::middleLeap) { middleLeap && leapMode.selected == LeapMode.Name }.suggests { allTeammatesNoSelf }
    private val p4Name by textInput("Target", "P5", length = 16).json("P5 leap name").childOf(::p4Leap) { p4Leap && leapMode.selected == LeapMode.Name }.suggests { allTeammatesNoSelf }
    private val relicName by textInput("Target", "Relic", length = 16).json("Relic leap name").childOf(::relicLeap) { relicLeap && leapMode.selected == LeapMode.Name }.suggests { allTeammatesNoSelf }

    private val p1Class by selector("Target", DungeonClass.Unknown).json("P1 leap class").childOf(::p1Leap) { p1Leap && leapMode.selected == LeapMode.Class }
    private val predevClass by selector("Target", DungeonClass.Unknown).json("Predev leap class").childOf(::predevLeap) { predevLeap && leapMode.selected == LeapMode.Class }
    private val greenClass by selector("Target", DungeonClass.Unknown).json("Green leap class").childOf(::greenLeap) { greenLeap && leapMode.selected == LeapMode.Class }
    private val yellowClass by selector("Target", DungeonClass.Unknown).json("Yellow leap class").childOf(::yellowLeap) { yellowLeap && leapMode.selected == LeapMode.Class }
    private val purpleClass by selector("Target", DungeonClass.Unknown).json("Purple leap class").childOf(::purpleLeap) { purpleLeap && leapMode.selected == LeapMode.Class }
    private val pyHealerClass by selector("Target", DungeonClass.Unknown).json("PY healer class").childOf(::pyHealerLeap) { pyHealerLeap && leapMode.selected == LeapMode.Class }
    private val i4Class by selector("Target", DungeonClass.Unknown).json("Pre4 leap class").childOf(::i4Leap) { i4Leap && leapMode.selected == LeapMode.Class }
    private val s1Class by selector("S1 leap", DungeonClass.Healer).json("S1 leap class").childOf(::p3Leap) { p3Leap && leapMode.selected == LeapMode.Class }
    private val s2Class by selector("S2 leap", DungeonClass.Archer).json("S2 leap class").childOf(::p3Leap) { p3Leap && leapMode.selected == LeapMode.Class }
    private val s3Class by selector("S3 leap", DungeonClass.Mage).json("S3 leap class").childOf(::p3Leap) { p3Leap && leapMode.selected == LeapMode.Class }
    private val s4Class by selector("S4 leap", DungeonClass.Mage).json("S4 leap class").childOf(::p3Leap) { p3Leap && leapMode.selected == LeapMode.Class }
    private val middleClass by selector("Target", DungeonClass.Unknown).json("Middle leap class").childOf(::middleLeap) { middleLeap && leapMode.selected == LeapMode.Class }
    private val p4Class by selector("Target", DungeonClass.Unknown).json("P5 leap class").childOf(::p4Leap) { p4Leap && leapMode.selected == LeapMode.Class }
    private val relicClass by selector("Target", DungeonClass.Unknown).json("Relic leap class").childOf(::relicLeap) { relicLeap && leapMode.selected == LeapMode.Class }

    private var lastClick = 0L
    private var arghCount = 0
    private var crystalCount = 0
    private var oofCount = 0
    private var melodyTarget: String? = null
    private var pickedUpRelic = false

    private val melodyProgress = setOf("1/4", "2/4", "3/4", "25%", "50%", "75%")

    private val greenPadBox = AABB(24.0, 170.0, 4.0, 41.0, 172.0, 21.0)
    private val yellowPadBox = AABB(24.0, 170.0, 86.0, 41.0, 172.0, 103.0)
    private val purplePadBox = AABB(95.0, 164.0, 86.0, 123.0, 172.0, 103.0)
    private val healerPyBox = AABB(56.0, 69.0, 169.0, 171.0, 64.0, 68.0)
    private val middleBox = AABB(47.0, 64.0, 69.0, 61.0, 75.0, 83.0)
    private val pre4Box = AABB(62.0, 127.0, 34.0, 65.0, 130.0, 37.0)

    private val melodyPlayerRegex = Regex("""([A-Za-z0-9_]{3,16}):""")
    private val deviceDoneRegex = Regex("""^(\w+) completed a device! \((.*?)\)$""")
    private val stormCrushMessages = setOf("[BOSS] Storm: Oof", "[BOSS] Storm: Ouch, that hurt!")

    override fun onDisable() {
        reset()
        super.onDisable()
    }

    init {
        command.sub("leap") { target: String ->
            val clazz = DungeonClass.entries.firstOrNull {
                it != DungeonClass.Unknown && it.name.equals(target, ignoreCase = true)
            }

            if (clazz != null) leap(clazz)
            else leap(target)
        }.description("Leaps to a dungeon teammate by name or class.")
            .suggests("target") {
                dungeonTeammatesNoSelf.map { it.name } +
                        DungeonClass.entries.filter { it != DungeonClass.Unknown }.map { it.name }
            }

        on<WorldEvent.Change> { reset() }

        on<DungeonEvent.StageComplete> {
            if (!p3Leap || !p3Auto || !Floor7Utils.inPhaseAt(Phase.P3)) return@on
            if (whenBlown) return@on
            handleP3Leap(completedStage = stage)
        }

        on<DungeonEvent.StageComplete.Full> {
            if (!p3Leap || !p3Auto || !Floor7Utils.inPhaseAt(Phase.P3)) return@on
            if (!whenBlown) return@on
            handleP3Leap(completedStage = stage)
        }

        on<ChatEvent.Packet> {
            if (!Floor7Utils.inF7Boss) return@on

            if (i4Leap && i4LeapMelody && "Party" in unformatted && melodyProgress.any { it in unformatted }) {
                melodyTarget = melodyPlayerRegex.findAll(unformatted).lastOrNull()?.groupValues?.get(1)
            }

            if (unformatted in stormCrushMessages) {
                oofCount++
                if (oofCount == 1) {
                    if (greenLeap && greenAuto && isInGreenPad()) leapToConfigured(greenName, greenClass.selected)
                    if (pyHealerLeap && pyHealerAuto && isInHealerPy()) leapToConfigured(pyHealerName, pyHealerClass.selected)
                }
                if (oofCount == 2 && yellowLeap && yellowAuto && isInYellowPad()) {
                    leapToConfigured(yellowName, yellowClass.selected)
                }
            }

            if (unformatted == "⚠ Storm is enraged! ⚠" && purpleLeap && purpleAuto && isInPurplePad()) {
                leapToConfigured(purpleName, purpleClass.selected)
            }

            if (unformatted == "The Energy Laser is charging up!" && p1Leap && p1Auto) {
                if (++crystalCount == 2 && isInP1()) leapToConfigured(p1Name, p1Class.selected)
            }

            if (unformatted == "[BOSS] Storm: I'd be happy to show you what that's like!" && predevLeap && predevAuto && isInPredev()) {
                leapToConfigured(predevName, predevClass.selected)
            }

            if (unformatted == "[BOSS] Necron: ARGH!" && p4Leap && p4Auto) {
                if (++arghCount == 2 && isInP4()) leapToConfigured(p4Name, p4Class.selected)
            }

            if (unformatted == "[BOSS] Necron: That's a very impressive trick. I guess I'll have to handle this myself." &&
                middleLeap && middleAuto && isOutsideMiddle()
            ) {
                leapToConfigured(middleName, middleClass.selected)
            }

            if (!i4Leap || !i4Auto || !isAtPre4()) return@on

            val (playerName) = deviceDoneRegex.matchEntire(unformatted)?.destructured ?: return@on
            if (playerName != player.name.string) return@on

            leapToPre4Target()
        }

        // based on https://github.com/Noamm9/NoammAddons/blob/1.1.9/src/main/kotlin/com/github/noamm9/features/impl/floor7/M7Relics.kt#L96-L104
        on<TickEvent.End> {
            if (!relicLeap || !relicAuto || pickedUpRelic || !isInRelic()) return@on

            val relic = player.inventory.getItem(8)
            if (!relic.displayName.string.contains("Relic")) return@on
            pickedUpRelic = true

            leapToConfigured(relicName, relicClass.selected)
        }

        on<MouseEvent.Click> {
            if (button != 0 || !state) return@on
            if (player.mainHandItem.skyblockId !in setOf("INFINITE_SPIRIT_LEAP", "SPIRIT_LEAP")) return@on
            cancel()

            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClick < fastLeapClickDelay) return@on

            if (!attemptFastLeap()) {
                if (!p3Leap || !Floor7Utils.inF7Boss) return@on
                handleP3Leap(Floor7Utils.getStageAt())
            }
            lastClick = currentTime
        }

        on<DungeonEvent.DoorOpen> {
            if (!doorOpenerLeap || !doorOpenerAuto || Dungeon.inBoss) return@on
            if (opener == player.name.string) return@on
            if (disableAfterBloodOpen && Dungeon.bloodOpen) return@on

            leap(opener)
        }
    }

    private fun leapToConfigured(name: String, clazz: DungeonClass): Boolean {
        return when (leapMode.selected) {
            LeapMode.Name -> {
                if (name.isBlank()) {
                    false
                } else {
                    leap(name)
                    true
                }
            }

            LeapMode.Class -> {
                if (clazz == DungeonClass.Unknown) {
                    false
                } else {
                    leap(clazz)
                    true
                }
            }
        }
    }

    private fun leapToPre4Target(): Boolean {
        val melody = melodyTarget

        if (i4LeapMelody && melody != null) {
            leap(melody)
            return true
        }

        return leapToConfigured(i4Name, i4Class.selected)
    }

    private fun leap(name: String) {
        LeapManager.leap(
            name,
            blockInput = blockInputs,
            fastMode = fastMode,
            swapBack = swapBack,
        )
    }

    private fun leap(clazz: DungeonClass) {
        LeapManager.leap(
            clazz,
            blockInput = blockInputs,
            fastMode = fastMode,
            swapBack = swapBack,
        )
    }

    private fun savePreset() {
        val name = presetName.trim()
        if (name.isEmpty()) {
            modMessage("&cEnter a preset name first.")
            return
        }

        val presetKey = presets.keys.firstOrNull { it.equals(name, ignoreCase = true) } ?: name
        presets[presetKey] = JsonObject().apply {
            settings.forEach { setting ->
                if (setting is Saving && setting.jsonName != "Preset name") add(setting.jsonName, setting.write())
            }
        }
        presetName = presetKey
        modMessage("&aSaved Auto Leap preset &e$presetKey&a.")
    }

    private fun loadPreset() {
        val name = presetName.trim()
        if (name.isEmpty()) {
            modMessage("&cEnter a preset name first.")
            return
        }

        val presetKey = presets.keys.firstOrNull { it.equals(name, ignoreCase = true) }
        val preset = presetKey?.let(presets::get)
        if (presetKey == null || preset == null) {
            modMessage("&cAuto Leap preset &e$name &cdoesn't exist.")
            return
        }

        preset.entrySet().forEach { (settingName, value) -> (getSettingByName(settingName) as? Saving)?.read(value) }
        presetName = presetKey
        modMessage("&aLoaded Auto Leap preset &e$presetKey&a.")
    }

    private fun reset() {
        melodyTarget = null
        arghCount = 0
        crystalCount = 0
        oofCount = 0
        pickedUpRelic = false
    }

    private fun isIn(box: AABB): Boolean = box.contains(player.position())

    private fun isInP1() = Floor7Utils.inPhaseAt(Phase.P1)
    private fun isInPredev() = Floor7Utils.inPhaseAt(Phase.P3) && Floor7Utils.inPhase(Phase.P1, Phase.P2)
    private fun isInP4() = Floor7Utils.inPhaseAt(Phase.P4)
    private fun isInRelic() = Floor7Utils.inPhaseAt(Phase.P5)
    private fun isInGreenPad() = isIn(greenPadBox)
    private fun isInYellowPad() = isIn(yellowPadBox)
    private fun isInPurplePad() = isIn(purplePadBox)
    private fun isInHealerPy() = isIn(healerPyBox) && Floor7Utils.inPhase(Phase.P2)
    private fun isInMiddle() = isIn(middleBox)
    private fun isAtPre4() = isIn(pre4Box)
    private fun isOutsideMiddle() = Floor7Utils.inPhaseAt(Phase.P4) && !isInMiddle()

    private fun attemptFastLeap(): Boolean {
        if (!Dungeon.inBoss) {
            val doorOpener = Dungeon.doorOpener.takeIf {
                doorOpenerLeap && it != "Unknown" && it != player.name.string && (!disableAfterBloodOpen || !Dungeon.bloodOpen)
            } ?: return false

            leap(doorOpener)
            return true
        }

        if (!Floor7Utils.inF7Boss) return false

        return when {
            predevLeap && isInPredev() -> leapToConfigured(predevName, predevClass.selected)
            relicLeap && isInRelic() -> leapToConfigured(relicName, relicClass.selected)
            p1Leap && isInP1() -> leapToConfigured(p1Name, p1Class.selected)
            p4Leap && isInMiddle() -> leapToConfigured(p4Name, p4Class.selected)
            greenLeap && isInGreenPad() -> leapToConfigured(greenName, greenClass.selected)
            yellowLeap && isInYellowPad() -> leapToConfigured(yellowName, yellowClass.selected)
            purpleLeap && isInPurplePad() -> leapToConfigured(purpleName, purpleClass.selected)
            pyHealerLeap && isInHealerPy() -> leapToConfigured(pyHealerName, pyHealerClass.selected)
            middleLeap && isOutsideMiddle() -> leapToConfigured(middleName, middleClass.selected)
            i4Leap && isAtPre4() -> leapToPre4Target()
            else -> false
        }
    }

    private fun handleP3Leap(completedStage: Stage) {
        val currentStage = Floor7Utils.getStageAt()

        if (currentStage == Stage.UNKNOWN || currentStage.number > completedStage.number) return // don't leap if the player is already in a later stage.

        val (name, clazz) = when (completedStage) {
            Stage.S1 -> s1Name to s1Class.selected
            Stage.S2 -> s2Name to s2Class.selected
            Stage.S3 -> s3Name to s3Class.selected
            Stage.S4 -> s4Name to s4Class.selected
            else -> return // since there are no S5 or UNKNOW complete events we do not care
        }

        leapToConfigured(name, clazz)
    }

    private enum class LeapMode {
        Name, Class
    }
}
