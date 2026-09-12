package quoi.api.customtriggers

import quoi.api.customtriggers.actions.TriggerAction
import quoi.api.customtriggers.actions.DelayAction
import quoi.api.customtriggers.conditions.TriggerCondition
import quoi.api.customtriggers.triggers.Trigger
import quoi.api.customtriggers.triggers.ChatTrigger
import java.util.UUID

/**
 * A single [trigger] with state [conditions] and an ordered list of [actions].
 * All conditions must match before the actions run.
 */
data class TriggerRule(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "New trigger",
    var enabled: Boolean = true,
    var trigger: Trigger = ChatTrigger(),
    val conditions: MutableList<TriggerCondition> = mutableListOf(),
    val actions: MutableList<TriggerAction> = mutableListOf()
) {
    constructor(name: String) : this(name = name, id = UUID.randomUUID().toString())

    /**
     * Checks component settings, event requirements and action order.
     *
     * @return the first configuration error, or `null` if the rule is valid
     */
    fun validationError(): String? {
        trigger.validationError()?.let { return it }
        conditions.forEach { it.validationError()?.let { error -> return error } }
        if (actions.isEmpty()) return "Add at least one action."
        var delayed = false
        actions.forEach {
            it.validationError()?.let { error -> return error }
            if (!it.supports(trigger)) return "This action requires a ${it.requiredEvent} trigger."
            if (delayed && !it.allowsDelay) return "Event-editing actions must precede delays."
            if (it is DelayAction && it.ticks > 0) delayed = true
        }
        return null
    }
}
