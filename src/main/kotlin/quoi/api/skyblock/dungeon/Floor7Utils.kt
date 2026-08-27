package quoi.api.skyblock.dungeon

import net.minecraft.client.player.AbstractClientPlayer
import quoi.annotations.Init
import quoi.api.events.ChatEvent
import quoi.api.events.DungeonEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.EventDispatcher
import quoi.api.events.core.EventListener
import quoi.api.events.core.on
import quoi.utils.Shortcuts

@Init
object Floor7Utils : EventListener, Shortcuts {
    private var phase = Phase.UNKNOWN
    private var stage = Stage.UNKNOWN
    val inF7Boss: Boolean
        get() = Dungeon.inBoss && Dungeon.isFloor(7)

    init {
        on<ChatEvent.Packet> {
            if (!inF7Boss) return@on

            when (unformatted) {
                "[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!" -> {
                    Stage.resetAll()
                    updateState(newPhase = Phase.P1)
                }
                "[BOSS] Storm: Pathetic Maxor, just like expected." -> {
                    updateState(newPhase = Phase.P2)
                }
                "[BOSS] Goldor: Who dares trespass into my domain?" -> {
                    Stage.resetAll()
                    Stage.S1.start()
                    updateState(newPhase = Phase.P3, newStage = Stage.S1)
                }
                // we don't use goldor's death message, since it could be dialog skipped
                "[BOSS] Necron: Finally, I heard so much about you. The Eye likes you very much.", // first comp message
                "[BOSS] Necron: You went further than any human before, congratulations." -> {
                    updateState(newPhase = Phase.P4, newStage = Stage.UNKNOWN)
                }
                "The Core entrance is opening!" -> {
                    updateState(newStage = Stage.S5)
                }
                "[BOSS] Necron: All this, for nothing..." -> {
                    updateState(newPhase = Phase.P5)
                }
            }

            if (phase == Phase.P3 && stage.number in 1..4) {
                val nextStage = stage.process(unformatted)

                if (nextStage != stage) {
                    updateState(newStage = nextStage)
                }
            }
        }

        on<WorldEvent.Change> {
            Stage.resetAll()
            phase = Phase.UNKNOWN
            stage = Stage.UNKNOWN
        }
    }

    fun getPhase(): Phase = if (inF7Boss) phase else Phase.UNKNOWN

    @JvmOverloads
    fun getPhaseAt(player: AbstractClientPlayer? = mc.player): Phase {
        if (!inF7Boss || player == null) return Phase.UNKNOWN

        return with(player.y) {
            when {
                this > 210 -> Phase.P1
                this > 155 -> Phase.P2
                this > 100 -> Phase.P3
                this > 45 -> Phase.P4
                else -> Phase.P5
            }
        }
    }

    fun getStage(): Stage = if (inF7Boss) stage else Stage.UNKNOWN

    fun getStageAt(player: AbstractClientPlayer? = mc.player): Stage {
        if (!inF7Boss || player == null || getPhaseAt(player) != Phase.P3) return Stage.UNKNOWN

        val x = player.x
        val z = player.z

        return when (x) {
            in 89.0..113.0 if z in 30.0..122.0 -> Stage.S1
            in 19.0..111.0 if z in 121.0..145.0 -> Stage.S2
            in -6.0..19.0 if z in 51.0..143.0 -> Stage.S3
            in -2.0..90.0 if z in 27.0..51.0 -> Stage.S4
            in 41.0..68.0 if z in 59.0..117.0 -> Stage.S5
            else -> Stage.UNKNOWN
        }
    }

    fun inPhase(vararg phases: Phase): Boolean = getPhase() in phases

    fun inPhaseAt(vararg phases: Phase, player: AbstractClientPlayer? = mc.player): Boolean = player != null && getPhaseAt(player) in phases

    fun inStage(vararg stages: Stage): Boolean = getStage() in stages

