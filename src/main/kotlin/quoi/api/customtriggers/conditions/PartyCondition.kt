package quoi.api.customtriggers.conditions

import quoi.api.abobaui.elements.ElementScope
import quoi.api.customtriggers.*
import quoi.config.TypeName
import quoi.utils.skyblock.PartyUtils

@TypeName("party")
class PartyCondition(var state: State = State.IN_PARTY, var member: String = "") : TriggerCondition {
    enum class State(val label: String) { IN_PARTY("In a party"), LEADER("Party leader"), HAS_MEMBER("Has member") }
    override fun validationError() = if (state == State.HAS_MEMBER && member.isBlank()) "Enter a party member." else null
    override fun matches(ctx: TriggerContext): Boolean = when (state) {
        State.IN_PARTY -> PartyUtils.isInParty
        State.LEADER -> PartyUtils.partyLeader == quoi.QuoiMod.mc.player?.gameProfile?.name
        State.HAS_MEMBER -> PartyUtils.members.any { it.equals(member.trim(), true) }
    }
    override fun displayString() = if (state == State.HAS_MEMBER) "Party has $member" else state.label
    override fun ElementScope<*>.draw() = settingRow(
        { choiceField("State", { state }, State.entries, { it.label }) { state = it } },
        { textField("Member", member) { member = it } }
    )
}
