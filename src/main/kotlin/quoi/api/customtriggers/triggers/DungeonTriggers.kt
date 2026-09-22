package quoi.api.customtriggers.triggers

import quoi.api.abobaui.elements.ElementScope
import quoi.api.customtriggers.TriggerContext
import quoi.api.customtriggers.choiceField
import quoi.api.skyblock.dungeon.enums.Floor
import quoi.api.skyblock.dungeon.enums.Phase
import quoi.api.skyblock.dungeon.enums.Stage
import quoi.config.TypeName

@TypeName("dungeon_entered")
class DungeonEnteredTrigger(var floor: Floor? = null) : Trigger {
    override val eventKind get() = TriggerContext.Kind.DUNGEON
    override fun matches(ctx: TriggerContext): Boolean {
        if (ctx !is TriggerContext.Dungeon || ctx.event != TriggerContext.Dungeon.Event.ENTER) return false
        if (floor != null && ctx.floor != floor) return false
        ctx.floor?.let { ctx.data["%floor%"] = it.name }
        return true
    }
    override fun displayString() = "Dungeon entered${floor?.let { ": ${it.name}" }.orEmpty()}"
    override fun ElementScope<*>.draw() = choiceField(
        "Floor", { floor }, listOf(null) + Floor.entries, { it?.name ?: "Any" }
    ) { floor = it }
}

@TypeName("dungeon_started")
class DungeonStartedTrigger : Trigger {
    override val eventKind get() = TriggerContext.Kind.DUNGEON
    override fun matches(ctx: TriggerContext): Boolean {
        if (ctx !is TriggerContext.Dungeon || ctx.event != TriggerContext.Dungeon.Event.START) return false
        ctx.floor?.let { ctx.data["%floor%"] = it.name }
        return true
    }
    override fun displayString() = "Dungeon started"
    override fun ElementScope<*>.draw() = this
}

@TypeName("dungeon_phase_completed")
class DungeonPhaseCompletedTrigger(var phase: Phase? = null) : Trigger {
    override val eventKind get() = TriggerContext.Kind.DUNGEON
    override fun matches(ctx: TriggerContext): Boolean {
        if (ctx !is TriggerContext.Dungeon || ctx.event != TriggerContext.Dungeon.Event.PHASE_COMPLETE) return false
        if (phase != null && ctx.phase != phase) return false
        ctx.phase?.let { ctx.data["%phase%"] = it.name }
        return true
    }
    override fun displayString() = "Phase completed${phase?.let { ": ${it.name}" }.orEmpty()}"
    override fun ElementScope<*>.draw() = choiceField(
        "Phase", { phase }, listOf(null) + Phase.entries.filter { it != Phase.Unknown }, { it?.name ?: "Any" }
    ) { phase = it }
}

@TypeName("dungeon_stage_completed")
class DungeonStageCompletedTrigger(var stage: Stage? = null) : Trigger {
    override val eventKind get() = TriggerContext.Kind.DUNGEON
    override fun matches(ctx: TriggerContext): Boolean {
        if (ctx !is TriggerContext.Dungeon || ctx.event != TriggerContext.Dungeon.Event.STAGE_COMPLETE) return false
        if (stage != null && ctx.stage != stage) return false
        ctx.stage?.let { ctx.data["%stage%"] = it.name }
        return true
    }
    override fun displayString() = "Stage completed${stage?.let { ": ${it.name}" }.orEmpty()}"
    override fun ElementScope<*>.draw() = choiceField(
        "Stage", { stage }, listOf(null) + Stage.entries.filter { it != Stage.Unknown }, { it?.name ?: "Any" }
    ) { stage = it }
}
