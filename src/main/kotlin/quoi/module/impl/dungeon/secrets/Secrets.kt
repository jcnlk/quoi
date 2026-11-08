package quoi.module.impl.dungeon.secrets

import quoi.api.skyblock.location.Island
import quoi.module.Module
import quoi.module.impl.dungeon.secrets.impl.*

@Suppress("UnusedExpression")
object Secrets : Module(
    "Secrets",
    Island.Dungeon,
    desc = "Highlights and automatically collects dungeon secrets."
) {
    init {
        SecretHighlights
        SecretAura
        SecretTriggerbot
        AutoCloseChest
        FullBlock
    }
}
