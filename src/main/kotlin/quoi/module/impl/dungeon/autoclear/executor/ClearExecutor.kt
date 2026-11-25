package quoi.module.impl.dungeon.autoclear.executor

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import quoi.QuoiMod.logger
import quoi.QuoiMod.mc
import quoi.QuoiMod.scope
import quoi.annotations.Init
import quoi.api.events.*
import quoi.api.events.core.EventListener
import quoi.api.events.core.on
import quoi.api.pathfinding.PathConfig
import quoi.api.pathfinding.impl.EtherwarpPathfinder
import quoi.api.vec.MutableVec3
import quoi.api.world.Direction
import quoi.module.impl.dungeon.autoclear.executor.nodes.ClearEtherNode
import quoi.module.impl.dungeon.autoclear.toEther
import quoi.utils.ChatUtils.modMessage
import quoi.utils.player
import quoi.utils.skyblock.player.PlayerUtils.at
import quoi.utils.skyblock.player.PlayerUtils.useItem

@Init
object ClearExecutor : EventListener {
    private const val STALL_TIMEOUT_TICKS = 100

    private var nodes: MutableList<ClearNode>? = null

    private var syncDelay = 0
    private var compDelay = 0

    var active = false
        private set

    var owner: Any? = null
        private set

    val routing: Boolean
        get() = pathJob?.isActive == true || !nodes.isNullOrEmpty() || pendingInteract != null || active || pendingCompletion != null

    private var pathJob: Job? = null
    private var pendingInteract: Direction? = null
    private var position: MutableVec3? = null
    private var activeNode: ClearNode? = null
    private var onComplete: (() -> Unit)? = null
    private var onFailure: ((String) -> Unit)? = null
    private var pendingCompletion: (() -> Unit)? = null
    private var ticksWithoutProgress = 0

    internal val hasFailureHandler: Boolean
        get() = onFailure != null

    init {
        on<PacketEvent.Received, ClientboundPlayerPositionPacket> {
            if (syncDelay == 1) syncDelay = 2
        }

        on<TickEvent.Server> {
            if (syncDelay < 2) return@on
            if (syncDelay++ > 9) {
                syncDelay = 0
                onFailure = null
                val callback = pendingCompletion
                if (callback != null) mc.submit {
                    if (pendingCompletion === callback) {
                        pendingCompletion = null
                        callback()
                    }
                }
            }
        }

        on<TickEvent.Start> {
            if (onFailure != null && (syncDelay != 0 || !nodes.isNullOrEmpty()) &&
                ++ticksWithoutProgress >= STALL_TIMEOUT_TICKS
            ) {
                fail(if (syncDelay != 0) "Timed out waiting for a response from the server." else "Travel stalled. Stand still and try again.")
                return@on
            }

            doInteract()
            updateDelays()

            if (!canNext()) return@on

            if (position == null) position = MutableVec3(player.position())

            handleQueue(position!!, nodes!!)
        }

        on<RenderEvent.World> {
            if (nodes.isNullOrEmpty()) return@on
            nodes!!.forEach { it.render(ctx) }
        }

        on<KeyEvent.Input> {
            if (!active) return@on
            val curr = activeNode is ClearEtherNode
            val next = nodes?.firstOrNull() is ClearEtherNode

            if (curr || next) input.shift = true
        }

        on<WorldEvent.Change> {
            cancel()
            compDelay = 0
        }
    }

    fun etherPath(
        to: BlockPos,
        config: PathConfig = PathConfig(),
        owner: Any? = null,
        onAlreadyThere: (() -> Unit)? = null,
        onFailure: ((String) -> Unit)? = null,
        onComplete: (() -> Unit)? = null,
    ) {
        cancel()
        if (player.at(to)) {
            onAlreadyThere?.invoke()
            onComplete?.invoke()
            if (onAlreadyThere == null) modMessage("Already there")
            return
        }

        this.owner = owner
        this.onFailure = onFailure
        val from = player.position()
        pathJob = scope.launch {
            try {
                val path = EtherwarpPathfinder.findDungeonPath(from = from, to = to, config = config)
                ensureActive()
                mc.submit {
                    if (!isActive) return@submit
                    if (path.isNullOrEmpty()) {
                        if (onFailure != null) fail("No Etherwarp path was found.")
                    } else {
                        clearPath(path.map { it.toEther() }, owner, onComplete)
                    }
                }.join()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error("Failed to prepare Etherwarp path to $to", e)
                mc.submit {
                    if (isActive && onFailure != null) fail("An error occurred while preparing the path.")
                }.join()
            }
        }
    }

    fun clearPath(path: List<ClearNode>, owner: Any? = null, onComplete: (() -> Unit)? = null) {
        this.owner = owner
        nodes = path.toMutableList()
        position = null
        pendingInteract = null
        this.onComplete = onComplete
        pendingCompletion = null
    }

    fun queueInteract(yaw: Float, pitch: Float) {
        pendingInteract = Direction(yaw, pitch)
    }

    fun cancel() {
        pathJob?.cancel()
        pathJob = null
        nodes = null
        position = null
        pendingInteract = null
        owner = null
        syncDelay = 0
        compDelay = 2
        active = false
        activeNode = null
        onComplete = null
        onFailure = null
        pendingCompletion = null
        ticksWithoutProgress = 0
    }

    fun fail(message: String): Boolean {
        val callback = onFailure
        cancel()
        callback?.invoke(message)
        return false
    }

    private fun handleQueue(playerPos: MutableVec3, clearNodes: MutableList<ClearNode>) {
        val node = clearNodes.filter { it.inside(playerPos) }.maxByOrNull { it.priority } ?: run {
            position = null
            return
        }

        active = true
        activeNode = node

        if (node.execute(playerPos)) {
            ticksWithoutProgress = 0
            clearNodes.remove(node)

            if (clearNodes.isEmpty()) {
                nodes = null
                position = null
                compDelay = 2
                pendingCompletion = onComplete
                onComplete = null
                syncDelay = 1
            }
        }
    }

    private fun doInteract() {
        pendingInteract?.let {
            player.useItem(it.yaw, it.pitch)
            pendingInteract = null
        }
    }

    private fun updateDelays() {
        if (compDelay > 0 && --compDelay == 0) {
            active = false
            activeNode = null
        }
    }

    private fun canNext(): Boolean {
        if (syncDelay != 0) return false
        if (nodes.isNullOrEmpty()) return false

        if (compDelay == 0) active = false

        return true
    }
}
