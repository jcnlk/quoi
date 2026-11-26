package quoi.api.customtriggers.ui

import quoi.api.customtriggers.Abobable
import quoi.api.customtriggers.TriggerRule
import quoi.api.customtriggers.actions.ContainerTaskAction
import quoi.api.customtriggers.actions.DelayAction
import quoi.api.customtriggers.actions.TriggerAction
import quoi.api.customtriggers.conditions.TriggerCondition

/**
 * Nodes in flow order: event, conditions, actions.
 *
 * Slot `s` inserts before node `s`; [size] appends after the last node.
 */
class TriggerGraph(val rule: TriggerRule) {
    enum class Kind { EVENT, CONDITION, ACTION }

    val size get() = 1 + rule.conditions.size + rule.actions.size

    /** First action index and insertion slot after the conditions. */
    val boundary get() = 1 + rule.conditions.size

    fun kind(index: Int) = when {
        index == 0 -> Kind.EVENT
        index < boundary -> Kind.CONDITION
        else -> Kind.ACTION
    }

    fun component(index: Int): Abobable = when (kind(index)) {
        Kind.EVENT -> rule.trigger
        Kind.CONDITION -> rule.conditions[index - 1]
        Kind.ACTION -> rule.actions[index - boundary]
    }

    /** Allowed insertion slots for [kind], keeping conditions before actions. */
    fun slots(kind: Kind): IntRange = when (kind) {
        Kind.EVENT -> IntRange.EMPTY
        Kind.CONDITION -> 1..boundary
        Kind.ACTION -> boundary..size
    }

    /**
     * Inserts [component] at [slot].
     *
     * @return the flat index of the new node, or `null` if the slot is not allowed for the component
     */
    fun insert(slot: Int, component: Abobable): Int? {
        when (component) {
            is TriggerCondition -> if (slot in slots(Kind.CONDITION)) rule.conditions.add(
                slot - 1,
                component
            ) else return null

            is TriggerAction -> if (slot in slots(Kind.ACTION)) rule.actions.add(
                slot - boundary,
                component
            ) else return null

            else -> return null
        }
        return slot
    }

    /** Removes [index], except the event at index 0. */
    fun remove(index: Int): Abobable? = when {
        index !in 1 until size -> null
        kind(index) == Kind.CONDITION -> rule.conditions.removeAt(index - 1)
        else -> rule.actions.removeAt(index - boundary)
    }

    /**
     * Moves [index] to [slot], using slot indices from before removal.
     *
     * @return the new flat index, or `null` if nothing moved
     */
    fun move(index: Int, slot: Int): Int? {
        if (index !in 1 until size || slot == index || slot == index + 1) return null
        if (slot !in slots(kind(index))) return null
        val component = remove(index)!!
        return insert(if (slot > index) slot - 1 else slot, component)
    }

    /** Checks node settings, event requirements and action order. */
    fun error(index: Int): String? {
        val component = component(index)
        component.validationError()?.let { return it }
        if (component !is TriggerAction) return null
        if (!component.supports(rule.trigger)) {
            return "Needs a ${NodeCatalog.eventNameFor(component.requiredEvent)} event."
        }
        if (!component.allowsDelay) {
            val position = index - boundary
            val delayed =
                rule.actions.take(position).any { it is ContainerTaskAction || it is DelayAction && it.ticks > 0 }
            if (delayed) return "Must run before any Wait or menu action."
        }
        return null
    }

    fun flowError(): String? = if (rule.actions.isEmpty()) "Add an action to finish this trigger." else null
}
