package quoi.utils

import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundResetScorePacket
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket
import net.minecraft.network.protocol.game.ClientboundSetScorePacket
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.PlayerTeam
import quoi.QuoiMod.mc
import quoi.annotations.Init
import quoi.api.events.PacketEvent
import quoi.api.events.core.EventListener
import quoi.api.events.core.Priority
import quoi.api.events.core.on

/**
 * modified NoammAddons (CC0-1.0)
 * original: https://github.com/Noamm9/NoammAddons/blob/b631da0640f9ca0ae7b35f4d37dc3fc801a36e97/src/main/kotlin/com/github/noamm9/utils/ScoreboardUtils.kt
 */
@Init
object ScoreboardUtils : EventListener {
    private var cachedLines: List<Component> = emptyList()
    private var listDirty = true

    init {
        on<PacketEvent.ReceivedPost>(Priority.HIGHEST) {
            when (packet) {
                is ClientboundSetScorePacket, is ClientboundSetObjectivePacket,
                is ClientboundSetDisplayObjectivePacket, is ClientboundResetScorePacket,
                is ClientboundSetPlayerTeamPacket -> listDirty = true
            }
        }
    }

    fun getLines(): List<Component> {
        if (listDirty) {
            cachedLines = fetchScoreboard()
            listDirty = false
        }
        return cachedLines
    }

    private fun fetchScoreboard(): List<Component> {
        val scoreboard = mc.level?.scoreboard ?: return emptyList()
        val objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR) ?: return emptyList()
        return buildList {
            add(objective.displayName)
            scoreboard.listPlayerScores(objective).sortedByDescending { it.value }.take(15).forEach {
                val name = it.ownerName().string
                add(PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(name), Component.literal(name)).copy())
            }
        }
    }
}
