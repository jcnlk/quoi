package quoi.module.impl.floor7

import net.minecraft.world.phys.AABB
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.events.RenderEvent
import quoi.api.events.core.on
import quoi.api.skyblock.dungeon.Stage
import quoi.api.skyblock.location.Island
import quoi.api.skyblock.location.invoke
import quoi.module.Module

object P4PlatformHighlight : Module(
    "P4 Platform Highlight",
    desc = "Highlights 3x3 area to mine after Goldor dies.",
    area = Island.Dungeon(7, inBoss = true, stage = Stage.S5)
) {
    private val platformHighlight = highlight(
        colour = Colour.CYAN,
        fillColour = Colour.CYAN.withAlpha(60),
        glow = false,
        defaultStyle = "Filled",
    )

    private val healerBox = AABB(53.0, 63.0, 113.0, 56.0, 64.0, 116.0)

    init {
        on<RenderEvent.World> {
            platformHighlight.draw(ctx, healerBox)
        }
    }
}
