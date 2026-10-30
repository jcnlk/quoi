package quoi.module.impl.floor7

import com.google.gson.JsonObject
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket
import net.minecraft.world.phys.AABB
import quoi.api.events.*
import quoi.api.events.core.on
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.Dungeon.allTeammatesNoSelf
import quoi.api.skyblock.dungeon.Dungeon.dungeonTeammatesNoSelf
import quoi.api.skyblock.dungeon.Floor7
import quoi.api.skyblock.dungeon.enums.DungeonClass
import quoi.api.skyblock.dungeon.enums.Phase
import quoi.api.skyblock.dungeon.enums.Stage
import quoi.api.skyblock.location.Island
import quoi.config.configMap
import quoi.module.Module
import quoi.module.settings.Saving
import quoi.module.settings.Setting.Companion.json
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.utils.ChatUtils.modMessage
import quoi.utils.skyblock.item.ItemUtils.skyblockId
import quoi.utils.skyblock.player.LeapManager
import quoi.utils.skyblock.player.container.ContainerSettings

// TODO: rewrite this
object AutoLeap : Module(
    "Auto Leap",
    desc = "Leaps to location-based targets. Left-click a Spirit Leap, or enable Auto for each leap.",
    area = Island.Dungeon
) {
    private val presets by configMap<String, JsonObject>("auto_leap_presets.json")

    private val leapMode by selector("Leap mode", LeapMode.Name, "Choose boss leap targets by player name or class.").open()
    private val containerSettings = ContainerSettings(this)
    private val swapBack by switch("Swap back", desc = "Restores your previous hotbar slot after leaping.")

    private var presetName by textInput("Preset name", length = 32, desc = "Preset to save or load.", placeholder = "My preset").suggests { presets.keys.sorted() }

    @Suppress("unused")
    private val savePreset by button("Save preset", desc = "Saves current settings. Overwrites presets with the same name.") { savePreset() }

    @Suppress("unused")
    private val loadPreset by button("Load preset", desc = "Loads the selected preset.") { loadPreset() }

    private val doorOpenerLeap by switch("Door opener leap", desc = "Leap to the last Wither or Blood door opener during clear.")
    private val doorOpenerAuto by switch("Auto", desc = "Leap when a teammate opens a Wither or Blood door.").json("Door opener auto").childOf(::doorOpenerLeap)
    private val disableAfterBloodOpen by switch("Disable after Blood Open", desc = "Stops door opener leaps once Blood is open.").childOf(::doorOpenerLeap)

    private val crystalLeap by switch("Crystal leap", desc = "Leap from either P1 crystal platform.")
    private val crystalAuto by switch("Auto", desc = "Leap on Energy Crystal pickup.").json("Crystal leap auto").childOf(::crystalLeap)

    private val p1Leap by switch("P1 leap", desc = "Leap from P1. Enabled Crystal leap takes priority on crystal platforms.").json("Pre P2 leap")
    private val p1Auto by switch("Auto", desc = "Leap on the second Energy Laser charge in P1.").json("P1 leap auto").childOf(::p1Leap)

    private val predevLeap by switch("Predev leap", desc = "Leap from P3 while Maxor or Storm is still active.")
    private val predevAuto by switch("Auto", desc = "Leap on Storm's lightning dialogue while in P3.").json("Predev leap auto").childOf(::predevLeap)

    private val yellowLeap by switch("Yellow pad leap", desc = "Leap from the yellow pad.")
    private val yellowAuto by switch("Auto", desc = "Leap after Storm's second crush while on yellow.").json("Yellow pad leap auto").childOf(::yellowLeap)

    private val purpleLeap by switch("Purple pad leap", desc = "Leap from the purple pad.")
    private val purpleAuto by switch("Auto", desc = "Leap after Storm's first crush while on purple.").json("Purple pad leap auto").childOf(::purpleLeap)

    private val betweenPyPadLeap by switch("Between PY pad leap", desc = "Leap from between the purple and yellow pads.")
    private val betweenPyPadAuto by switch("Auto", desc = "Leap after Storm's first crush while between the pads.").json("Between PY Pad leap auto").childOf(::betweenPyPadLeap)

    private val stormDeathLeap by switch("Storm death leap", desc = "Leap from P2, outside the pads and the area between them.")
    private val stormDeathAuto by switch("Auto", desc = "Leap on Storm's death message, from any position.").json("Storm death auto").childOf(::stormDeathLeap)

    private val i4Leap by switch("I4 leap", desc = "Leap from the I4 device.").json("Pre4 leap")
    private val i4Auto by switch("Auto", desc = "Leap after completing I4 during section 1 or 2.").json("Pre4 leap auto").childOf(::i4Leap)
    private val i4LeapMelody by switch("Leap Melody", desc = "Uses the last Melody progress sender in party chat, otherwise the I4 target.").childOf(::i4Leap)

    private val p3Leap by switch("P3 leap", desc = "Leap to your P3 section's target when no other leap applies.")
    private val p3Auto by switch("Auto", desc = "Leap when a section's objectives finish, unless you are ahead or at I4.").json("P3 leap auto").childOf(::p3Leap)
    private val whenBlown by switch("Only when gate blown", desc = "Also waits for the gate, or the Core entrance in section 4.").childOf(::p3Auto)

    private val middleLeap by switch("Middle leap", desc = "Leap from P4, outside the middle platform.")
    private val middleAuto by switch("Auto", desc = "Leap on Necron's 'Goodbye.' while outside middle in P4.").json("Middle leap auto").childOf(::middleLeap)

    private val p4Leap by switch("P4 leap", desc = "Leap from the P4 middle platform.").json("P5 leap")
    private val p4Auto by switch("Auto", desc = "Leap once at about 5% Necron HP while in P4.").json("P5 leap auto").childOf(::p4Leap)

    private val relicLeap by switch("Relic leap", desc = "Leap during M7 P5.")
    private val relicAuto by switch("Auto", desc = "Leap when a relic enters hotbar slot 9 in P5.").json("Relic leap auto").childOf(::relicLeap)

    private val crystalName by textInput("Target", length = 16, desc = "Target player name. Leave empty to skip.").json("Crystal leap name").childOf(::crystalLeap) { crystalLeap && leapMode.selected == LeapMode.Name }.suggests { allTeammatesNoSelf }
    private val p1Name by textInput("Target", "P1", length = 16, desc = "Target player name. Leave empty to skip.").json("P1 leap name").childOf(::p1Leap) { p1Leap && leapMode.selected == LeapMode.Name }.suggests { allTeammatesNoSelf }
    private val predevName by textInput("Target", length = 16, desc = "Target player name. Leave empty to skip.").json("Predev leap name").childOf(::predevLeap) { predevLeap && leapMode.selected == LeapMode.Name }.suggests { allTeammatesNoSelf }
    private val yellowName by textInput("Target", length = 16, desc = "Target player name. Leave empty to skip.").json("Yellow leap name").childOf(::yellowLeap) { yellowLeap && leapMode.selected == LeapMode.Name }.suggests { allTeammatesNoSelf }
    private val purpleName by textInput("Target", length = 16, desc = "Target player name. Leave empty to skip.").json("Purple leap name").childOf(::purpleLeap) { purpleLeap && leapMode.selected == LeapMode.Name }.suggests { allTeammatesNoSelf }
    private val betweenPyPadName by textInput("Target", length = 16, desc = "Target player name. Leave empty to skip.").json("Between PY Pad Name").childOf(::betweenPyPadLeap) { betweenPyPadLeap && leapMode.selected == LeapMode.Name }.suggests { allTeammatesNoSelf }

    private val stormLeapName by textInput("Target", length = 16, desc = "Target player name. Leave empty to skip.").json("Storm death leap name").childOf(::stormDeathLeap) { stormDeathLeap && leapMode.selected == LeapMode.Name }.suggests { allTeammatesNoSelf }
    private val i4Name by textInput("Target", length = 16, desc = "Target player name. Leave empty to skip.").json("Pre4 leap name").childOf(::i4Leap) { i4Leap && leapMode.selected == LeapMode.Name }.suggests { allTeammatesNoSelf }
    private val s1Name by textInput("S1 leap", length = 16, desc = "Player to leap to from section 1 or when it finishes.").childOf(::p3Leap) { p3Leap && leapMode.selected == LeapMode.Name }.suggests { allTeammatesNoSelf }
    private val s2Name by textInput("S2 leap", length = 16, desc = "Player to leap to from section 2 or when it finishes.").childOf(::p3Leap) { p3Leap && leapMode.selected == LeapMode.Name }.suggests { allTeammatesNoSelf }
    private val s3Name by textInput("S3 leap", length = 16, desc = "Player to leap to from section 3 or when it finishes.").childOf(::p3Leap) { p3Leap && leapMode.selected == LeapMode.Name }.suggests { allTeammatesNoSelf }
    private val s4Name by textInput("S4 leap", length = 16, desc = "Player to leap to from section 4 or when it finishes.").childOf(::p3Leap) { p3Leap && leapMode.selected == LeapMode.Name }.suggests { allTeammatesNoSelf }
    private val middleName by textInput("Target", length = 16, desc = "Target player name. Leave empty to skip.").json("Middle leap name").childOf(::middleLeap) { middleLeap && leapMode.selected == LeapMode.Name }.suggests { allTeammatesNoSelf }
    private val p4Name by textInput("Target", length = 16, desc = "Target player name. Leave empty to skip.").json("P5 leap name").childOf(::p4Leap) { p4Leap && leapMode.selected == LeapMode.Name }.suggests { allTeammatesNoSelf }
    private val relicName by textInput("Target", length = 16, desc = "Target player name. Leave empty to skip.").json("Relic leap name").childOf(::relicLeap) { relicLeap && leapMode.selected == LeapMode.Name }.suggests { allTeammatesNoSelf }

    private val crystalClass by selector("Target", DungeonClass.Unknown, desc = "Target class. Unknown skips this target.").json("Crystal leap class").childOf(::crystalLeap) { crystalLeap && leapMode.selected == LeapMode.Class }
    private val p1Class by selector("Target", DungeonClass.Unknown, desc = "Target class. Unknown skips this target.").json("P1 leap class").childOf(::p1Leap) { p1Leap && leapMode.selected == LeapMode.Class }
    private val predevClass by selector("Target", DungeonClass.Unknown, desc = "Target class. Unknown skips this target.").json("Predev leap class").childOf(::predevLeap) { predevLeap && leapMode.selected == LeapMode.Class }
    private val yellowClass by selector("Target", DungeonClass.Unknown, desc = "Target class. Unknown skips this target.").json("Yellow leap class").childOf(::yellowLeap) { yellowLeap && leapMode.selected == LeapMode.Class }
    private val purpleClass by selector("Target", DungeonClass.Unknown, desc = "Target class. Unknown skips this target.").json("Purple leap class").childOf(::purpleLeap) { purpleLeap && leapMode.selected == LeapMode.Class }
    private val betweenPyPadClass by selector("Target", DungeonClass.Unknown, desc = "Target class. Unknown skips this target.").json("Between PY Pad class").childOf(::betweenPyPadLeap) { betweenPyPadLeap && leapMode.selected == LeapMode.Class }
    private val stormDeathClass by selector("Target", DungeonClass.Unknown, desc = "Target class. Unknown skips this target.").json("Storm death class").childOf(::stormDeathLeap) { stormDeathLeap && leapMode.selected == LeapMode.Class }
    private val i4Class by selector("Target", DungeonClass.Unknown, desc = "Target class. Unknown skips this target.").json("Pre4 leap class").childOf(::i4Leap) { i4Leap && leapMode.selected == LeapMode.Class }
    private val s1Class by selector("S1 leap", DungeonClass.Unknown, desc = "Class to leap to from section 1 or when it finishes.").json("S1 leap class").childOf(::p3Leap) { p3Leap && leapMode.selected == LeapMode.Class }
    private val s2Class by selector("S2 leap", DungeonClass.Unknown, desc = "Class to leap to from section 2 or when it finishes.").json("S2 leap class").childOf(::p3Leap) { p3Leap && leapMode.selected == LeapMode.Class }
    private val s3Class by selector("S3 leap", DungeonClass.Unknown, desc = "Class to leap to from section 3 or when it finishes.").json("S3 leap class").childOf(::p3Leap) { p3Leap && leapMode.selected == LeapMode.Class }
    private val s4Class by selector("S4 leap", DungeonClass.Unknown, desc = "Class to leap to from section 4 or when it finishes.").json("S4 leap class").childOf(::p3Leap) { p3Leap && leapMode.selected == LeapMode.Class }
    private val middleClass by selector("Target", DungeonClass.Unknown, desc = "Target class. Unknown skips this target.").json("Middle leap class").childOf(::middleLeap) { middleLeap && leapMode.selected == LeapMode.Class }
    private val p4Class by selector("Target", DungeonClass.Unknown, desc = "Target class. Unknown skips this target.").json("P5 leap class").childOf(::p4Leap) { p4Leap && leapMode.selected == LeapMode.Class }
    private val relicClass by selector("Target", DungeonClass.Unknown, desc = "Target class. Unknown skips this target.").json("Relic leap class").childOf(::relicLeap) { relicLeap && leapMode.selected == LeapMode.Class }

    private var lastClick = 0L
    private var crystalCount = 0
    private var oofCount = 0
    private var melodyTarget: String? = null
    private var p4Leaped = false

    private val melodyProgress = setOf("1/3", "2/3", "33%", "66%", "67%")

    private val leftCrystalBox = AABB(78.0, 233.0, 46.0, 87.0, 243.0, 55.0)
    private val rightCrystalBox = AABB(60.0, 233.0, 46.0, 69.0, 243.0, 55.0)
    private val yellowPadBox = AABB(24.0, 170.0, 86.0, 41.0, 172.0, 103.0)
    private val purplePadBox = AABB(106.0, 170.0, 86.0, 125.0, 172.0, 103.0)
    private val betweenPyPadBox = AABB(41.0, 163.0, 86.0, 106.0, 172.0, 103.0)
    private val middleBox = AABB(47.0, 64.0, 69.0, 62.0, 75.0, 84.0)
    private val pre4Box = AABB(62.0, 127.0, 34.0, 65.0, 130.0, 37.0)

    private val stormCrushMessages = setOf("[BOSS] Storm: Oof", "[BOSS] Storm: Ouch, that hurt!")
    private val relicIds = setOf("GREEN_KING_RELIC", "PURPLE_KING_RELIC", "BLUE_KING_RELIC", "ORANGE_KING_RELIC", "RED_KING_RELIC")
    private val spiritLeapIds = setOf("INFINITE_SPIRIT_LEAP", "SPIRIT_LEAP")

    override fun onDisable() {
        reset()
        super.onDisable()
    }

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

        on<DungeonEvent.StageComplete> {
            if (!p3Leap || !p3Auto || !Floor7.inPhaseAt(Phase.P3) || isAtPre4()) return@on
            if (whenBlown) return@on
            handleP3Leap(completedStage = stage)
        }

        on<DungeonEvent.StageComplete.Full> {
            if (!p3Leap || !p3Auto || !Floor7.inPhaseAt(Phase.P3) || isAtPre4()) return@on
            if (!whenBlown) return@on
            handleP3Leap(completedStage = stage)
        }

        on<PartyEvent.Message> {
            if (content.endsWith(')') || !melodyProgress.any { it in content }) return@on
            melodyTarget = sender
        }

        on<ChatEvent.Packet> {
            if (!Floor7.inF7Boss) return@on

            if (unformatted in stormCrushMessages) {
                when (++oofCount) {
                    1 -> {
                        if (purpleLeap && purpleAuto && isInPurplePad()) leapToConfigured(purpleName, purpleClass.selected)
                        if (betweenPyPadLeap && betweenPyPadAuto && isInBetweenPyPad()) leapToConfigured(betweenPyPadName, betweenPyPadClass.selected)
                    }
                    2 -> if (yellowLeap && yellowAuto && isInYellowPad()) leapToConfigured(yellowName, yellowClass.selected)
                }
            }

            if (unformatted == "The Energy Laser is charging up!" && p1Leap && p1Auto) {
                if (++crystalCount == 2 && isInP1()) leapToConfigured(p1Name, p1Class.selected)
            }

            if (unformatted == "[BOSS] Storm: I'd be happy to show you what that's like!" && predevLeap && predevAuto && isInPredev()) {
                leapToConfigured(predevName, predevClass.selected)
            }

            if (unformatted == "[BOSS] Necron: Goodbye." && middleLeap && middleAuto && isOutsideMiddle()) {
                leapToConfigured(middleName, middleClass.selected)
            }

            if (unformatted == "[BOSS] Storm: I should have known that I stood no chance." && stormDeathLeap && stormDeathAuto) {
                leapToConfigured(stormLeapName, stormDeathClass.selected)
            }

            if (crystalLeap && crystalAuto && isAtCrystal() && unformatted == "${player.name.string} picked up an Energy Crystal!") {
                leapToConfigured(crystalName, crystalClass.selected)
            }

            if (i4Leap && i4Auto && isAtPre4() && Floor7.inStage(Stage.S1, Stage.S2) && unformatted.startsWith("${player.name.string} completed a device!")) {
                leapToPre4Target()
            }
        }

        on<BossBarEvent.Update> {
            if (!p4Leap || !p4Auto || !Floor7.inF7Boss) return@on
            if (progress !in 0.001f..0.051f || p4Leaped || !unformatted.contains("necron", ignoreCase = true) || !Floor7.inPhaseAt(Phase.P4)) return@on

            p4Leaped = true
            leapToConfigured(p4Name, p4Class.selected)
        }

        on<PacketEvent.ReceivedPost, ClientboundContainerSetSlotPacket> {
            if (!relicLeap || !relicAuto || !isInRelic()) return@on
            if (packet.containerId != 0 || packet.slot != 44 || packet.item.skyblockId !in relicIds) return@on

            leapToConfigured(relicName, relicClass.selected)
        }

        on<MouseEvent.Click> {
            if (button != 0 || !state) return@on
            if (player.mainHandItem.skyblockId !in spiritLeapIds) return@on
            cancel()

            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClick < 250) return@on

            if (!attemptFastLeap()) {
                if (!p3Leap || !Floor7.inF7Boss) return@on
                handleP3Leap(Floor7.getStageAt())
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
            LeapMode.Name -> name.isNotBlank().also { if (it) leap(name) }
            LeapMode.Class -> (clazz != DungeonClass.Unknown).also { if (it) leap(clazz) }
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
            settings = containerSettings,
            swapBack = swapBack,
        )
    }

    private fun leap(clazz: DungeonClass) {
        LeapManager.leap(
            clazz,
            settings = containerSettings,
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

        val presetKey = presets.keys.firstOrNull { it.equals(name, ignoreCase = true) } ?: run {
            modMessage("&cAuto Leap preset &e$name &cdoesn't exist.")
            return
        }

        val preset = presets[presetKey] ?: return
        preset.entrySet().forEach { (settingName, value) -> (getSettingByName(settingName) as? Saving)?.read(value) }
        presetName = presetKey
        modMessage("&aLoaded Auto Leap preset &e$presetKey&a.")
    }

    private fun reset() {
        melodyTarget = null
        crystalCount = 0
        oofCount = 0
        p4Leaped = false
    }

    private fun isIn(box: AABB): Boolean = box.contains(player.position())

    private fun isInLeftCrystal() = isIn(leftCrystalBox)
    private fun isInRightCrystal() = isIn(rightCrystalBox)
    private fun isAtCrystal() = Floor7.inPhaseAt(Phase.P1) && (isInLeftCrystal() || isInRightCrystal())
    private fun isInP1() = Floor7.inPhaseAt(Phase.P1) && (!crystalLeap || !isAtCrystal())
    private fun isInPredev() = Floor7.inPhaseAt(Phase.P3) && Floor7.inPhase(Phase.P1, Phase.P2)
    private fun isInRelic() = Floor7.inPhaseAt(Phase.P5)
    private fun isInYellowPad() = isIn(yellowPadBox)
    private fun isInPurplePad() = isIn(purplePadBox)
    private fun isInBetweenPyPad() = isIn(betweenPyPadBox)
    private fun isInP2() = Floor7.inPhaseAt(Phase.P2) && !isInPurplePad() && !isInYellowPad() && !isInBetweenPyPad()
    private fun isInMiddle() = isIn(middleBox)
    private fun isAtPre4() = isIn(pre4Box)
    private fun isOutsideMiddle() = Floor7.inPhaseAt(Phase.P4) && !isInMiddle()

    private fun attemptFastLeap(): Boolean {
        if (!Dungeon.inBoss) {
            val doorOpener = Dungeon.doorOpener.takeIf {
                doorOpenerLeap && it != "Unknown" && it != player.name.string && (!disableAfterBloodOpen || !Dungeon.bloodOpen)
            } ?: return false

            leap(doorOpener)
            return true
        }

        if (!Floor7.inF7Boss) return false

        return when {
            crystalLeap && isAtCrystal() -> leapToConfigured(crystalName, crystalClass.selected)
            predevLeap && isInPredev() -> leapToConfigured(predevName, predevClass.selected)
            relicLeap && isInRelic() -> leapToConfigured(relicName, relicClass.selected)
            p1Leap && isInP1() -> leapToConfigured(p1Name, p1Class.selected)
            p4Leap && isInMiddle() -> leapToConfigured(p4Name, p4Class.selected)
            yellowLeap && isInYellowPad() -> leapToConfigured(yellowName, yellowClass.selected)
            purpleLeap && isInPurplePad() -> leapToConfigured(purpleName, purpleClass.selected)
            betweenPyPadLeap && isInBetweenPyPad() -> leapToConfigured(betweenPyPadName, betweenPyPadClass.selected)
            stormDeathLeap && isInP2() -> leapToConfigured(stormLeapName, stormDeathClass.selected)
            middleLeap && isOutsideMiddle() -> leapToConfigured(middleName, middleClass.selected)
            i4Leap && isAtPre4() -> leapToPre4Target()
            else -> false
        }
    }

    private fun handleP3Leap(completedStage: Stage) {
        val currentStage = Floor7.getStageAt()

        if (currentStage == Stage.Unknown || currentStage.number > completedStage.number) return // don't leap if the player is already in a later stage.

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
