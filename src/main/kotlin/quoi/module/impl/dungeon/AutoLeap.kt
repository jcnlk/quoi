package quoi.module.impl.dungeon

import kotlinx.coroutines.launch
import net.minecraft.world.phys.Vec3
import quoi.QuoiMod.scope
import quoi.api.abobaui.constraints.Positions
import quoi.api.abobaui.dsl.at
import quoi.api.abobaui.dsl.px
import quoi.api.abobaui.elements.ElementScope
import quoi.api.abobaui.elements.impl.Text
import quoi.api.abobaui.elements.impl.Text.Companion.shadow
import quoi.api.colour.Colour
import quoi.api.events.ChatEvent
import quoi.api.events.DungeonEvent
import quoi.api.events.KeyEvent
import quoi.api.events.MouseEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.skyblock.Island
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.Dungeon.allTeammatesNoSelf
import quoi.api.skyblock.dungeon.Dungeon.dungeonTeammatesNoSelf
import quoi.api.skyblock.dungeon.DungeonClass
import quoi.api.skyblock.dungeon.M7Phases
import quoi.api.skyblock.dungeon.P3Section
import quoi.module.Module
import quoi.module.settings.Setting.Companion.json
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.utils.Scheduler.wait
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.skyblock.item.ItemUtils.skyblockId
import quoi.utils.skyblock.player.LeapManager
import quoi.utils.skyblock.player.MovementUtils.stop
import quoi.utils.ui.hud.impl.TextHud

/**
 * TODO:
 * auto leap delay (?)
 * add other pre4 done detection methods
 * option to create custom fast/auto leaps (maybe; prob use custom triggers for that)
 * move some stuff to utils
 */

