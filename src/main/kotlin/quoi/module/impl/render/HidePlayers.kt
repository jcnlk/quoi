package quoi.module.impl.render

import quoi.api.events.core.on
import net.minecraft.world.entity.player.Player
import quoi.api.events.RenderEvent
import quoi.api.skyblock.location.Island
import quoi.api.skyblock.location.Location
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.M7Phases
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.visibleIf

object HidePlayers : Module(
    "Hide Players",
) {
    private val hideAll by switch("Hide all", desc = "Hides all players, regardless of distance.")
    private val distance by slider("Distance", 3.0, 0.0, 32.0, 0.5, desc = "The number of blocks away to hide players.", unit = " blocks")
        .visibleIf { !hideAll }
    private val clickThrough by switch("Click Through", desc = "Allows clicking through players.")
    private val dungeonOnly by switch("Dungeon only", desc = "Only hides players in dungeons.")
    private val bossOnly by switch("Boss only", desc = "Only hides players in boss.")
    private val onlyDevs by switch("Only at Devs", desc = "Only hides players when standing at ss or fourth device.")

    init {
        on<RenderEvent.Entity> {
            if (Location.currentArea.isArea(Island.SinglePlayer)) return@on
            if (dungeonOnly && !Dungeon.inDungeons) return@on
            if (bossOnly && !Dungeon.inBoss) return@on

            val entity = entity

            if (
                entity !is Player ||
                entity.uuid.version() == 2 ||
                entity == player ||
                clickThrough ||
                onlyDevs && !isAtDevs()
            ) return@on

            if (hideAll || entity.distanceTo(player) <= distance) cancel()
        }
    }

    private fun isAtDevs(): Boolean {
        if (Dungeon.getF7Phase() != M7Phases.P3) return false

        return player.distanceToSqr(108.63, 120.0, 94.0) <= 1.8 * 1.8 ||
            player.distanceToSqr(63.5, 127.0, 35.5) <= 1.8 * 1.8
    }
}
