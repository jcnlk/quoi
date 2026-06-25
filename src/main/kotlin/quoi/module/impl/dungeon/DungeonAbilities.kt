package quoi.module.impl.dungeon

import quoi.api.events.ChatEvent
import quoi.api.events.core.on
import quoi.api.skyblock.Island
import quoi.api.skyblock.dungeon.Dungeon.currentDungeonPlayer
import quoi.api.skyblock.dungeon.Dungeon.isDead
import quoi.api.skyblock.dungeon.DungeonClass
import quoi.module.Module

/**
 * TODO:
 *  add more messages
 *  support tank
 */

// Kyleen
object DungeonAbilities : Module(
    "Dungeon Abilities",
    desc = "Automatically uses abilities.",
    area = Island.Dungeon,
) {
    private val autoWish by switch("Healer auto wish")

    init {
        on<ChatEvent.Packet> {
            if (isDead) return@on
            when (unformatted) {
                "[BOSS] Goldor: You have done it, you destroyed the factory…" -> {
                    dropItem()
                }
                "⚠ Maxor is enraged! ⚠" -> {
                    dropItem()
                }
            }
        }
    }

    private fun dropItem() {
        if (!autoWish || currentDungeonPlayer.clazz != DungeonClass.Healer) return
        player.drop(false)
    }
}