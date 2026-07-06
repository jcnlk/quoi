package quoi.module.impl.render

import quoi.api.events.EntityEvent
import quoi.api.events.RenderEvent
import quoi.api.events.core.on
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.utils.ChatUtils.modMessage
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
    private val ironmenOnly by switch("Ir*nmen only")

    private var specific by switch("Specific player")
    private var specificName by textInput("Name", length = 16, placeholder = "Player name").childOf(::specific)
        .suggests { WorldUtils.players.map { it.profile.name } }

    private val tracer = tracer(customColour = true)
    private val highlight = highlight(customColour = true, customFillColour = true, aabbOffset = true)

    val targetedPlayerName: String
        get() = specificName.trim()

    fun setTargetedPlayer(name: String) {
        specific = name.isNotBlank()
        specificName = name.trim()
    }

    private fun matchesFilters(entityName: String?, displayName: String?): Boolean {
        if (ironmenOnly && displayName?.contains("♲") == false) return false
        if (!specific) return true

        val target = targetedPlayerName
        if (target.isBlank()) return false

        return entityName.noControlCodes.trim().equals(target, true) ||
            displayName.noControlCodes.trim().equals(target, true)
    }

    init {
        command.sub("playeresp") { name: String ->
            if (name.equals("clear", true)) {
                setTargetedPlayer("")
                modMessage("Player ESP target cleared.")
            } else {
                setTargetedPlayer(name)
                if (!enabled) toggle()
                modMessage("Player ESP now targets §b$targetedPlayerName§r.")
            }
        }.description("Targets Player ESP to a specific player and enables it.")
            .suggests("name") { WorldUtils.players.map { it.profile.name } }

        on<RenderEvent.World> {
            playerEntitiesNoSelf.forEach { entity ->
                if (!matchesFilters(entity.name?.string, entity.displayName?.string)) return@forEach
                highlight.draw(ctx, entity.interpolatedBox, entity.colourFromDistance, entity.colourFromDistance)
                tracer.draw(ctx, entity, entity.colourFromDistance)
            }
        }

        on<EntityEvent.ForceGlow> {
            if (highlight.style != "Glow") return@on
            if (entity !in playerEntitiesNoSelf) return@on
            if (!matchesFilters(entity.name?.string, entity.displayName?.string)) return@on
            highlight.draw(this, entity.colourFromDistance)
        }
    }
}
