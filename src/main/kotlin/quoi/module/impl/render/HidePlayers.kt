package quoi.module.impl.render

import net.minecraft.world.entity.player.Player
import quoi.api.events.EntityEvent
import quoi.api.events.RenderEvent
import quoi.api.events.core.on
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.Floor7Utils
import quoi.api.skyblock.dungeon.Phase
import quoi.api.skyblock.location.Island
import quoi.api.skyblock.location.Location
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.visibleIf

object HidePlayers : Module(
    "Hide Players",
) {
    private val hideAll by switch("Hide all", desc = "Hides all players, regardless of distance.")
    private val distance by slider("Distance", 3.0, 0.0, 32.0, 0.5, desc = "The number of blocks away to hide players.", unit = " blocks").visibleIf { !hideAll }
    private val clickThrough by switch("Click Through", desc = "Allows clicking through players.")
    private val dungeonOnly by switch("Dungeon only", desc = "Only hides players in dungeons.")
    private val bossOnly by switch("Boss only", desc = "Only hides players in boss.")
    private val onlyDevs by switch("Only at Devs", desc = "Only hides players when standing at ss or fourth device.")

    init {
        on<RenderEvent.Entity> {
            val target = entity as? Player ?: return@on
            if (shouldHide(target)) cancel()
        }

        on<EntityEvent.Pick> {
            if (!clickThrough) return@on

            val target = entity as? Player ?: return@on
            if (shouldHide(target)) cancel()
        }
    }

    private fun shouldHide(target: Player): Boolean {
        if (Location.currentArea.isArea(Island.SinglePlayer)) return false
        if (dungeonOnly && !Dungeon.inDungeons) return false
        if (bossOnly && !Dungeon.inBoss) return false
        if (onlyDevs && !isAtDevs()) return false
        if (target.uuid.version() == 2) return false
        if (target == player) return false

        return hideAll || target.distanceTo(player) <= distance
    }

    private fun isAtDevs(): Boolean {
        if (!Floor7Utils.inPhaseAt(Phase.P3)) return false

        return player.distanceToSqr(108.63, 120.0, 94.0) <= 1.8 * 1.8 ||
            player.distanceToSqr(63.5, 127.0, 35.5) <= 1.8 * 1.8
    }
}
