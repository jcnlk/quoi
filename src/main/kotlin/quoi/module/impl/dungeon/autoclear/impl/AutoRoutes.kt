package quoi.module.impl.dungeon.autoclear.impl

import net.minecraft.world.entity.ambient.Bat
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.EntityHitResult
import quoi.api.autoroutes.AutoRoutesCommand
import quoi.api.autoroutes.RouteNode
import quoi.api.autoroutes.RouteRegistry
import quoi.api.autoroutes.awaits.SecretAwait
import quoi.api.autoroutes.nodes.BreakerNode
import quoi.api.autoroutes.nodes.EtherwarpNode
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.events.*
import quoi.api.events.core.EventDispatcher
import quoi.api.events.core.on
import quoi.api.skyblock.dungeon.odonscanning.ScanUtils
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonRoom
import quoi.api.skyblock.dungeon.odonscanning.tiles.RoomType
import quoi.api.skyblock.location.Island
import quoi.api.skyblock.location.invoke
import quoi.api.vec.MutableVec3
import quoi.api.world.Direction
import quoi.config.ConfigMap
import quoi.config.configMap
import quoi.config.typeName
import quoi.module.Module
import quoi.module.impl.dungeon.autoclear.executor.ClearExecutor
import quoi.module.settings.Setting.Companion.json
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.UIComponent.Companion.visibleIf
import quoi.utils.*
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.StringUtils.width
import quoi.utils.render.DrawContextUtils.drawText
import quoi.utils.skyblock.player.PlayerUtils
import quoi.utils.skyblock.player.PlayerUtils.useItem
import quoi.utils.skyblock.player.SwapManager