    fun inStageAt(vararg stages: Stage, player: AbstractClientPlayer? = mc.player): Boolean = player != null && getStageAt(player) in stages

    private fun updateState(newPhase: Phase? = null, newStage: Stage? = null) {
        val oldPhase = phase
        val oldStage = stage

        phase = newPhase ?: phase
        stage = newStage ?: stage

        // explicitly setting the same phase/stage should still complete it, while
        // updating only one part of the state must not complete the other one
        if (newPhase != null && oldPhase != Phase.UNKNOWN) {
            DungeonEvent.PhaseComplete(oldPhase).post()
        }

        if (newStage != null && oldStage != Stage.UNKNOWN) {
            DungeonEvent.StageComplete.Full(oldStage).post()
        }
    }
}

enum class Phase {
    UNKNOWN, P1, P2, P3, P4, P5;
}

enum class Stage(val number: Int, val reqTerminals: Int) {
    UNKNOWN(0, 0), S1(1, 4), S2(2, 5), S3(3, 4), S4(4, 4), S5(5, 0);

    var terminals = 0
        private set

    var levers = 0
        private set

    var device = false
        private set

    private var _gate = false

    val gate: Boolean
        get() = this == S4 || _gate

    var startTime = 0L
        private set

    var startTicks = 0
        private set

    var endTime = 0L
        private set

    var endTicks = 0
        private set

    private var current = 0
    private var total = 0

    val objectivesCompleted: Boolean
        get() = total > 0 && current == total

    fun process(message: String): Stage {
        REGEX_TERM_COMPLETED.find(message)?.destructured?.let { (playerName, _, type, currentStr, totalStr) ->
            current = currentStr.toIntOrNull() ?: 0
            total = totalStr.toIntOrNull() ?: 0

            val dungeonPlayer = Dungeon.dungeonTeammates.find {
                it.name.equals(playerName, ignoreCase = true)
            }

            when (type) {
                "terminal" -> {
                    terminals++
                    dungeonPlayer?.p3Stats?.let { it.terminals++ }
                }

                "lever" -> {
                    levers++
                    dungeonPlayer?.p3Stats?.let { it.levers++ }
                }

                "device" -> {
                    device = true
                    dungeonPlayer?.p3Stats?.let { it.devices++ }
                }
            }

            if (objectivesCompleted) {
                DungeonEvent.StageComplete(this).post()
            }
        }

        if (message == "The gate has been destroyed!") {
            _gate = true
        }

        if (!objectivesCompleted || !gate || endTime != 0L) {
            return this
        }

        endTime = System.currentTimeMillis()
        endTicks = EventDispatcher.totalTicks

        val next = when (this) {
            S1 -> S2
            S2 -> S3
            S3 -> S4

            // S5 starts on core opening
            S4, S5, UNKNOWN -> return this
        }

        next.start()
        return next
    }

    fun getDuration(): Long {
        if (startTime == 0L) return 0L
        if (endTime != 0L) return endTime - startTime
        return System.currentTimeMillis() - startTime
    }

    fun getDurationTicks(): Long {
        if (startTicks == 0) return 0L
        if (endTicks != 0) {
            return (endTicks - startTicks).toLong()
        }

        return (EventDispatcher.totalTicks - startTicks).toLong()
    }

    fun start() {
        startTime = System.currentTimeMillis()
        startTicks = EventDispatcher.totalTicks
    }

    fun reset() {
        current = 0
        total = 0

        terminals = 0
        levers = 0
        device = false
        _gate = false

        startTime = 0L
        startTicks = 0

        endTime = 0L
        endTicks = 0
    }

    companion object {
        private val REGEX_TERM_COMPLETED = Regex("^(.{1,16}) (activated|completed) a (terminal|lever|device)! \\((\\d)/(\\d)\\)$")

        fun resetAll() {
            entries.forEach(Stage::reset)
            Dungeon.dungeonTeammates.forEach { it.p3Stats.reset() }
        }
    }
}
