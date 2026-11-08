package quoi.module.impl.render

import net.minecraft.world.entity.player.Player
import quoi.api.events.EntityEvent
import quoi.api.events.RenderEvent
import quoi.api.events.core.on
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.utils.EntityUtils.colourFromDistance
import quoi.utils.EntityUtils.interpolatedBox
import quoi.utils.EntityUtils.playerEntitiesNoSelf
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.WorldUtils

@Suppress("UNNECESSARY_SAFE_CALL")
object PlayerESP : Module(
    "Player ESP",
    desc = "Highlights players through walls."
) {
    private var specific by switch("Specific player")
    private var specificName by textInput("Name", length = 16, placeholder = "Player name").childOf(::specific).suggests { WorldUtils.players.map { it.profile.name } }

    private val tracer = tracer(customColour = true)
    private val highlight = highlight(customColour = true, customFillColour = true, aabbOffset = true)

    val targetedPlayerName: String
        get() = specificName.trim()

    private fun matchesFilters(entityName: String?, displayName: String?): Boolean {
        if (!specific) return true

        val target = targetedPlayerName
        if (target.isBlank()) return false

        return listOfNotNull(entityName, displayName).any { it.noControlCodes.trim().equals(target, ignoreCase = true) }
    }

    init {
        on<RenderEvent.World> {
            playerEntitiesNoSelf.forEach { entity ->
                if (!matchesFilters(entity.name?.string, entity.displayName?.string)) return@forEach
                val colour = entity.colourFromDistance
                highlight.draw(ctx, entity.interpolatedBox, colour, colour)
                tracer.draw(ctx, entity, colour)
            }
        }

        on<EntityEvent.ForceGlow> {
            if (entity !is Player || entity == player || entity.uuid.version() != 4) return@on
            if (!level.players().contains(entity)) return@on
            if (!matchesFilters(entity.name?.string, entity.displayName?.string)) return@on
            highlight.draw(this, entity.colourFromDistance)
        }
    }
}
