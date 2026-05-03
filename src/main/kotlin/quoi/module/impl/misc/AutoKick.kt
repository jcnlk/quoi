package quoi.module.impl.misc

import quoi.api.events.ChatEvent
import quoi.api.events.WorldEvent
import quoi.api.skyblock.dungeon.DungeonClass
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.utils.ChatUtils.command
import quoi.utils.ChatUtils.modMessage
import quoi.utils.Scheduler.scheduleTask
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.skyblock.PartyUtils

object AutoKick : Module(
    "Auto Kick",
    desc = "Automatically kicks selected party members."
) {
    private val kickSkyblockerUsers by switch("Kick Skyblocker Users", desc = "I hate Skyblocker")
    private val kickClasses by switch("Kick classes")
    private val kickArcher by switch("Archer").childOf(::kickClasses)
    private val kickBerserk by switch("Berserk").childOf(::kickClasses)
    private val kickHealer by switch("Healer").childOf(::kickClasses)
    private val kickMage by switch("Mage").childOf(::kickClasses)
    private val kickTank by switch("Tank").childOf(::kickClasses)

    private val skyblockerRegex = Regex("^Party > ((?:\\[[^]]*?])? ?)?(\\w{1,16}): \\[Skyblocker] (.+)$")
    private val kickedRegex = Regex("^((?:\\[[^]]*?])? ?)?(\\w{1,16}) has been removed from the party\\.$")
    private val dungeonJoinRegex = Regex("^Party Finder > ((?:\\[[^]]*?])? ?)?(\\w{1,16}) joined the dungeon group! \\((\\w+) Level \\d+\\)$")

    private val pendingKicks = mutableSetOf<String>()

    override fun onDisable() {
        pendingKicks.clear()
        super.onDisable()
    }

    init {
        on<WorldEvent.Change> {
            pendingKicks.clear()
        }

        on<ChatEvent.Packet> {
            val cleanMessage = message.noControlCodes

            kickedRegex.find(cleanMessage)?.let {
                pendingKicks.remove(it.groupValues[2])
                return@on
            }

            if (kickSkyblockerUsers) {
                skyblockerRegex.find(cleanMessage)?.let {
                    requestKick(
                        name = it.groupValues[2],
                        message = "&cKicking ${it.groupValues[2]} for using Skyblocker!",
                        kickMessage = "SKYBLOCKER TAX!",
                        transferDelay = 15
                    )
                    return@on
                }
            }

            if (!kickClasses) return@on

            dungeonJoinRegex.find(cleanMessage)?.let {
                val name = it.groupValues[2]
                val clazz = it.groupValues[3]
                if (!shouldKickClass(clazz)) return@on

                requestKick(
                    name = name,
                    message = "&cKicking $name!",
                    kickMessage = null,
                    transferDelay = 5
                )
            }
        }
    }

    private fun requestKick(name: String, message: String, kickMessage: String?, transferDelay: Int) {
        if (!pendingKicks.add(name)) return

        val oldLeader = PartyUtils.partyLeader
        if (PartyUtils.isLeader()) kickPlayer(name, message, kickMessage)
        else requestTransferAndKick(name, oldLeader, message, kickMessage, transferDelay)
    }

    private fun kickPlayer(name: String, message: String, kickMessage: String?) {
        if (!shouldKick(name)) return

        modMessage(message)
        if (kickMessage != null) command("party chat $kickMessage")
        if (kickMessage == null) executeKick(name)
        else scheduleTask(5, server = true) { executeKick(name) }
    }

    private fun requestTransferAndKick(name: String, oldLeader: String?, message: String, kickMessage: String?, transferDelay: Int) {
        scheduleTask(5, server = true) {
            if (!shouldKick(name)) return@scheduleTask
            command("party chat !ptme")

            scheduleTask(transferDelay, server = true) {
                if (!shouldKick(name) || !PartyUtils.isLeader()) return@scheduleTask

                modMessage(message)
                if (kickMessage != null) command("party chat $kickMessage")
                if (kickMessage == null) executeKick(name)
                else scheduleTask(5, server = true) { executeKick(name) }
                scheduleTransferBack(oldLeader)
            }
        }
    }

    private fun executeKick(name: String) {
        if (!shouldKick(name)) return
        command("party kick $name")
    }

    private fun scheduleTransferBack(oldLeader: String?) {
        scheduleTask(5, server = true) {
            if (!enabled || oldLeader == null || oldLeader !in PartyUtils.members) return@scheduleTask
            command("party transfer $oldLeader")
        }
    }

    private fun shouldKick(name: String): Boolean {
        return enabled && name in pendingKicks
    }

    private fun shouldKickClass(name: String): Boolean {
        return when (name.uppercase()) {
            DungeonClass.Archer.name.uppercase() -> kickArcher
            DungeonClass.Berserk.name.uppercase() -> kickBerserk
            DungeonClass.Healer.name.uppercase() -> kickHealer
            DungeonClass.Mage.name.uppercase() -> kickMage
            DungeonClass.Tank.name.uppercase() -> kickTank
            else -> false
        }
    }
}
