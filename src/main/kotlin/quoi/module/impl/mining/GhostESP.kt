package quoi.module.impl.mining

import net.minecraft.world.entity.monster.Creeper
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.events.EntityEvent
import quoi.api.events.RenderEvent
import quoi.api.events.core.on
import quoi.api.skyblock.location.Island
import quoi.module.Module
import quoi.utils.EntityUtils.getEntities
import quoi.utils.EntityUtils.interpolatedBox

/**
 * TODO:
 *  exclude wither cloak creepers
 */

object GhostESP : Module(
    "Ghost ESP",
    area = Island.DwarvenMines,
    subarea = "The Mist"
) {
    private val highlight = highlight(
        colour = Colour.RGB(0, 200, 200),
        fillColour = Colour.RGB(0, 200, 200).withAlpha(0.5f),
    )

    init {
        on<RenderEvent.World> {
            getEntities<Creeper>().filter(::isGhost).forEach { ghost ->
                highlight.draw(ctx, ghost.interpolatedBox)
            }
        }

        on<EntityEvent.ForceGlow> {
            val ghost = entity as? Creeper ?: return@on
            if (isGhost(ghost)) highlight.draw(this)
        }
    }

    @JvmStatic
    fun isGhost(creeper: Creeper): Boolean =
        creeper.y in 100.0..120.0 && creeper.isInvisible && creeper.isPowered
}
