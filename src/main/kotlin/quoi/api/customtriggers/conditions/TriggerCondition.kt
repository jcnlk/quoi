package quoi.api.customtriggers.conditions

import quoi.api.customtriggers.Abobable
import quoi.api.customtriggers.TriggerContext
import quoi.config.TypeNamed

/**
 * Additional state check evaluated after a trigger matches.
 * All conditions in a rule must pass before its actions run.
 */
sealed interface TriggerCondition : TypeNamed, Abobable {
    fun matches(ctx: TriggerContext): Boolean
}