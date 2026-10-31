//~ swing_packet

package quoi.module.impl.misc.slayers.blaze

import net.minecraft.world.InteractionHand
import quoi.api.events.*
import quoi.api.events.core.Subscription
import quoi.api.events.core.on
import quoi.api.events.core.once
import quoi.module.impl.misc.slayers.QuestState
import quoi.module.impl.misc.slayers.Slayers
import quoi.module.settings.group.ToggleableGroup
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.skyblock.item.ItemUtils.lore
import quoi.utils.skyblock.player.PlayerUtils.rightClick
import quoi.utils.skyblock.player.SwapManager

import net.minecraft.network.protocol.game.ServerboundSwingPacket

object AutoAttune : ToggleableGroup(BlazeSlayer, "Auto attune") {
    private var swapTask: Subscription<TickEvent.Start>? = null
    private val pendingAttunements = mutableMapOf<String, Pair<Attunement, Long>>()

    init {
        on<PacketEvent.Sent, ServerboundSwingPacket> {
            //? if <26.3
            if (packet.hand != InteractionHand.MAIN_HAND) return@on
            if (swapTask != null) return@on
            if (BlazeSlayer.attune == null) return@on

            swapTask = once<TickEvent.Start> {
                swapTask = null
                val attunement = BlazeSlayer.attune ?: return@once
                val result = SwapManager.swapById(*attunement.daggers)

                if (!result.success) return@once

                val attuned = player.mainHandItem.lore
                    ?.map { it.noControlCodes }
                    ?.firstOrNull { it.startsWith("Attuned: ") }
                    ?.removePrefix("Attuned: ")
                    ?.trim()
                    ?.uppercase()

                val family = attunement.daggers.first()
                val now = System.currentTimeMillis()
                pendingAttunements[family]?.let { (expected, clickedAt) ->
                    if (attuned != expected.name && now - clickedAt < 1_000L) return@once
                    pendingAttunements.remove(family)
                }

                if (attuned != attunement.name) {
                    pendingAttunements[family] = attunement to now
                    player.rightClick()
                }
            }
        }

        Slayers.on<SlayerEvent.State> { onDisable() }
        Slayers.on<WorldEvent.Change> { onDisable() }

        on<ChatEvent.Packet> {
            if (unformatted.startsWith("Strike using the") || unformatted.startsWith("Your hit was reduced by Hellion Shield!")) cancel()
        }
    }

    override fun onDisable() {
        swapTask?.unregister()
        swapTask = null
        pendingAttunements.clear()
    }

    override val running: Boolean
        get() = super.running && Slayers.questState == QuestState.KILLING
}
