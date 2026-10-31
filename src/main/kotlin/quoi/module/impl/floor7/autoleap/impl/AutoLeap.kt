package quoi.module.impl.floor7.autoleap.impl

import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket
import quoi.api.events.BossBarEvent
import quoi.api.events.ChatEvent
import quoi.api.events.DungeonEvent
import quoi.api.events.PacketEvent
import quoi.api.events.core.on
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.Floor7
import quoi.api.skyblock.dungeon.enums.Phase
import quoi.api.skyblock.dungeon.enums.Stage
import quoi.module.Module
import quoi.module.impl.floor7.autoleap.LeapSettings
import quoi.module.impl.floor7.autoleap.LeapTarget
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.utils.skyblock.item.ItemUtils.skyblockId

class AutoLeap(parent: Module) : LeapSettings(parent, "Auto Leap", automatic = true) {
    private val firstCrush = LeapTarget(
        this, "First crush leap", "Leap after Storm's first crush, from any position."
    )
    private val secondCrush = LeapTarget(
        this, "Second crush leap", "Leap after Storm's second crush, from any position."
    )
    private val stormDeath = LeapTarget(
        this, "Storm death leap", "Leap on Storm's death message, from any position."
    )
    private val stormEnrage = LeapTarget(
        this, "Storm enrage leap", "Leap when Storm becomes enraged while P2 is active."
    ) { Floor7.inPhase(Phase.P2) }
    private val whenBlown by switch("Only when gate blown", desc = "Also waits for the gate, or the Core entrance in section 4.").childOf(p3)
    private var crystalCount = 0
    private var crushCount = 0
    private var p4Leaped = false
    private val relicIds = setOf("GREEN_KING_RELIC", "PURPLE_KING_RELIC", "BLUE_KING_RELIC", "ORANGE_KING_RELIC", "RED_KING_RELIC")

    init {
        on<DungeonEvent.StageComplete> { if (!whenBlown) completeSection(stage) }
        on<DungeonEvent.StageComplete.Full> { if (whenBlown) completeSection(stage) }

        on<ChatEvent.Packet> {
            if (!Floor7.inF7Boss) return@on
            when (unformatted) {
                "[BOSS] Storm: Oof", "[BOSS] Storm: Ouch, that hurt!" -> when (++crushCount) {
                    1 -> firstCrush.leap()
                    2 -> secondCrush.leap()
                }
                "The Energy Laser is charging up!" -> if (p1.enabled && ++crystalCount == 2) p1.leap()
                "⚠ Storm is enraged! ⚠" -> stormEnrage.leap()
                "[BOSS] Storm: I'd be happy to show you what that's like!" -> predev.leap()
                "[BOSS] Necron: Goodbye." -> middle.leap()
                "[BOSS] Storm: I should have known that I stood no chance." -> stormDeath.leap(checkPosition = false)
                "${player.name.string} picked up an Energy Crystal!" -> crystal.leap()
                else -> if (Floor7.inStage(Stage.S1, Stage.S2) && unformatted.startsWith("${player.name.string} completed a device!")) {
                    i4.leap()
                }
            }
        }

        on<BossBarEvent.Update> {
            if (!p4.enabled || p4Leaped || !Floor7.inF7Boss || !Floor7.inPhaseAt(Phase.P4)) return@on
            if (progress !in 0.001f..0.051f || !unformatted.contains("necron", ignoreCase = true)) return@on
            p4Leaped = true
            p4.leap(checkPosition = false)
        }

        on<PacketEvent.ReceivedPost, ClientboundContainerSetSlotPacket> {
            if (packet.containerId == 0 && packet.slot == 44 && packet.item.skyblockId in relicIds) relic.leap()
        }

        on<DungeonEvent.DoorOpen> {
            if (!Dungeon.inBoss) leapToDoorOpener(opener)
        }
    }

    private fun completeSection(stage: Stage) {
        if (p3.value && Floor7.inPhaseAt(Phase.P3) && !i4.matches()) leapToSection(stage)
    }

    fun reset() {
        crystalCount = 0
        crushCount = 0
        p4Leaped = false
    }
}
