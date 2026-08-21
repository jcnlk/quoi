package quoi.utils.skyblock

import quoi.annotations.Init
import quoi.api.events.ChatEvent
import quoi.api.events.PartyEvent
import quoi.api.events.core.EventListener
import quoi.api.events.core.on
import quoi.api.skyblock.dungeon.DungeonClass
import quoi.utils.Shortcuts

/**
 * TODO:
 *  make stuff regexless
 */

/**
 * modified OdinFabric (BSD 3-Clause)
 * copyright (c) 2025-2026 odtheking
 * original: https://github.com/odtheking/OdinFabric/blob/main/src/main/kotlin/com/odtheking/odin/utils/skyblock/PartyUtils.kt
 */
@Init
object PartyUtils : EventListener, Shortcuts {
    private val joinedSelf = Regex("^You have joined ((?:\\[[^]]*?])? ?)?(\\w{1,16})'s? party!$")
    private val joinedOther = Regex("^((?:\\[[^]]*?])? ?)?(\\w{1,16}) joined the party\\.$")
    private val leftParty = Regex("^((?:\\[[^]]*?])? ?)?(\\w{1,16}) has left the party\\.$")
    private val kickedParty = Regex("^((?:\\[[^]]*?])? ?)?(\\w{1,16}) has been removed from the party\\.$")
    private val kickedOffline = Regex("^Kicked ((?:\\[[^]]*?])? ?)?(\\w{1,16}) because they were offline\\.$")
    private val kickedDisconnected = Regex("^((?:\\[[^]]*?])? ?)?(\\w{1,16}) was removed from your party because they disconnected\\.$")
    private val transferLeave = Regex("^The party was transferred to ((?:\\[[^]]*?])? ?)?(\\w{1,16}) because ((?:\\[[^]]*?])? ?)?(\\w{1,16}) left$")
    private val transferBy = Regex("^The party was transferred to ((?:\\[[^]]*?])? ?)?(\\w{1,16}) by ((?:\\[[^]]*?])? ?)?(\\w{1,16})$")
    private val partyChat = Regex("^Party > ((?:\\[[^]]*?])? ?)?(\\w{1,16}): (.+)$")
    private val partyInvite = Regex("^((?:\\[[^]]*?])? ?)?(\\w{1,16}) invited ((?:\\[[^]]*?])? ?)?(\\w{1,16}) to the party! They have 60 seconds to accept.$")
    private val leaderDisconnected = Regex("^The party leader, ((?:\\[[^]]*?])? ?)?(\\w{1,16}) has disconnected, they have 5 minutes to rejoin before the party is disbanded\\.$")
    private val leaderRejoined = Regex("^The party leader ((?:\\[[^]]*?])? ?)?(\\w{1,16}) has rejoined\\.$")
    private val memberFormat = Regex("^((?:\\[[^]]*?])? ?)?(\\w{1,16})$")
    private val partyWith = Regex("^You'll be partying with: (.+)$")

    private val queuedInFinder = Regex("^Party Finder > Your party has been queued in the dungeon finder!$")
    private val dungeonJoin = Regex("^Party Finder > (\\w{1,16}) joined the dungeon group! \\((\\w+) Level (\\d+)\\)$")
    private val kuudraJoin = Regex("^Party Finder > ((?:\\[[^]]*?])? ?)?(\\w{1,16}) joined the group! \\(Combat Level (\\d+)\\)$")
    private val membersList = Regex("^Party (Leader|Moderators|Members): (.+)$")

    private val disbandPatterns = listOf(
        Regex("^((?:\\[[^]]*?])? ?)?(\\w{1,16}) has disbanded the party!$"),
        Regex("^You have been kicked from the party by ((?:\\[[^]]*?])? ?)?(\\w{1,16})$"),
        Regex("^The party was disbanded because all invites expired and the party was empty.$"),
        Regex("^The party was disbanded because the party leader disconnected.$"),
        Regex("^You left the party.$"),
        Regex("^You are not currently in a party.$")
    )

    private val partyMembers = mutableListOf<String>()

    val members: List<String>
        get() = partyMembers

    val membersNoSelf
        get() = partyMembers.filter { it != player.name.string }

    var partyLeader: String? = null
        private set

    var isInParty: Boolean = false
        private set

