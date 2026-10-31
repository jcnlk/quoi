package quoi.module.impl.floor7.autoleap.impl

import net.minecraft.world.phys.AABB
import quoi.api.events.MouseEvent
import quoi.api.events.core.on
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.Floor7
import quoi.api.skyblock.dungeon.enums.Phase
import quoi.module.Module
import quoi.module.impl.floor7.autoleap.LeapSettings
import quoi.module.impl.floor7.autoleap.LeapTarget
import quoi.utils.skyblock.item.ItemUtils.skyblockId

class FastLeap(parent: Module) : LeapSettings(parent, "Fast Leap", automatic = false) {
    private val yellow = LeapTarget(
        this, "Yellow pad leap", "Leap from the yellow pad."
    ) { Floor7.inPhase(Phase.P2) && at(AABB(24.0, 170.0, 86.0, 41.0, 172.0, 103.0)) }
    private val purple = LeapTarget(
        this, "Purple pad leap", "Leap from the purple pad."
    ) { Floor7.inPhase(Phase.P2) && at(AABB(106.0, 170.0, 86.0, 125.0, 172.0, 103.0)) }
    private val betweenPads = LeapTarget(
        this, "Between PY pad leap", "Leap from between the purple and yellow pads."
    ) { Floor7.inPhase(Phase.P2) && at(AABB(41.0, 163.0, 86.0, 106.0, 172.0, 103.0)) }
    private val stormDeath = LeapTarget(
        this, "Storm death leap", "Leap from P2, outside the pads and the area between them."
    ) { Floor7.inPhase(Phase.P2) && Floor7.inPhaseAt(Phase.P2) && !purple.matches() && !yellow.matches() && !betweenPads.matches() }
    private var lastClick = 0L
    private val spiritLeapIds = setOf("INFINITE_SPIRIT_LEAP", "SPIRIT_LEAP")
    private val targets = listOf(crystal, predev, relic, p1, p4, yellow, purple, betweenPads, stormDeath, middle, i4)

    init {
        on<MouseEvent.Click> {
            if (button != 0 || !state || player.mainHandItem.skyblockId !in spiritLeapIds) return@on
            cancel()
            val now = System.currentTimeMillis()
            if (now - lastClick < 250) return@on
            if (!attemptLeap()) {
                if (!p3.value || !Floor7.inF7Boss) return@on
                leapToSection(Floor7.getStageAt())
            }
            lastClick = now
        }
    }

    private fun attemptLeap(): Boolean {
        if (!Dungeon.inBoss) {
            val opener = Dungeon.doorOpener
            return opener != "Unknown" && leapToDoorOpener(opener)
        }
        if (!Floor7.inF7Boss) return false
        val target = targets.firstOrNull { it.enabled && it.matches() } ?: return false
        return target.leap()
    }
}
