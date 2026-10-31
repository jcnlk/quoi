package quoi.module.impl.floor7.autoleap

import net.minecraft.world.phys.AABB
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.Floor7
import quoi.api.skyblock.dungeon.enums.Phase
import quoi.api.skyblock.dungeon.enums.Stage
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.group.SettingGroup

abstract class LeapSettings(parent: Module, name: String, val automatic: Boolean) : SettingGroup(parent, name) {
    private val doorOpenerLeap by switch("Door opener leap", desc = if (automatic) "Leap when a teammate opens a Wither or Blood door." else "Leap to the last Wither or Blood door opener during clear.")
    private val disableAfterBloodOpen by switch("Disable after Blood Open", desc = "Stops door opener leaps once Blood is open.").childOf(::doorOpenerLeap)

    protected val crystal = LeapTarget(
        this, "Crystal leap", "Leap from either P1 crystal platform.", "Leap on Energy Crystal pickup."
    ) { Floor7.inPhase(Phase.P1) && crystalPlatforms.any { at(it) } }
    protected val p1 = LeapTarget(
        this, "P1 leap", "Leap from P1. Enabled Crystal leap takes priority on crystal platforms.", "Leap on the second Energy Laser charge in P1.",
        defaultName = "P1"
    ) { Floor7.inPhase(Phase.P1) && Floor7.inPhaseAt(Phase.P1) && (!crystal.enabled || !crystal.matches()) }
    protected val predev = LeapTarget(
        this, "Predev leap", "Leap from P3 while Maxor or Storm is still active.", "Leap on Storm's lightning dialogue while in P3."
    ) { Floor7.inPhaseAt(Phase.P3) && Floor7.inPhase(Phase.P1, Phase.P2) }
    protected val i4: LeapTarget = LeapTarget(
        this, "I4 leap", "Leap from the I4 device.", "Leap after completing I4 during section 1 or 2.",
        nameOverride = { Leaps.melodyTarget.takeIf { i4LeapMelody } }
    ) { Floor7.inPhase(Phase.P3) && at(AABB(62.0, 127.0, 34.0, 65.0, 130.0, 37.0)) }
    private val i4LeapMelody by switch("Leap Melody", desc = "Uses the last Melody progress sender in party chat, otherwise the I4 target.").childOf(i4.toggle)

    protected val p3 = +switch("P3 leap", desc = if (automatic) "Leap when a section's objectives finish, unless you are ahead or at I4." else "Leap to your P3 section's target when no other leap applies.")
    private val sections = (1..4).map {
        LeapTarget(this, "S$it leap", "Target when leaving or completing section $it.", parent = p3) { Floor7.inPhase(Phase.P3) }
    }

    protected val middle = LeapTarget(
        this, "Middle leap", "Leap from P4, outside the middle platform.", "Leap on Necron's 'Goodbye.' while outside middle in P4."
    ) { Floor7.inPhase(Phase.P4) && Floor7.inPhaseAt(Phase.P4) && !at(middlePlatform) }
    protected val p4 = LeapTarget(
        this, "P4 leap", "Leap from the P4 middle platform.", "Leap once at about 5% Necron HP while in P4."
    ) { Floor7.inPhase(Phase.P4) && Floor7.inPhaseAt(Phase.P4) && (automatic || at(middlePlatform)) }
    protected val relic = LeapTarget(
        this, "Relic leap", "Leap during M7 P5.", "Leap when a relic enters hotbar slot 9 in P5."
    ) { Floor7.inPhase(Phase.P5) && Floor7.inPhaseAt(Phase.P5) }

    private val middlePlatform = AABB(47.0, 64.0, 69.0, 62.0, 75.0, 84.0)
    private val crystalPlatforms = listOf(
        AABB(78.0, 233.0, 46.0, 87.0, 243.0, 55.0),
        AABB(60.0, 233.0, 46.0, 69.0, 243.0, 55.0)
    )

    protected fun leapToDoorOpener(name: String): Boolean {
        if (!doorOpenerLeap || name == player.name.string || disableAfterBloodOpen && Dungeon.bloodOpen) return false
        Leaps.leap(name)
        return true
    }

    protected fun leapToSection(stage: Stage) {
        val current = Floor7.getStageAt()
        if (current != Stage.Unknown && current.number <= stage.number) sections.getOrNull(stage.number - 1)?.leap()
    }

    protected fun at(box: AABB): Boolean = box.contains(player.position())
}
