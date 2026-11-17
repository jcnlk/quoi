package quoi.api.customtriggers.conditions

import quoi.api.customtriggers.Abobable
import quoi.api.customtriggers.TriggerContext
import quoi.config.TypeNamed

/**
 * Additional state check evaluated after a trigger matches.
 * All conditions in a rule must pass before its actions run.
 */
sealed class TriggerCondition : TypeNamed, Abobable {
    var expected: Boolean = true

    abstract fun matches(ctx: TriggerContext): Boolean

    /** Display text for the inverted check. */
    protected open fun invertedString(): String = "Not: ${displayString()}"

    /** Display text accounting for [expected]. */
    fun summary(): String = if (expected) displayString() else invertedString()
}
