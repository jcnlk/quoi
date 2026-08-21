package quoi.module.impl.general

import kotlinx.coroutines.launch
import quoi.QuoiMod.scope
import quoi.api.events.PartyEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.on
import quoi.api.skyblock.SkyblockPlayer
import quoi.api.skyblock.dungeon.DungeonClass
import quoi.module.Module
import quoi.utils.ChatUtils.command
import quoi.utils.Scheduler.wait
import quoi.utils.skyblock.PartyUtils

/**
 * TODO:
 *  add custom kick list or smth
 */
object AutoKick : Module(
    "Auto Kick",
    desc = "Automatically kicks selected party members."
) {
    private val kickSkyblockerUsers by switch("Kick Skyblocker Users", desc = "I hate Skyblocker")
    private val kickClasses by multiSelect("Kick classes", emptySet(), DungeonClass.entries - DungeonClass.Unknown)

    private val pendingKicks = mutableSetOf<String>()

    override fun onDisable() {
        pendingKicks.clear()
        super.onDisable()
    }

    init {
        on<WorldEvent.Change> { pendingKicks.clear() }
        on<PartyEvent.Disband> { pendingKicks.clear() }

        on<PartyEvent.Member.Join> {
            if (clazz != null && clazz in kickClasses) requestKick(name, "&cKicking $name!")
        }

        on<PartyEvent.Message> {
            if (!kickSkyblockerUsers || !content.startsWith("[Skyblocker] ")) return@on
            requestKick(sender, "SKYBLOCKER TAX!")
        }
    }

    private fun requestKick(name: String, partyMessage: String? = null) {
        if (name == player.gameProfile.name || !pendingKicks.add(name)) return

        scope.launch {
            try {
                kick(name, partyMessage)
            } finally {
                pendingKicks.remove(name)
            }
        }
    }

    private suspend fun kick(name: String, partyMessage: String?) {
        wait(0, server = true)
        if (!shouldKick(name)) return

        val oldLeader = PartyUtils.partyLeader
        val leaderNeeded = !PartyUtils.isLeader()

        if (leaderNeeded) {
            if (!await(name) { SkyblockPlayer.canUseCommands }) return
            command("party chat !ptme")
            if (!await(name, PartyUtils::isLeader)) return
        }

        try {
            if (!await(name) { SkyblockPlayer.canUseCommands }) return

            partyMessage?.let {
                command("party chat $it")
                if (!await(name) { SkyblockPlayer.canUseCommands }) return
            }

            if (!PartyUtils.isLeader() || name !in PartyUtils.members) return
            command("party kick $name")
            await(name) { name !in PartyUtils.members }
        } finally {
            if (leaderNeeded) transferBack(name, oldLeader)
        }
    }

    private suspend fun transferBack(name: String, oldLeader: String?) {
        val leader = oldLeader ?: return
        if (!await(name) { SkyblockPlayer.canUseCommands }) return
        if (!PartyUtils.isLeader() || leader !in PartyUtils.members) return

        command("party transfer $leader")
    }

    private suspend fun await(name: String, condition: () -> Boolean): Boolean {
        while (shouldKick(name) && !condition()) wait(1, server = true)
        return shouldKick(name) && condition()
    }

    private fun shouldKick(name: String) = enabled && name in pendingKicks
}
