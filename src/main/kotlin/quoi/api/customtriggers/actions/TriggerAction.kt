package quoi.api.customtriggers.actions

import quoi.api.customtriggers.Abobable
import quoi.api.customtriggers.TriggerContext
import quoi.config.TypeNamed
import quoi.api.customtriggers.triggers.Trigger

/**
 * An action in a rule's ordered sequence.
 * [DelayAction] is handled by the engine; other actions run through [execute].
 */
sealed interface TriggerAction : TypeNamed, Abobable {
    /**
     * Event type required by this action, or `null` for actions that support any trigger.
     */
    val requiredEvent: TriggerContext.Kind? get() = null
    /**
     * Whether this action can run after a delay.
     * Event edits must finish before the originating event returns.
     */
    val allowsDelay: Boolean get() = true
    fun supports(trigger: Trigger) = requiredEvent == null || requiredEvent == trigger.eventKind
    fun execute(ctx: TriggerContext)
}