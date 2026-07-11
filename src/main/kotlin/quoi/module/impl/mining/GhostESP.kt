package quoi.module.impl.mining

import net.minecraft.world.entity.monster.Creeper
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.events.RenderEvent
import quoi.api.events.core.on
import quoi.api.skyblock.location.Island
import quoi.module.Module
import quoi.utils.EntityUtils.getEntities
import quoi.utils.EntityUtils.interpolatedBox

object GhostESP : Module(
    "Ghost ESP",
    area = Island.DwarvenMines,
    subarea = "The Mist"
) {
    private val hideGhost by switch("Hide Ghost")

    private val highlight = highlight(
        colour = Colour.RGB(0, 200, 200),
        fillColour = Colour.RGB(0, 200, 200).withAlpha(0.5f),
    )

    init {
        on<RenderEvent.Entity> {
            if (hideGhost && entity is Creeper && isGhost(entity)) cancel()
        }

        on<RenderEvent.World> {
            getEntities<Creeper>().filter(::isGhost).forEach { ghost ->
                highlight.draw(ctx, ghost.interpolatedBox)
            }
        }
    }

    @JvmStatic
    fun isGhost(creeper: Creeper): Boolean =
        creeper.y in 76.0..100.0 && creeper.isInvisible && creeper.isPowered && creeper.health != 20f
}
