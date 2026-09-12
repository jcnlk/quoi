package quoi.api.customtriggers.triggers

import quoi.api.customtriggers.Abobable
import quoi.api.customtriggers.TriggerContext
import quoi.config.TypeNamed

/**
 * Filters one [eventKind] and creates the context passed to conditions and actions.
 */
sealed interface Trigger : TypeNamed, Abobable {
    val eventKind: TriggerContext.Kind
    fun matches(ctx: TriggerContext): Boolean

    /**
     * Matches [event] against a private copy of its values.
     *
     * @return the context with captures from [matches], or `null` if the event does not match
     */
    fun createContext(event: TriggerContext): TriggerContext? {
        if (event.kind != eventKind) return null
        val local = event.snapshot().also { it.data.clear() }
        return local.takeIf { matches(it) }
    }
}
