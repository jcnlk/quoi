package quoi.api.customtriggers

import quoi.api.customtriggers.actions.TriggerAction
import quoi.api.customtriggers.conditions.TriggerCondition
import quoi.api.customtriggers.actions.DelayAction
import quoi.api.customtriggers.actions.ContainerTaskAction
import quoi.utils.skyblock.player.container.task.ContainerTask
import quoi.utils.skyblock.player.container.task.ContainerTaskResult

/**
 * Runs [TriggerRule]s and resumes sequences after delays or container tasks on the client thread.
 * Event subscriptions and module state are managed by [TriggerManager].
 */
class TriggerEngine(
    private val triggers: () -> List<TriggerRule>,
    private val execute: (TriggerAction, TriggerContext) -> Unit = { action, context -> action.execute(context) },
    private val matches: (TriggerCondition, TriggerContext) -> Boolean = { condition, context -> condition.matches(context) },
    private val isActive: () -> Boolean = { true },
    private val onError: (TriggerRule, Exception) -> Unit = { _, _ -> },
) {
    private val failed = mutableSetOf<String>()
    private val pending = mutableListOf<PendingSequence>()
    private var dispatching = false
    private var generation = 0L

    private data class PendingSequence(
        val trigger: TriggerRule,
        val actions: List<TriggerAction>,
        val nextIndex: Int,
        val context: TriggerContext,
        var remaining: Int = 0,
        val server: Boolean = false,
        val task: ContainerTask? = null,
    )

    /**
     * Cancels pending tasks and clears sequences and failed rules so they can run again.
     * Also stops an action sequence if called during its execution.
     */
    fun reset() {
        generation++
        failed.clear()
        discard(pending.toList())
    }

    fun handle(context: TriggerContext) = guarded {
        val started = generation
        triggers().forEach { trigger ->
            if (!isActive() || generation != started) return@forEach
            if (!trigger.enabled || trigger.id in failed) return@forEach
            safely(trigger) {
                if (trigger.validationError() != null) return@safely
                val local = trigger.trigger.createContext(context) ?: return@safely
                if (!trigger.conditions.all { matches(it, local) == it.expected }) return@safely
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
                    checkPendingLimit()
                    pending.add(PendingSequence(trigger, actions, index + 1, context.snapshot(), action.ticks, action.server))
                    return true
                }
            } else if (action is ContainerTaskAction) {
                checkPendingLimit()
                val task = action.createTask(context).run()
                // Let the task finish and release the container manager before continuing.
                pending.add(PendingSequence(trigger, actions, index + 1, context.snapshot(), task = task))
                return true
            } else execute(action, context)
        }
        return true
    }

    /**
     * Advances delays on their selected clock and resumes completed tasks on client ticks.
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
        discard(pending.filterNot(::valid))
        val due = pending.filter { queued ->
            queued.task?.let { !server && it.completed }
                ?: (queued.server == server && --queued.remaining <= 0)
        }
        pending.removeAll(due.toSet())
        due.forEach { queued ->
            if (isActive() && generation == started && valid(queued)) safely(queued.trigger) {
                if (queued.trigger.validationError() == null) {
                    when (val result = queued.task?.result) {
                        null, ContainerTaskResult.Success ->
                            resume(queued.trigger, queued.actions, queued.nextIndex, queued.context, started)
                        ContainerTaskResult.Cancelled -> Unit
                        ContainerTaskResult.Busy -> error("A container action is already in progress")
                        is ContainerTaskResult.Failure -> error(result.message)
                    }
                }
            }
        }
    }

    private fun checkPendingLimit() {
        check(pending.size < MAX_PENDING) { "Too many pending sequences. Reduce trigger frequency or delay." }
    }

    private fun discard(sequences: List<PendingSequence>) {
        pending.removeAll(sequences.toSet())
        sequences.mapNotNull { it.task }.filterNot { it.completed }.forEach { it.cancel() }
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
            discard(pending.filter { it.trigger === trigger })
            onError(trigger, error)
        }
    }

    private companion object {
        const val MAX_PENDING = 4096
    }
}
