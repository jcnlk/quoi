package quoi.api.customtriggers

import quoi.api.customtriggers.actions.TriggerAction
import quoi.api.customtriggers.conditions.TriggerCondition
import quoi.api.customtriggers.actions.DelayAction

/**
 * Runs [TriggerRule]s and resumes delayed action sequences on the client thread.
 * Event subscriptions and module state are managed by [TriggerManager].
 */
class TriggerEngine(
    private val triggers: () -> List<TriggerRule>,
    private val execute: (TriggerAction, TriggerContext) -> Unit = { action, context -> action.execute(context) },
    private val matches: (TriggerCondition, TriggerContext) -> Boolean = { condition, context -> condition.matches(context) },
    private val isActive: () -> Boolean = { true },
    private val onError: (TriggerRule, Exception) -> Unit = { _, _ -> }
) {
    private val failed = mutableSetOf<String>()
    private val pending = mutableListOf<PendingSequence>()
    private var dispatching = false
    private var generation = 0L

    private data class PendingSequence(
        val trigger: TriggerRule,
        val actions: List<TriggerAction>,
        val nextIndex: Int,
        val server: Boolean,
        val context: TriggerContext,
        var remaining: Int
    )

    /**
     * Discards pending sequences and clears failed rules so they can run again.
     * Also stops an action sequence if called during its execution.
     */
    fun reset() {
        generation++
        failed.clear()
        pending.clear()
    }

    fun handle(context: TriggerContext) = guarded {
        val started = generation
        triggers().forEach { trigger ->
            if (!isActive() || generation != started) return@forEach
            if (!trigger.enabled || trigger.id in failed) return@forEach
            safely(trigger) {
                if (trigger.validationError() != null) return@safely
                val local = trigger.trigger.createContext(context) ?: return@safely
                if (!trigger.conditions.all { matches(it, local) }) return@safely
                if (!resume(trigger, trigger.actions.toList(), 0, local, started)) return@safely
                when (context) {
                    is TriggerContext.Chat if local is TriggerContext.Chat -> context.cancelled = context.cancelled || local.cancelled
                    is TriggerContext.Command if local is TriggerContext.Command -> context.cancelled = context.cancelled || local.cancelled
                    else -> {}
                }
            }
        }
    }

    /**
     * Runs the actions of [trigger] without checking its trigger or conditions.
     * Uses an empty [TriggerContext.Preview], so event captures are unavailable.
     */
    fun preview(trigger: TriggerRule) = guarded {
        safely(trigger) {
            if (trigger.enabled && trigger.validationError() == null)
                resume(trigger, trigger.actions.toList(), 0, TriggerContext.Preview(), generation)
        }
    }

    private fun resume(
        trigger: TriggerRule, actions: List<TriggerAction>, nextIndex: Int,
        context: TriggerContext, started: Long
    ): Boolean {
        for (index in nextIndex until actions.size) {
            if (!isActive() || generation != started || !trigger.enabled) return false
            val action = actions[index]
            if (action is DelayAction) {
                if (action.ticks > 0 && index < actions.lastIndex) {
                    check(pending.size < MAX_PENDING) { "Too many pending sequences. Reduce trigger frequency or delay." }
                    pending.add(PendingSequence(trigger, actions, index + 1, action.server, context.snapshot(), action.ticks))
                    return true
                }
            } else execute(action, context)
        }
        return true
    }

    /**
     * Advances pending delays by one client or server tick.
     * Delays queued while resuming actions start counting on the next tick.
     */
    fun tick(server: Boolean = false) = guarded {
        val started = generation
        val current = triggers()
        // Cancel queued work if the rule was removed or its action order changed.
        fun valid(queued: PendingSequence): Boolean =
            queued.trigger.enabled && queued.trigger.id !in failed &&
                current.any { it === queued.trigger } &&
                queued.trigger.actions.size == queued.actions.size &&
                queued.actions.indices.all { queued.trigger.actions[it] === queued.actions[it] }
        pending.removeAll { !valid(it) }
        val due = pending.filter { it.server == server && --it.remaining <= 0 }
        pending.removeAll(due.toSet())
        due.forEach { queued ->
            if (isActive() && generation == started && valid(queued)) safely(queued.trigger) {
                if (queued.trigger.validationError() == null)
                    resume(queued.trigger, queued.actions, queued.nextIndex, queued.context, started)
            }
        }
    }

    private inline fun guarded(block: () -> Unit) {
        // Locally emitted chat and commands must not recursively trigger themselves.
        if (dispatching) return
        if (!isActive()) {
            reset()
            return
        }
        dispatching = true
        try { block() } finally { dispatching = false }
    }

    private inline fun safely(trigger: TriggerRule, block: () -> Unit) {
        try { block() } catch (error: Exception) {
            failed.add(trigger.id)
            pending.removeAll { it.trigger === trigger }
            onError(trigger, error)
        }
    }

    private companion object {
        const val MAX_PENDING = 4096
    }
}
