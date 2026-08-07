package quoi.module.impl.dungeon.secrets

import quoi.api.skyblock.location.Island
import quoi.module.Module
import quoi.module.impl.dungeon.secrets.impl.AutoCloseChest
import quoi.module.impl.dungeon.secrets.impl.FullBlock
import quoi.module.impl.dungeon.secrets.impl.SecretAura
import quoi.module.impl.dungeon.secrets.impl.SecretHighlights
import quoi.module.impl.dungeon.secrets.impl.SecretTriggerbot

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
        FullBlock

        command.sub("clearaura") {
            SecretAura.clear()
        }
    }
}
