package quoi.module.impl.dungeon

import quoi.api.events.ChatEvent
import quoi.api.events.core.on
import quoi.api.skyblock.location.Island
import quoi.api.skyblock.dungeon.Dungeon.currentDungeonPlayer
import quoi.api.skyblock.dungeon.Dungeon.isDead
import quoi.api.skyblock.dungeon.DungeonClass
import quoi.api.skyblock.location.invoke
import quoi.module.Module

// Kyleen
object DungeonAbilities : Module(
    "Dungeon Abilities",
    desc = "Automatically uses abilities.",
    area = Island.Dungeon(inBoss = true)
) {
    private val ultTriggers = mapOf(
        "[BOSS] Goldor: You have done it, you destroyed the factory…" to setOf(
            DungeonClass.Healer,
            DungeonClass.Tank
        ),
        "⚠ Maxor is enraged! ⚠" to setOf(
            DungeonClass.Healer,
            DungeonClass.Tank
        ),
        "[BOSS] Sadan: My giants! Unleashed!" to setOf(
            DungeonClass.Healer,
            DungeonClass.Tank,
            DungeonClass.Archer,
            DungeonClass.Berserk,
            DungeonClass.Mage
        ),
        "[BOSS] Livid: I respect you for making it to here, but I'll be your undoing." to setOf(
            DungeonClass.Healer,
            DungeonClass.Tank
        ),
    )

    init {
        on<ChatEvent.Packet> {
            if (isDead) return@on

            val classes = ultTriggers[unformatted] ?: return@on
            val playerClass = currentDungeonPlayer.clazz

            if (playerClass !in classes) return@on

            player.drop(false)
        }
    }
}
