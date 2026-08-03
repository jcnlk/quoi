package quoi.module.impl.mining.glacitetunnels

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.world.phys.Vec3
import quoi.utils.StringUtils.noControlCodes
import java.util.PriorityQueue

internal data class TunnelNode(
    val id: Int,
    val position: Vec3,
    val name: String?,
    val neighbours: Map<Int, Double>,
)

internal class TargetSearch(
    private val distances: Map<Int, Double>,
    private val next: Map<Int, Int>,
    private val targetByNode: Map<Int, Int>,
) {
    fun routeFrom(
        position: Vec3,
        graph: TunnelGraph,
        preferredEntryId: Int?,
        directlyReachable: (Vec3, Vec3) -> Boolean,
    ): List<TunnelNode>? {
        val nearby = graph.entryCandidates(position)
        val reachable = nearby.filter { directlyReachable(position, it.position) }.ifEmpty { nearby.take(1) }
        val scored = reachable.mapNotNull { node ->
            val graphDistance = distances[node.id] ?: return@mapNotNull null
            val targetId = targetByNode[node.id] ?: return@mapNotNull null
            EntryScore(node, position.distanceTo(node.position) + graphDistance, targetId)
        }
        val best = scored.minByOrNull(EntryScore::cost) ?: return null
        val preferred = preferredEntryId?.let { id -> scored.firstOrNull { it.node.id == id } }
        val selected = preferred?.takeIf {
            it.targetId == best.targetId && it.cost <= best.cost + 2.5
        } ?: best

        val nodes = mutableListOf(selected.node)
        while (nodes.last().id != selected.targetId) {
            val nextId = next[nodes.last().id] ?: return null
            nodes += graph[nextId] ?: return null
        }
        return nodes
    }

    private data class EntryScore(val node: TunnelNode, val cost: Double, val targetId: Int)
}

internal class TunnelGraph(private val nodes: Map<Int, TunnelNode>) {
    private val namedNodes = nodes.values.mapNotNull { node ->
        node.name?.let { normalize(it) to node }
    }.groupBy(keySelector = { it.first }, valueTransform = { it.second })

    private val incomingEdges: Map<Int, List<Pair<Int, Double>>> = mutableMapOf<Int, MutableMap<Int, Double>>().apply {
        nodes.values.forEach { node ->
            node.neighbours.forEach { (destination, weight) ->
                getOrPut(destination) { mutableMapOf() }.merge(node.id, weight, ::minOf)
                getOrPut(node.id) { mutableMapOf() }.merge(destination, weight, ::minOf)
            }
        }
    }.mapValues { (_, neighbours) -> neighbours.map { it.key to it.value } }

    operator fun get(id: Int): TunnelNode? = nodes[id]

    fun targetNameForCommission(commission: String): String? = normalize(
        commission.removeSuffix(" Collector").removeSuffix(" Commission")
    ).takeIf(namedNodes::containsKey)

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
            val targetId = targetByNode.getValue(current.id)
            incomingEdges[current.id].orEmpty().forEach { (nextId, weight) ->
                val candidate = current.distance + weight
                if (candidate < (distances[nextId] ?: Double.POSITIVE_INFINITY)) {
                    distances[nextId] = candidate
                    next[nextId] = current.id
                    targetByNode[nextId] = targetId
                    queue += QueueEntry(nextId, candidate)
                }
            }
        }
        return TargetSearch(distances, next, targetByNode)
    }

    fun entryCandidates(position: Vec3): List<TunnelNode> {
        val nearest = PriorityQueue(compareByDescending(EntryCandidate::distanceSquared))
        nodes.values.forEach { node ->
            val candidate = EntryCandidate(node, node.position.distanceToSqr(position))
            if (nearest.size < ENTRY_CANDIDATE_LIMIT) nearest += candidate
            else if (candidate.distanceSquared < nearest.peek().distanceSquared) {
                nearest.poll()
                nearest += candidate
            }
        }
        return nearest.sortedBy(EntryCandidate::distanceSquared).map(EntryCandidate::node)
    }

    private data class QueueEntry(val id: Int, val distance: Double)
    private data class EntryCandidate(val node: TunnelNode, val distanceSquared: Double)

    companion object {
        private const val ENTRY_CANDIDATE_LIMIT = 24
        private val NORMALIZE_PATTERN = Regex("[^a-z0-9]+")

        fun load(): TunnelGraph {
            val stream = TunnelGraph::class.java.getResourceAsStream("/assets/quoi/glacite_tunnels_graph.json")
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

        private fun JsonObject?.toWeights(): Map<Int, Double> = this?.entrySet()
            ?.associate { (id, weight) -> id.toInt() to weight.asDouble }
            .orEmpty()

        private fun normalize(value: String): String = value.noControlCodes
            .lowercase()
            .replace(NORMALIZE_PATTERN, " ")
            .trim()
    }
}