// Kyleen (maybe)
object AutoLeap : Module(
    "Auto Leap",
    desc = "Automatically leaps to predefined targets.",
    area = Island.Dungeon
) {
    private val leapMode by selector("Leap mode", "Name", listOf("Name", "Class"), "Leap mode for the module.").open()
    private val fastDelay by slider("Delay", 250L, 100L, 500L, 50L)
    private val preventMoving by switch("Prevent moving", true, desc = "Stops movement while leaping.")
    private val blockInputs by switch("Block inputs", true, desc = "Blocks keyboard and mouse input while leaping.")

    private val doorOpenerLeap by switch("Door opener leap", desc = "Outside of F7 boss, fast leap to the last wither door opener.")
    private val disableAfterBloodOpen by switch("Disable after Blood Open", desc = "Disables Door Fast Leap after the Blood Room has been opened.").childOf(::doorOpenerLeap)

    private val p1Leap by switch("Pre P2 leap", desc = "Leaps in P1.")
    private val p1Auto by switch("Auto", desc = "Automatically leaps after Maxor died.").childOf(::p1Leap)

    private val predevLeap by switch("Predev leap", desc = "Leaps before Storm dev.")
    private val predevAuto by switch("Auto", desc = "Automatically leaps before Storm's lightning.").childOf(::predevLeap)

    private val greenLeap by switch("Green pad leap", desc = "Leaps on green pad.")
    private val greenAuto by switch("Auto", desc = "Automatically leaps after the first Storm crush.").childOf(::greenLeap)

    private val yellowLeap by switch("Yellow pad leap", desc = "Leaps on yellow pad.")
    private val yellowAuto by switch("Auto", desc = "Automatically leaps after the second Storm crush.").childOf(::yellowLeap)

    private val purpleLeap by switch("Purple pad leap", desc = "Leaps on purple pad.")
    private val purpleAuto by switch("Auto", desc = "Automatically leaps when Storm is enraged.").childOf(::purpleLeap)

    private val pre4Leap by switch("Pre4 leap", desc="Leaps on Pre4 dev.")
    private val pre4Auto by switch("Auto", desc="Automatically leaps when Pre4 is done.").childOf(::pre4Leap)
    private val pre4LeapMelody by switch("Leap Melody", false, desc = "Leaps to the player doing Melody when Pre4 is done.").childOf(::pre4Leap)

    private val p3Leap by switch("P3 leap", desc = "Leaps in terminal phase.")
    private val p3Auto by switch("Auto", desc = "Automatically leaps when a section is finished.").childOf(::p3Leap)
    private val whenBlown by switch("Only when gate blown", desc = "Only leaps when gate is blown").childOf(::p3Auto)

    private val middleLeap by switch("Middle leap", desc = "Leaps in middle.")
    private val middleAuto by switch("Auto", desc = "Automatically leaps when instamid would send you to middle.").childOf(::middleLeap)

    private val p5Leap by switch("P5 leap", desc = "Leaps at P5 start.")
    private val p5Auto by switch("Auto", desc = "Automatically leaps after Necron died.").childOf(::p5Leap)

    private val relicLeap by switch("Relic leap", desc = "Leaps in relic.")
    private val relicAuto by switch("Auto", desc = "Automatically leaps after picking up a relic.").childOf(::relicLeap)

    private val p1Name by textInput("Target", "P1", length = 16).childOf(::p1Leap) { p1Leap && leapMode.selected == "Name" }.suggests { allTeammatesNoSelf }
    private val predevName by textInput("Target", "Predev", length = 16).childOf(::predevLeap) { predevLeap && leapMode.selected == "Name" }.suggests { allTeammatesNoSelf }
    private val greenName by textInput("Target", "Green", length = 16).childOf(::greenLeap) { greenLeap && leapMode.selected == "Name" }.suggests { allTeammatesNoSelf }
    private val yellowName by textInput("Target", "Yellow", length = 16).childOf(::yellowLeap) { yellowLeap && leapMode.selected == "Name" }.suggests { allTeammatesNoSelf }
    private val purpleName by textInput("Target", "Purple", length = 16).childOf(::purpleLeap) { purpleLeap && leapMode.selected == "Name" }.suggests { allTeammatesNoSelf }
    private val pre4Name by textInput("Target", "Pre4", length=16).childOf(::predevLeap) { pre4Leap && leapMode.selected == "Name" }.suggests { allTeammatesNoSelf }
    private val s1Name by textInput("S1 leap", "S1", length = 16).childOf(::p3Leap) { p3Leap && leapMode.selected == "Name" }.suggests { allTeammatesNoSelf }
    private val s2Name by textInput("S2 leap", "S2", length = 16).childOf(::p3Leap) { p3Leap && leapMode.selected == "Name" }.suggests { allTeammatesNoSelf }
    private val s3Name by textInput("S3 leap", "S3", length = 16).childOf(::p3Leap) { p3Leap && leapMode.selected == "Name" }.suggests { allTeammatesNoSelf }
    private val s4Name by textInput("S4 leap", "S4", length = 16).childOf(::p3Leap) { p3Leap && leapMode.selected == "Name" }.suggests { allTeammatesNoSelf }
    private val middleName by textInput("Target", "Middle", length = 16).childOf(::middleLeap) { middleLeap && leapMode.selected == "Name" }.suggests { allTeammatesNoSelf }
    private val p5Name by textInput("Target", "P5", length = 16).childOf(::p5Leap) { p5Leap && leapMode.selected == "Name" }.suggests { allTeammatesNoSelf }
    private val relicName by textInput("Target", "Relic", length = 16).childOf(::relicLeap) { relicLeap && leapMode.selected == "Name" }.suggests { allTeammatesNoSelf }

    private val p1Class by selector("Target", DungeonClass.Unknown).json("P1 leap class").childOf(::p1Leap) { p1Leap && leapMode.selected == "Class" }
    private val predevClass by selector("Target", DungeonClass.Unknown).json("Predev leap class").childOf(::predevLeap) { predevLeap && leapMode.selected == "Class" }
    private val greenClass by selector("Target", DungeonClass.Unknown).json("Green leap class").childOf(::greenLeap) { greenLeap && leapMode.selected == "Class" }
    private val yellowClass by selector("Target", DungeonClass.Unknown).json("Yellow leap class").childOf(::yellowLeap) { yellowLeap && leapMode.selected == "Class" }
    private val purpleClass by selector("Target", DungeonClass.Unknown).json("Purple leap class").childOf(::purpleLeap) { purpleLeap && leapMode.selected == "Class" }
    private val pre4Class by selector("Target", DungeonClass.Unknown).json("Pre4 leap class").childOf(::pre4Leap) { pre4Leap && leapMode.selected == "Class" }
    private val s1Class by selector("S1 leap", DungeonClass.Healer).json("S1 leap class").childOf(::p3Leap) { p3Leap && leapMode.selected == "Class" }
    private val s2Class by selector("S2 leap", DungeonClass.Archer).json("S2 leap class").childOf(::p3Leap) { p3Leap && leapMode.selected == "Class" }
    private val s3Class by selector("S3 leap", DungeonClass.Mage).json("S3 leap class").childOf(::p3Leap) { p3Leap && leapMode.selected == "Class" }
    private val s4Class by selector("S4 leap", DungeonClass.Mage).json("S4 leap class").childOf(::p3Leap) { p3Leap && leapMode.selected == "Class" }
    private val middleClass by selector("Target", DungeonClass.Unknown).json("Middle leap class").childOf(::middleLeap) { middleLeap && leapMode.selected == "Class" }
    private val p5Class by selector("Target", DungeonClass.Unknown).json("P5 leap class").childOf(::p5Leap) { p5Leap && leapMode.selected == "Class" }
    private val relicClass by selector("Target", DungeonClass.Unknown).json("Relic leap class").childOf(::relicLeap) { relicLeap && leapMode.selected == "Class" }

    private val hud by textHud("Leap hud", Colour.WHITE, font = TextHud.HudFont.Minecraft) {
        visibleIf { this@AutoLeap.enabled && (preview || leapHudText != null) }
        dynamicTextSupplied(
            supplier = { leapHudText ?: "Leaping to &dHealer" },
            colour = colour,
            font = font,
            size = 18.px,
        ).shadow = shadow
    }.setting("Shows the current leap target.")

    private var lastClick = 0L
    private var arghCount = 0
    private var crystalCount = 0
    private var oofCount = 0
    private var leapHudText: String? = null
    private var leapHudShownAt = 0L
    private var blockingGameInput = false
    private var leapBlockId = 0
    private var melodyTarget: String? = null

    private val leapHudDuration = 1_500L
    private val leapBlockTimeout = 20
    private val melodyProgress = setOf("1/4", "2/4", "3/4", "25%", "50%", "75%")

    override fun onDisable() {
        resetLeapState()
        super.onDisable()
    }

    private val doNotLeapLocations = listOf(
        Vec3(108.5, 120.0, 94.5) to 1.5, // at ss
        Vec3(58.5, 109.0, 131.5) to 1.5, // at ee2
        Vec3(60.5, 132.0, 140.5) to 1.5, // at ee2 high / levers dev
        Vec3(69.5, 109.0, 122.5) to 1.0, // ee2 safe spot 1
        Vec3(48.5, 109.0, 122.5) to 1.0, // ee2 safe spot 2
        Vec3(2.5, 109.0, 104.5) to 1.5,  // at ee3
        Vec3(18.5, 121.0, 99.5) to 3.0,  // ee3 safe spot
        Vec3(1.5, 120.0, 77.5) to 3.0,   // arrows dev
        Vec3(58.5, 123.0, 122.5) to 0.3, // entering core
        Vec3(54.5, 115.0, 51.5) to 1.5   // at core
    )

    init {
        on<WorldEvent.Change> {
            arghCount = 0
            crystalCount = 0
            oofCount = 0
            resetLeapState()
        }

        on<TickEvent.Start> {
            if (leapHudText != null && System.currentTimeMillis() - leapHudShownAt > leapHudDuration) {
                leapHudText = null
            }
            if ((preventMoving || blockInputs) && blockingGameInput) player.stop()
        }

        on<KeyEvent.Press> {
            if (blockInputs && blockingGameInput) cancel()
        }

        on<KeyEvent.Release> {
            if (blockInputs && blockingGameInput) cancel()
        }

        on<MouseEvent.Scroll> {
            if (blockInputs && blockingGameInput) cancel()
        }

        on<MouseEvent.Move> {
            if (blockInputs && blockingGameInput) cancel()
        }

        on<DungeonEvent.SectionComplete> {
            if (!p3Leap || !p3Auto || !Dungeon.inP3) return@on
            if (whenBlown) return@on
            handleP3Leap(completedSection = Dungeon.p3Section)
        }

        on<DungeonEvent.SectionComplete.Full> {
            if (!p3Leap || !p3Auto || !Dungeon.inP3) return@on
            if (!whenBlown) return@on
            handleP3Leap(completedSection = Dungeon.p3Section)
        }

        on<ChatEvent.Packet> {
            if (!inF7Boss()) return@on

            if (pre4Leap && pre4LeapMelody && "Party" in message.noControlCodes && melodyProgress.any { it in message.noControlCodes }) {
                melodyTarget = Regex("""([A-Za-z0-9_]{3,16}):""")
                    .findAll(message.noControlCodes)
                    .lastOrNull()
                    ?.groupValues
                    ?.get(1)
            }

            if (message.noControlCodes.matches(Regex("\\[BOSS] Storm: (?:Oof|Ouch, that hurt!)"))) {
                oofCount++
                if (oofCount == 1 && greenLeap && greenAuto && isInGreenPad()) {
                    leapToConfigured(greenName, greenClass.selected)
                }
                if (oofCount == 2 && yellowLeap && yellowAuto && isInYellowPad()) {
                    leapToConfigured(yellowName, yellowClass.selected)
                }
            }

            if (message.noControlCodes == "⚠ Storm is enraged! ⚠" && purpleLeap && purpleAuto && isInPurplePad()) {
                leapToConfigured(purpleName, purpleClass.selected)
            }

            if (message.noControlCodes == "The Energy Laser is charging up!" && p1Leap && p1Auto) {
                crystalCount++
                if (crystalCount == 2 && isInP1()) {
                    leapToConfigured(p1Name, p1Class.selected)
                }
            }

            if (message.noControlCodes == "[BOSS] Storm: I'd be happy to show you what that's like!" && predevLeap && predevAuto && isInPredev()) {
                leapToConfigured(predevName, predevClass.selected)
            }

            if (message.noControlCodes == "[BOSS] Necron: ARGH!" && p5Leap && p5Auto) {
                arghCount++
                if (arghCount == 2 && isInP4()) {
                    leapToConfigured(p5Name, p5Class.selected)
                }
            }

            if (message.noControlCodes == "[BOSS] Necron: That's a very impressive trick. I guess I'll have to handle this myself." &&
                middleLeap && middleAuto && isOutsideMiddle()
            ) {
                leapToConfigured(middleName, middleClass.selected)
            }

            val relicPickup = Regex("^([A-Za-z0-9_]{3,16}) picked the Corrupted (?:\\w+) Relic!$").matchEntire(message.noControlCodes)
            if (relicPickup != null && relicLeap && relicAuto && relicPickup.groupValues[1] == player.name.string && isInRelic()) {
                leapToConfigured(relicName, relicClass.selected)
            }

            if (message.noControlCodes == "[BOSS] Storm: I should have known that I stood no chance." && p3Leap && p3Auto) {
                handleP3Leap(forceS1 = true)
            }
            if (message.noControlCodes == "The Core entrance is opening!" && p3Leap && p3Auto) {
                handleP3Leap(completedSection = P3Section.S4)
            }

            val pre4Done = Regex("""(\w+) completed a device! \((.*?)\)""").matchEntire(message.noControlCodes)
            if (pre4Done != null && pre4Done.groupValues[1] == player.name.string && pre4Leap && pre4Auto) {
                leapToPre4Target()
            }
        }

        on<MouseEvent.Click> {
            if (blockInputs && blockingGameInput) {
                cancel()
                return@on
            }

            if (button != 0 || !state) return@on
            if (player.mainHandItem.skyblockId !in setOf("INFINITE_SPIRIT_LEAP", "SPIRIT_LEAP")) return@on
            cancel()

            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClick < fastDelay) return@on

            val target = getFastLeapTarget()
            if (target != null) {
                leap(target)
            } else {
                if (!p3Leap) return@on
                if (Dungeon.getP3Section() == P3Section.Unknown && !Dungeon.inClear) return@on
                handleP3Leap(autoLeap = false)
            }
            lastClick = currentTime
        }
    }

    private fun leapToConfigured(name: String, clazz: DungeonClass) {
        when (leapMode.selected) {
            "Name" -> if (name.isNotBlank()) leap(name)
            "Class" -> if (clazz != DungeonClass.Unknown) leap(clazz)
        }
    }

    private fun leapToPre4Target() {
        val target = getPre4Target() ?: return
        leap(target)
    }

    private fun leap(target: Any) {
        LeapManager.leap(target) {
            showLeapHud(target)
            startLeapBlock()
        }
    }

    private fun startLeapBlock() {
        if (!preventMoving && !blockInputs) return

        val blockId = ++leapBlockId
        blockingGameInput = true

        scope.launch {
            repeat(leapBlockTimeout) {
                wait(1)
                if (blockId != leapBlockId) return@launch
            }
            if (blockId == leapBlockId) blockingGameInput = false
        }.invokeOnCompletion {
            if (blockId == leapBlockId) blockingGameInput = false
        }
    }

    private fun showLeapHud(target: Any) {
        leapHudText = "Leaping to ${formatTarget(target)}"
        leapHudShownAt = System.currentTimeMillis()
    }

    private fun clearLeapHud() {
        leapHudText = null
    }

    private fun resetLeapState() {
        leapBlockId++
        blockingGameInput = false
        melodyTarget = null
        clearLeapHud()
    }

    private fun formatTarget(target: Any): String {
        return when (target) {
            is DungeonClass -> "§${target.colourCode}${target.name}"
            is String -> {
                val clazz = dungeonTeammatesNoSelf.firstOrNull { it.name.equals(target, true) }?.clazz
                "§${clazz?.colourCode ?: 'f'}$target"
            }
            else -> target.toString()
        }
    }

    private inline fun TextHud.Scope.dynamicTextSupplied(
        crossinline supplier: () -> Any?,
        colour: Colour,
        font: quoi.utils.ui.rendering.Font,
        pos: Positions = at(),
        size: quoi.api.abobaui.constraints.Constraint.Size
    ): ElementScope<Text> = object : Text(supplier().toString(), font, colour, pos, size) {
        override fun draw() {
            text = supplier().toString()
            this.colour = this@dynamicTextSupplied.colour
            super.draw()
        }
    }.scope { }

    private fun configuredTarget(name: String, clazz: DungeonClass): Any? {
        return when (leapMode.selected) {
            "Name" -> name.takeIf { it.isNotBlank() }
            "Class" -> clazz.takeIf { it != DungeonClass.Unknown }
            else -> null
        }
    }

    private fun getPre4Target(): Any? {
        return if (pre4LeapMelody) {
            melodyTarget ?: configuredTarget(pre4Name, pre4Class.selected)
        } else {
            configuredTarget(pre4Name, pre4Class.selected)
        }
    }

    private fun inF7Boss() = Dungeon.inBoss && Dungeon.isFloor(7)

    // TODO: Replace with Vec3 ?
    private fun inBox(x1: Double, x2: Double, y1: Double, y2: Double, z1: Double, z2: Double): Boolean =
        player.x in x1..x2 && player.y in y1..y2 && player.z in z1..z2

    private fun isInP1() = Dungeon.getF7Phase() == M7Phases.P1
    private fun isInPredev() = Dungeon.p3Section == P3Section.Unknown && Dungeon.getF7Phase() == M7Phases.P3
    private fun isInP4() = player.y >= 55.0 && Dungeon.getF7Phase() == M7Phases.P4
    private fun isInRelic() = Dungeon.getF7Phase() == M7Phases.P5
    private fun isInGreenPad() = inBox(24.0, 41.0, 170.0, 172.0, 4.0, 21.0) // TODO: Vec3 ?
    private fun isInYellowPad() = inBox(24.0, 41.0, 170.0, 172.0, 86.0, 103.0) // TODO: Vec3 ?
    private fun isInPurplePad() = inBox(95.0, 123.0, 164.0, 172.0, 86.0, 103.0) // TODO: Vec3 ?
    private fun isInMiddle() = inBox(47.0, 61.0, 64.0, 75.0, 69.0, 83.0) // TODO: Vec3 ?
    private fun isAtPre4() = inBox(62.0, 65.0, 127.0, 130.0, 34.0, 37.0) // TODO: Vec3 ?
    private fun isOutsideMiddle() = Dungeon.getF7Phase() == M7Phases.P4 && !isInMiddle()

    private fun getFastLeapTarget(): Any? {
        if (!Dungeon.inBoss) {
            return Dungeon.doorOpener.takeIf { doorOpenerLeap && it != "Unknown" && (!disableAfterBloodOpen || !Dungeon.bloodOpen) }
        }

        if (!inF7Boss()) {
            return null
        }

        return when {
            predevLeap && isInPredev() -> configuredTarget(predevName, predevClass.selected)
            relicLeap && isInRelic() -> configuredTarget(relicName, relicClass.selected)
            p1Leap && isInP1() -> configuredTarget(p1Name, p1Class.selected)
            p5Leap && isInMiddle() -> configuredTarget(p5Name, p5Class.selected)
            greenLeap && isInGreenPad() -> configuredTarget(greenName, greenClass.selected)
            yellowLeap && isInYellowPad() -> configuredTarget(yellowName, yellowClass.selected)
            purpleLeap && isInPurplePad() -> configuredTarget(purpleName, purpleClass.selected)
            middleLeap && isOutsideMiddle() -> configuredTarget(middleName, middleClass.selected)
            pre4Leap && isAtPre4() -> getPre4Target()
            else -> null
        }
    }

    private fun handleP3Leap(completedSection: P3Section? = null, forceS1: Boolean = false, autoLeap: Boolean = true) {
        if (autoLeap) {
            if (Dungeon.getP3Section() == P3Section.Unknown) return
            for ((pos, distSqr) in doNotLeapLocations) {
                if (player.distanceToSqr(pos) <= distSqr) return
            }
        }

        val targetSection = if (forceS1) {
            P3Section.S1
        } else if (completedSection != null && completedSection != P3Section.Unknown) {
            when (completedSection) {
                P3Section.S1 -> P3Section.S2
                P3Section.S2 -> P3Section.S3
                P3Section.S3 -> P3Section.S4
                P3Section.S4 -> P3Section.S4
                else -> return
            }
        } else {
            Dungeon.getP3Section()
        }

        val (name, clazz) = when (targetSection) {
            P3Section.S1 -> s1Name to s1Class.selected
            P3Section.S2 -> s2Name to s2Class.selected
            P3Section.S3 -> s3Name to s3Class.selected
            P3Section.S4 -> s4Name to s4Class.selected
            else -> return
        }

        when (leapMode.selected) {
            "Name" -> leap(name)
            "Class" -> leap(clazz)
        }
    }
}
