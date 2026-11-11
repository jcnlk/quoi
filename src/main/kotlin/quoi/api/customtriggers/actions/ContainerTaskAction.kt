package quoi.api.customtriggers.actions

import quoi.api.customtriggers.TriggerContext
import quoi.utils.skyblock.player.container.task.ContainerTask

/**
 * Provides a [ContainerTask] that the engine waits for before continuing the sequence
 * Implement alongside [TriggerAction] so actions remain direct sealed subclasses for config discovery
 */
interface ContainerTaskAction {
    /**
     * Builds a fresh task without starting it
     * The engine submits the task and cancels it if the sequence is discarded
     */
    fun createTask(ctx: TriggerContext): ContainerTask
}