    init {
        on<ChatEvent.Packet> {
            joinedOther.find(unformatted)?.let { return@on addMember(it.groupValues[2]) }

            joinedSelf.find(unformatted)?.let {
                addMember(it.groupValues[2])
                updateLeader(it.groupValues[2])
                addMember(player.gameProfile.name)
                return@on
            }

            leftParty.find(unformatted)?.let { return@on removeMember(it.groupValues[2]) }

            kickedParty.find(unformatted)?.let { return@on removeMember(it.groupValues[2]) }

            kickedOffline.find(unformatted)?.let { return@on removeMember(it.groupValues[2]) }

            kickedDisconnected.find(unformatted)?.let { return@on removeMember(it.groupValues[2]) }

            transferBy.find(unformatted)?.let {
                addMember(it.groupValues[2])
                addMember(it.groupValues[4])
                updateLeader(it.groupValues[2])
                return@on
            }

            transferLeave.find(unformatted)?.let {
                addMember(it.groupValues[2])
                updateLeader(it.groupValues[2])
                removeMember(it.groupValues[4])
                return@on
            }

            leaderDisconnected.find(unformatted)?.let {
                updateLeader(it.groupValues[2])
                return@on
            }

            leaderRejoined.find(unformatted)?.let {
                updateLeader(it.groupValues[2])
                return@on
            }

            partyChat.find(unformatted)?.let { match ->
                val sender = match.groupValues[2]
                addMember(sender)
                PartyEvent.Message(sender, match.groupValues[3]).post()
                return@on
            }

            partyInvite.find(unformatted)?.let {
                addMember(it.groupValues[2])
                if (partyLeader == null) updateLeader(it.groupValues[2])
                return@on
            }

            queuedInFinder.find(unformatted)?.let {
                addMember(player.gameProfile.name)
                if (partyLeader == null) updateLeader(player.gameProfile.name)
                return@on
            }

            for (pattern in disbandPatterns) {
                if (pattern.containsMatchIn(unformatted)) return@on disband()
            }

            membersList.find(unformatted)?.let { match ->
                val type = match.groupValues[1]

                match.groupValues[2].split(" ●").forEach { segment ->
                    val memberMatch = memberFormat.find(segment.trim()) ?: return@forEach
                    addMember(memberMatch.groupValues[2])
                    if (type == "Leader") updateLeader(memberMatch.groupValues[2])

                    return@on
                }
            }

            partyWith.find(unformatted)?.let { match ->
                match.groupValues[1].split(", ").forEach { playerName ->
                    val memberMatch = memberFormat.find(playerName.trim()) ?: return@forEach
                    addMember(memberMatch.groupValues[2])
                }
                return@on
            }

            kuudraJoin.find(unformatted)?.let { return@on addMember(it.groupValues[2]) }

            dungeonJoin.find(unformatted)?.let { match ->
                val clazz = DungeonClass.entries.find { it.name.equals(match.groupValues[2], ignoreCase = true) }
                return@on addMember(match.groupValues[1], clazz)
            }
        }
    }

    private fun addMember(playerName: String, clazz: DungeonClass? = null) {
        if (playerName in partyMembers) {
            if (clazz != null) PartyEvent.Member.Join(playerName, clazz).post()
            return
        }

        val created = !isInParty

        isInParty = true
        partyMembers.add(playerName)

        if (created) PartyEvent.Create().post()
        PartyEvent.Member.Join(playerName, clazz).post()
    }

    private fun removeMember(playerName: String) {
        if (!partyMembers.remove(playerName)) return

        PartyEvent.Member.Leave(playerName).post()

        if (partyMembers.isEmpty()) disband()
    }

    private fun updateLeader(newLeader: String) {
        val oldLeader = partyLeader
        if (oldLeader == newLeader) return

        partyLeader = newLeader

        PartyEvent.Leader.Change(oldLeader, newLeader).post()
    }

    private fun disband() {
        if (!isInParty && partyMembers.isEmpty() && partyLeader == null) return

        val members = partyMembers.toList()
        val oldLeader = partyLeader

        partyMembers.clear()
        partyLeader = null
        isInParty = false

        members.forEach { PartyEvent.Member.Leave(it).post() }

        if (oldLeader != null) {
            PartyEvent.Leader.Change(oldLeader, null).post()
        }

        PartyEvent.Disband().post()
    }

    fun isLeader(): Boolean = partyLeader == player.gameProfile.name
}