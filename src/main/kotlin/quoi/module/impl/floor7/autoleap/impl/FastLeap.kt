package quoi.module.impl.floor7.autoleap.impl

import quoi.api.events.MouseEvent
import quoi.api.events.core.on
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.Floor7
import quoi.module.Module
import quoi.module.impl.floor7.autoleap.LeapSettings
import quoi.utils.skyblock.item.ItemUtils.skyblockId

class FastLeap(parent: Module) : LeapSettings(parent, "Fast Leap", automatic = false) {
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
        return target.leap(checkPosition = false)
    }
}
