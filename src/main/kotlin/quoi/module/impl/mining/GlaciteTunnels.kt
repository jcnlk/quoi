package quoi.module.impl.mining

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import quoi.api.colour.Colour
import quoi.api.events.AreaEvent
import quoi.api.events.ChatEvent
import quoi.api.events.GuiEvent
import quoi.api.events.MouseEvent
import quoi.api.events.PacketEvent
import quoi.api.events.RenderEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.on
import quoi.api.skyblock.location.Island
import quoi.api.skyblock.location.Location
import quoi.module.Module
import quoi.utils.ChatUtils.command
import quoi.utils.EntityUtils.renderPos
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.render.drawLine
import quoi.utils.render.drawText
import quoi.utils.skyblock.item.ItemUtils.lore
import quoi.utils.skyblock.item.ItemUtils.skyblockId
import java.util.PriorityQueue

object GlaciteTunnels : Module(
    "Glacite Tunnels",
    area = Island.DwarvenMines,
    desc = "Navigation and utilities for the Glacite Tunnels.",
    // TODO: add subarea req here
) {
    private val commissionRoutes by switch("Commission routes", desc = "Shows the best route to the next collector commission location.")
    @Suppress("unused")
    private val baseWarpKey by keybind("Base warp", desc = "Warps to the Dwarven Base Camp with /warp basecamp.")
        .onPress {
            if (active && inGlaciteEnvironment() && mc.screen == null) command("warp basecamp")
        }
    private val pathColour by colourPicker("Path colour", Colour.RGB(76, 235, 160), allowAlpha = true)
    private val pathWidth by slider("Path width", 4f, 1f, 12f, 1f)
    private val targetTextSize by slider("Target text size", 1f, 0.5f, 2.5f, 0.1f)
    private val depthCheck by switch("Depth check", true)

    private val graph by lazy(LazyThreadSafetyMode.NONE) { TunnelGraph.load() }
    private var targetName: String? = null
    private var target: TunnelNode? = null
    private var route: List<TunnelNode> = emptyList()
    private var commission: String? = null
    private var search: TargetSearch? = null
    private var entryNodeId: Int? = null
    private var lastRoutePosition: Vec3? = null
    private var waitingAtTarget = false
    private var ticks = 0
    private var hasPigeonData = false
    private var awaitingClaim = false
    private val commissionMenu = CommissionMenuTracker(COMMISSIONS_MENU)
    private var claimedSlotId: Int? = null
    private var claimBaseline = emptyList<String>()
    private val recentlyVisited = mutableMapOf<Int, Long>()

    init {
        on<TickEvent.End> {
            if (!inGlaciteEnvironment()) {
                reset()
                return@on
            }
            ticks++
            commissionMenu.consume()?.let(::refreshFromCommissionMenu)
            if (!commissionRoutes) {
                clearRoute()
                return@on
            }
            updateRoute()
        }

        on<RenderEvent.World> {
            if (!inGlaciteEnvironment()) return@on
            val destination = target ?: return@on
            if (route.isEmpty()) return@on
            val points = buildList {
                add(player.renderPos.add(0.0, 0.15, 0.0))
                route.forEach { add(it.position.add(0.0, 0.15, 0.0)) }
            }
            if (points.size > 1) ctx.drawLine(points, pathColour, depth = depthCheck, thickness = pathWidth)

            val viewer = player.renderPos
            val targetTextPosition = destination.position.add(0.0, 1.8, 0.0)
            val targetDistance = viewer.distanceTo(targetTextPosition).coerceAtLeast(MIN_TEXT_DISTANCE)
            val renderDistance = targetDistance.coerceAtMost(MAX_TEXT_DISTANCE)
            val renderPosition = viewer.add(targetTextPosition.subtract(viewer).scale(renderDistance / targetDistance))
            val displayName = (destination.name ?: targetName ?: "Commission").let {
                if (it.startsWith('§') && it.length >= 2) it.take(2) + "§l" + it.drop(2) else "§f§l$it"
            }
            ctx.drawText(
                Component.literal(displayName),
                renderPosition,
                shadow = true,
                scale = (renderDistance / 12.0 * targetTextSize).toFloat(),
            )
        }

        on<WorldEvent.Change> { reset() }
        on<AreaEvent.Main> { if (area != Island.DwarvenMines) reset() }
        on<AreaEvent.Sub> { if (!isGlaciteSubarea(subarea)) reset() }

        on<ChatEvent.Packet> {
            if (COMMISSION_COMPLETE.matches(unformatted.noControlCodes.trim())) awaitClaim()
        }

        on<MouseEvent.Click> {
            if (button != 0 || !state || mc.screen != null) return@on
            if (player.mainHandItem.skyblockId == "ROYAL_PIGEON") requestNewRoute(skipCurrentTarget = true)
        }

        on<PacketEvent.ReceivedPost> {
            commissionMenu.handle(packet)
        }

        on<GuiEvent.Slot.Click> {
            val container = screen as? AbstractContainerScreen<*> ?: return@on
            if (!container.title.string.noControlCodes.equals(COMMISSIONS_MENU, ignoreCase = true)) return@on
            if (slot.item.lore?.any { it.noControlCodes.contains("COMPLETED", ignoreCase = true) } == true) {
                claimedSlotId = slotId
                claimBaseline = container.menu.slots.map { it.item.signature() }
                hasPigeonData = true
                awaitClaim()
            }
        }
    }

    override fun onDisable() {
        reset()
        super.onDisable()
    }

    private fun updateRoute() {
        if (commission == null && !hasPigeonData && !awaitingClaim && ticks % TABLIST_FALLBACK_TICKS == 1) {
            CommissionDisplay.currentActiveCommissionNames()
                .firstNotNullOfOrNull(graph::targetNameForCommission)
                ?.let(::applyFreshCommission)
        }
        val commission = commission ?: return clearRoute()
        if (waitingAtTarget) return

        val now = System.currentTimeMillis()
        recentlyVisited.entries.removeIf { it.value <= now }
        if (search == null) search = createSearch(commission)
        val playerPosition = player.position()

        target?.let { currentTarget ->
            if (playerPosition.distanceTo(currentTarget.position) <= ARRIVAL_RADIUS) {
                markReachedTargetCluster(commission, playerPosition, now)
                waitAtTarget()
                return
            }
        }

        val movedEnough = lastRoutePosition?.distanceToSqr(playerPosition)?.let { it >= ROUTE_UPDATE_DISTANCE_SQUARED } ?: true
        if (!movedEnough && route.isNotEmpty()) return
        val result = search?.routeFrom(
            position = playerPosition,
            graph = graph,
            preferredEntryId = entryNodeId,
            directlyReachable = ::hasWalkingLine,
        ) ?: return clearRoute(keepTarget = true)
        entryNodeId = result.entry.id
        lastRoutePosition = playerPosition
        target = result.target

        val arrivalDistance = playerPosition.distanceTo(result.target.position)
        if (arrivalDistance <= ARRIVAL_RADIUS) {
            markReachedTargetCluster(commission, playerPosition, now)
            waitAtTarget()
            return
        }

        route = result.nodes.dropVisiblePrefix(playerPosition)
    }

    private fun markReachedTargetCluster(commission: String, position: Vec3, now: Long) {
        graph.targets(commission)
            .filter { position.distanceTo(it.position) <= ARRIVAL_RADIUS }
            .forEach { recentlyVisited[it.id] = now + VISITED_COOLDOWN_MS }
    }

    private fun createSearch(commission: String): TargetSearch? {
        val allTargets = graph.targets(commission)
        val available = allTargets.filter { it.id !in recentlyVisited }
        if (available.isNotEmpty()) return graph.searchFromTargets(available)

        allTargets.forEach { recentlyVisited.remove(it.id) }
        return graph.searchFromTargets(allTargets)
    }

    private fun waitAtTarget() {
        waitingAtTarget = true
        search = null
        entryNodeId = null
        lastRoutePosition = null
        target = null
        route = emptyList()
    }

    private fun requestNewRoute(skipCurrentTarget: Boolean) {
        val commission = commission ?: return
        if (skipCurrentTarget) target?.let {
            markReachedTargetCluster(commission, it.position, System.currentTimeMillis())
        }
        this.commission = commission
        targetName = commission
        waitingAtTarget = false
        search = createSearch(commission)
        entryNodeId = null
        lastRoutePosition = null
        target = null
        route = emptyList()
    }

    private fun applyMenuSnapshot(snapshot: List<String>, sawActiveCommission: Boolean) {
        if (sawActiveCommission || snapshot.isNotEmpty()) hasPigeonData = true
        val freshCommission = snapshot.firstOrNull()
        if (freshCommission == null) {
            if (sawActiveCommission) {
                awaitingClaim = false
                awaitNextCommission()
            }
            return
        }

        applyFreshCommission(freshCommission)
    }

    private fun applyFreshCommission(freshCommission: String) {
        graph.targets(freshCommission).forEach { recentlyVisited.remove(it.id) }
        commission = freshCommission
        targetName = freshCommission
        waitingAtTarget = false
        awaitingClaim = false
        search = createSearch(freshCommission)
        entryNodeId = null
        lastRoutePosition = null
        target = null
        route = emptyList()
    }

    private fun parseCommissionItems(items: Iterable<ItemStack>): Pair<List<String>, Boolean> {
        var hasActiveCommission = false
        val navigable = linkedSetOf<String>()
        items.forEach { item ->
            val lines = item.lore?.map { it.noControlCodes.trim() }.orEmpty()
            if (lines.none(GLACITE_REWARD::matches) || lines.any { it.equals("COMPLETED", ignoreCase = true) }) return@forEach
            val name = sequenceOf(item.hoverName.string.noControlCodes.trim())
                .plus(lines.asSequence())
                .firstOrNull(COLLECTOR_COMMISSION::matches)
                ?: return@forEach
            hasActiveCommission = true
            graph.targetNameForCommission(name)?.let(navigable::add)
        }
        return navigable.toList() to hasActiveCommission
    }

    private fun refreshFromCommissionMenu(snapshot: CommissionMenuTracker.Snapshot) {
        if (awaitingClaim && snapshot.fullUpdate) {
            val (targets, hasActiveCommission) = parseCommissionItems(snapshot.items)
            if (!hasActiveCommission) return
            applyMenuSnapshot(targets, true)
            clearClaimTracking()
            return
        }

        claimedSlotId?.let { slotId ->
            val changedTargets = snapshot.items.mapIndexedNotNull { index, item ->
                if (item.signature() == claimBaseline.getOrNull(index)) return@mapIndexedNotNull null
                val (active, target) = item.activeCommissionTarget()
                if (active) index to target else null
            }
            val target = changedTargets.firstOrNull { it.first == slotId }?.second
                ?: changedTargets.firstOrNull()?.second
            if (changedTargets.isNotEmpty()) {
                hasPigeonData = true
                if (target == null) awaitNextCommission() else applyFreshCommission(target)
                clearClaimTracking()
                return
            }
            return
        }

        val (targets, hasActiveCommission) = parseCommissionItems(snapshot.items)
        if (hasActiveCommission) {
            applyMenuSnapshot(targets, true)
            clearClaimTracking()
        }
    }

    private fun ItemStack.signature(): String = buildString {
        append(hoverName.string.noControlCodes)
        append('|')
        lore.orEmpty().forEach { append(it.noControlCodes).append('\n') }
    }

    private fun ItemStack.activeCommissionTarget(): Pair<Boolean, String?> {
        val lines = lore?.map { it.noControlCodes.trim() }.orEmpty()
        if (lines.none(GLACITE_REWARD::matches) || lines.any { it.equals("COMPLETED", ignoreCase = true) }) return false to null
        val name = sequenceOf(hoverName.string.noControlCodes.trim())
            .plus(lines.asSequence())
            .firstOrNull(COLLECTOR_COMMISSION::matches)
            ?: return false to null
        return true to graph.targetNameForCommission(name)
    }

    private fun awaitNextCommission() {
        commission = null
        targetName = null
        waitingAtTarget = false
        search = null
        entryNodeId = null
        lastRoutePosition = null
        target = null
        route = emptyList()
    }

    private fun awaitClaim() {
        awaitingClaim = true
        awaitNextCommission()
    }

    private fun clearClaimTracking() {
        claimedSlotId = null
        claimBaseline = emptyList()
    }

    private fun reset() {
        targetName = null
        target = null
        route = emptyList()
        commission = null
        search = null
        entryNodeId = null
        lastRoutePosition = null
        waitingAtTarget = false
        awaitingClaim = false
        hasPigeonData = false
        commissionMenu.reset()
        clearClaimTracking()
        recentlyVisited.clear()
        ticks = 0
    }

    private fun clearRoute(keepTarget: Boolean = false) {
        route = emptyList()
        if (!keepTarget) target = null
    }

    private fun List<TunnelNode>.dropVisiblePrefix(position: Vec3): List<TunnelNode> {
        if (size < 2) return this
        val furthestVisible = indices.reversed().firstOrNull { hasWalkingLine(position, this[it].position) } ?: 0
        return drop(furthestVisible)
    }

    private fun hasWalkingLine(from: Vec3, to: Vec3): Boolean {
        val level = mc.level ?: return false
        val eyeOffset = Vec3(0.0, 1.0, 0.0)
        return level.clip(ClipContext(from.add(eyeOffset), to.add(eyeOffset), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).type == HitResult.Type.MISS
    }

    private fun inGlaciteEnvironment(): Boolean = isGlaciteSubarea(Location.subarea)

    private fun isGlaciteSubarea(value: String?): Boolean = value != null && GLACITE_SUBAREAS.any {
        value.contains(it, ignoreCase = true)
    }

    private const val ARRIVAL_RADIUS = 7.0
    private const val VISITED_COOLDOWN_MS = 60_000L
    private const val ROUTE_UPDATE_DISTANCE_SQUARED = 1.0
    private const val TABLIST_FALLBACK_TICKS = 20
    private const val MIN_TEXT_DISTANCE = 5.0
    private const val MAX_TEXT_DISTANCE = 50.0
    private const val COMMISSIONS_MENU = "Commissions"
    private val COLLECTOR_COMMISSION = Regex("^.+ Collector$")
    private val GLACITE_REWARD = Regex("^- [\\d,]+ Glacite Powder$")
    private val COMMISSION_COMPLETE = Regex("^.* Commission Complete!.*$")
    private val GLACITE_SUBAREAS = setOf("Dwarven Base Camp", "Glacite Tunnels", "Great Glacite Lake")
}

private data class TunnelNode(
    val id: Int,
    val position: Vec3,
    val name: String?,
    val neighbours: MutableMap<Int, Double>,
)

private data class RouteResult(val nodes: List<TunnelNode>, val entry: TunnelNode, val target: TunnelNode)

private class TargetSearch(
    private val distances: Map<Int, Double>,
    private val next: Map<Int, Int>,
    private val targetByNode: Map<Int, Int>,
) {
    fun routeFrom(
        position: Vec3,
        graph: TunnelGraph,
        preferredEntryId: Int?,
        directlyReachable: (Vec3, Vec3) -> Boolean,
    ): RouteResult? {
        val nearby = graph.entryCandidates(position)
        val reachable = nearby.filter { directlyReachable(position, it.position) }.ifEmpty { nearby.take(1) }
        val scored = reachable.mapNotNull { node ->
            val graphDistance = distances[node.id] ?: return@mapNotNull null
            EntryScore(node, position.distanceTo(node.position) + graphDistance, targetByNode[node.id] ?: return@mapNotNull null)
        }
        val best = scored.minByOrNull(EntryScore::cost) ?: return null
        val preferred = preferredEntryId?.let { id -> scored.firstOrNull { it.node.id == id } }
        val selected = preferred?.takeIf {
            it.targetId == best.targetId && it.cost <= best.cost + ENTRY_SWITCH_MARGIN
        } ?: best
        val entry = selected.node
        val targetId = targetByNode[entry.id] ?: return null
        val nodes = mutableListOf(entry)
        while (nodes.last().id != targetId) nodes += graph[next[nodes.last().id] ?: return null] ?: return null
        return RouteResult(nodes, entry, graph[targetId] ?: return null)
    }

    private data class EntryScore(val node: TunnelNode, val cost: Double, val targetId: Int)

    private companion object {
        const val ENTRY_SWITCH_MARGIN = 2.5
    }
}

private class TunnelGraph(private val nodes: Map<Int, TunnelNode>) {
    private val namedNodes = nodes.values.filter { it.name != null }.groupBy { normalize(it.name!!) }
    private val incomingEdges: Map<Int, List<Pair<Int, Double>>> = mutableMapOf<Int, MutableMap<Int, Double>>().apply {
        nodes.values.forEach { node ->
            node.neighbours.forEach { (destination, weight) ->
                getOrPut(destination) { mutableMapOf() }.merge(node.id, weight, ::minOf)
                getOrPut(node.id) { mutableMapOf() }.merge(destination, weight, ::minOf)
            }
        }
    }.mapValues { (_, neighbours) -> neighbours.map { it.key to it.value } }

    operator fun get(id: Int): TunnelNode? = nodes[id]

    fun entryCandidates(position: Vec3): List<TunnelNode> {
        val nearest = PriorityQueue(compareByDescending(EntryCandidate::distanceSquared))
        nodes.values.forEach { node ->
            val candidate = EntryCandidate(node, node.position.distanceToSqr(position))
            if (nearest.size < 24) nearest += candidate
            else if (candidate.distanceSquared < nearest.peek().distanceSquared) {
                nearest.poll()
                nearest += candidate
            }
        }
        return nearest.sortedBy(EntryCandidate::distanceSquared).map(EntryCandidate::node)
    }

    fun targetNameForCommission(commission: String): String? {
        val wanted = normalize(commission.removeSuffix(" Collector").removeSuffix(" Commission"))
        if (wanted == "glacite" || wanted == "scrap") return null
        return wanted.takeIf(namedNodes::containsKey)
    }

    fun targets(name: String): List<TunnelNode> = namedNodes[name].orEmpty()

    fun searchFromTargets(targets: List<TunnelNode>): TargetSearch? {
        if (targets.isEmpty()) return null
        val distances = mutableMapOf<Int, Double>()
        val next = mutableMapOf<Int, Int>()
        val targetByNode = mutableMapOf<Int, Int>()
        val queue = PriorityQueue(compareBy<QueueEntry> { it.distance })
        targets.forEach {
            distances[it.id] = 0.0
            targetByNode[it.id] = it.id
            queue += QueueEntry(it.id, 0.0)
        }

        while (queue.isNotEmpty()) {
            val current = queue.poll()
            if (current.distance != distances[current.id]) continue
            incomingEdges[current.id].orEmpty().forEach { (nextId, weight) ->
                val candidate = current.distance + weight
                if (candidate < (distances[nextId] ?: Double.POSITIVE_INFINITY)) {
                    distances[nextId] = candidate
                    next[nextId] = current.id
                    targetByNode[nextId] = targetByNode[current.id] ?: return@forEach
                    queue += QueueEntry(nextId, candidate)
                }
            }
        }
        return TargetSearch(distances, next, targetByNode)
    }

    private data class QueueEntry(val id: Int, val distance: Double)
    private data class EntryCandidate(val node: TunnelNode, val distanceSquared: Double)

    companion object {
        fun load(): TunnelGraph {
            val stream = GlaciteTunnels::class.java.getResourceAsStream("/assets/quoi/glacite_tunnels_graph.json")
                ?: error("Missing Glacite Tunnels graph")
            val root = stream.reader(Charsets.UTF_8).use { JsonParser.parseReader(it).asJsonObject }
            val nodes = root.entrySet().associate { (idString, value) ->
                val id = idString.toInt()
                val json = value.asJsonObject
                val coordinates = json["Position"].asString.split(':').map(String::toDouble)
                id to TunnelNode(
                    id,
                    Vec3(coordinates[0], coordinates[1], coordinates[2]),
                    json["Name"]?.asString,
                    json["Neighbours"]?.asJsonObject.toWeights(),
                )
            }
            return TunnelGraph(nodes)
        }

        private fun JsonObject?.toWeights(): MutableMap<Int, Double> = this?.entrySet()
            ?.associateTo(mutableMapOf()) { (id, weight) -> id.toInt() to weight.asDouble }
            ?: mutableMapOf()

        private fun normalize(value: String): String = value.noControlCodes
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
    }
}