object AutoRoutes : Module(
    "Auto Routes",
    area = Island.Dungeon(inClear = true),
    tag = Tag.BETA
) {
    val zeroTickDb by switch("Zero tick dungeon breaker")
    private val pauseOnMimic by switch("Pause on mimic", true, desc = "Pauses the route and shows a title when opening a Mimic chest.")
    private val style by selector("Style", "Box", listOf("Box", "Filled box", "Cylinder"))
    private val thickness by slider("Thickness", 4f, 1f, 8f, 0.5f)

    private val multicolour by switch("Multicolour")
    private val colour by colourPicker("Colour", Colour.CYAN).visibleIf { !multicolour }
    private val fillColour by colourPicker("Fill colour", Colour.CYAN.withAlpha(0.5f), allowAlpha = true).visibleIf { style.selected == "Filled box" && !multicolour }
    private val activeCol by colourPicker("Active colour", Colour.WHITE)

    private val colourDropdown by text("Colours").visibleIf { multicolour }
    private val colours = RouteRegistry.nodeEntries.associate { (type, node) ->
        val set = colourPicker(type, node().colour).childOf(::colourDropdown)
        this.register(set)
        type to set
    }

    private val fillColourDropdown by text("Fill colours").visibleIf { style.selected == "Filled box" && multicolour }
    private val fillColours = RouteRegistry.nodeEntries.associate { (type, node) ->
        val set = colourPicker(type, node().colour.withAlpha(0.5f), allowAlpha = true).json("$type fill").childOf(::fillColourDropdown)
        this.register(set)
        type to set
    }

    var executing = false
        private set
    val routing: Boolean
        get() = !editMode && (executing || pendingInteract != null) && active
    var editMode = false
        set(value) {
            if (field != value) resetExecution()
            field = value
        }
    var breakerRing: BreakerNode? = null
    var currentChain: String? = null

    val routeNodes: ConfigMap<String, MutableList<RouteNode>> by configMap("route_nodes.json")
    private var roomNodes: List<RouteNode> = emptyList()
    private data class PendingInteract(val direction: Direction, val slot: Int)
    private data class ForcedNode(val node: RouteNode, val onMissingItem: ((String) -> Unit)?)
    private var pendingInteract: PendingInteract? = null
    private var position: MutableVec3? = null
    private var interactDelay = 0

    private var activeNode: RouteNode? = null
    private var forcedNode: ForcedNode? = null
    private var mimicNode: RouteNode? = null

    private var lastChainName: String? = null
    private var lastChainIndex: Int = -1

    init {
        AutoRoutesCommand.register()

        on<TickEvent.Start> {
            if (interactDelay > 0) interactDelay--
            if (editMode || breakerRing != null || roomNodes.isEmpty() || ClearExecutor.routing) {
                if (position != null || activeNode != null || pendingInteract != null || mimicNode != null) resetExecution()
                return@on
            }
            if (mimicNode != null) return@on

            pendingInteract?.let {
                if (!SwapManager.swapToSlot(it.slot).success) return@on
                player.useItem(it.direction)
                pendingInteract = null
            }

            if (activeNode?.hasSecretAwait == true) {
                EntityUtils.getEntities<Bat>(radius = 10.0) { it.maxHealth.equalsOneOf(100f, 200f, 400f, 800f) }
                    .forEach { EventDispatcher.onSecretBat(it.id) }
            }

            if (position == null) {
                position = player.position().mutable()
            }

            val desynced = roomNodes.any { it.inside() && it.triggered }

            if (!desynced) {
                roomNodes.forEach { if (!it.inside() && it.triggered) it.reset() }
                position = player.position().mutable()
            }

            handleQueue(position!!)
        }

        on<KeyEvent.Input> {
            if (editMode || activeNode == null) return@on
            input.shift = activeNode is EtherwarpNode || activeNode!!.unsneak != true
        }

        on<MouseEvent.Click> {
            if (button != 0 || !state) return@on
            if (mimicNode != null && mc.hitResult is EntityHitResult) return@on
            if (skipNode()) cancel()
        }

        on<DungeonEvent.Secret.Interact> {
            activeNode?.onSecret()
            interactDelay = 2

            if (!pauseOnMimic || editMode || !blockState.`is`(Blocks.TRAPPED_CHEST)) return@on
            if (ScanUtils.getRoomFromPos(blockPos.x, blockPos.z)?.data?.type == RoomType.TRAP) return@on
            mimicNode = activeNode ?: return@on
            pendingInteract = null
            activeNode = null
            forcedNode = null
            position = null
            executing = false
            PlayerUtils.setTitle("§cMimic!", stayAlive = 60)
        }
        on<DungeonEvent.Secret.Item> { activeNode?.onSecret() }
        on<DungeonEvent.Secret.Bat> { activeNode?.onSecret() }

        on<DungeonEvent.Room.Enter> {
            updateCache(room)
        }

        on<RenderEvent.World> {
            roomNodes.forEach { it.render(ctx, style.selected, it.colour(), it.fillColour(), activeCol, thickness) }
        }

        on<RenderEvent.Overlay> {
            val lines = mutableListOf<String>()
            if (editMode) lines.add("Edit mode")
            if (breakerRing != null) lines.add("&6DB Editor")
            if (currentChain != null) lines.add("&bChain: $currentChain")
            lines.forEachIndexed { i, string ->
                val x = (scaledWidth - string.noControlCodes.width()) / 2f
                val y = scaledHeight / 2f + 10 + i * 10
                ctx.drawText(string, x, y)
            }
        }

        on<WorldEvent.Change> {
            resetExecution()
            roomNodes = emptyList()
        }
    }

    fun skipNode(onUnavailable: ((String) -> Unit)? = null): Boolean {
        val unavailable = when {
            !active -> "Enable Auto Routes before continuing a route."
            editMode || breakerRing != null -> "Exit the route editor before continuing a route."
            ClearExecutor.routing || pendingInteract != null -> "Wait for the current action to finish before continuing the route."
            else -> null
        }
        if (unavailable != null) {
            onUnavailable?.invoke(unavailable)
            return false
        }
        val pausedNode = mimicNode
        val pos = if (pausedNode != null) player.position().mutable() else position ?: player.position().mutable()

        val nodes = (if (pausedNode != null) listOf(pausedNode) else roomNodes).filter { it.inside(pos) }.sortedByDescending { it.priority }
        if (nodes.isEmpty()) {
            onUnavailable?.invoke("There's no route step at your current position.")
            return false
        }

        val next = (nodes.indexOf(forcedNode?.node) + 1) % nodes.size
        val node = nodes[next]
        if (activeNode !== node) activeNode?.takeIf { !it.triggered }?.reset()
        mimicNode = null
        forcedNode = ForcedNode(node, onUnavailable)
        activeNode = node

        executing = true
        if (node.execute(pos, onUnavailable)) {
            node.triggered = true
            node.chain?.let {
                lastChainName = it.name
                lastChainIndex = it.index
            }
            position = pos
            executing = false
        } else {
            node.triggered = false
            executing = true
        }
        return true
    }

    fun handleQueue(playerPos: MutableVec3) {
        if (mimicNode != null) return

        if (forcedNode?.node?.inside(playerPos) == false) forcedNode = null
        val forced = forcedNode?.takeIf { !it.node.triggered }
        val node = forced?.node ?: roomNodes.asSequence()
            .filter { !it.triggered && it.inside(playerPos) }
            .filter { n ->
                val c = n.chain ?: return@filter true
                if (c.index == 0) return@filter true
                c.name == lastChainName && c.index == lastChainIndex + 1
            }
            .maxByOrNull { it.priority } ?: run {

            activeNode?.takeIf { !it.triggered }?.reset()
            activeNode = null
            executing = false

            if (roomNodes.none { it.inside() && it.triggered }) {
                position = null
            }
            return
        }

        if (activeNode != node) {
            activeNode?.takeIf { !it.triggered }?.reset()
            activeNode = node
            executing = false
        }

        if (interactDelay > 0) return

        if (forced == null && !executing && !node.checkAwaits(playerPos)) return

        executing = true
        if (node.execute(playerPos, forced?.onMissingItem)) {
            node.triggered = true
            node.chain?.let {
                lastChainName = it.name
                lastChainIndex = it.index
            }
            executing = false
        } else {
            position = null
            executing = true
        }
    }

    fun queueInteract(dir: Direction) {
        pendingInteract = PendingInteract(dir, player.inventory.selectedSlot)
    }

    private fun resetExecution() {
        roomNodes.forEach { it.reset() }
        pendingInteract = null
        activeNode = null
        forcedNode = null
        mimicNode = null
        position = null
        executing = false
        interactDelay = 0
        lastChainName = null
        lastChainIndex = -1
    }

    override fun onEnable() {
        updateCache(Dungeon.currentRoom)
    }

    override fun onDisable() {
        resetExecution()
        roomNodes = emptyList()
        AutoRoutesCommand.stopEditing()
    }

    fun updateCache(room: OdonRoom?) {
        resetExecution()
        roomNodes = room?.let { routeNodes[it.name]?.toList() }.orEmpty()
        if (room != null) roomNodes.forEach { it.update(room) }
    }

    private val RouteNode.hasSecretAwait: Boolean
        get() = this.awaits?.any { it is SecretAwait } == true

    private fun RouteNode.colour() = if (multicolour) colours[this.typeName]?.value ?: Colour.WHITE else AutoRoutes.colour
    private fun RouteNode.fillColour() = if (multicolour) fillColours[this.typeName]?.value ?: Colour.WHITE else fillColour
}
