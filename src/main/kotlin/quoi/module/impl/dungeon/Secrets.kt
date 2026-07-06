package quoi.module.impl.dungeon

import quoi.api.skyblock.location.Island
import quoi.module.Module
import quoi.module.impl.dungeon.secrets.AutoCloseChest
import quoi.module.impl.dungeon.secrets.SecretAura
import quoi.module.impl.dungeon.secrets.SecretHighlights
import quoi.module.impl.dungeon.secrets.SecretTriggerbot

@Suppress("unused_expression")
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

        command.sub("clearaura") {
            SecretAura.clear()
        }
    }
}
