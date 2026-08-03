package quoi.module.impl.mining.glacitetunnels

import net.minecraft.world.phys.Vec3

internal class GlaciteTunnelNavigator(
    private val graph: () -> TunnelGraph,
    private val directlyReachable: (Vec3, Vec3) -> Boolean,
) {
    var target: TunnelNode? = null
        private set
    var route: List<TunnelNode> = emptyList()
        private set
    val hasCommission: Boolean
        get() = commission != null

    private var commission: String? = null
    private var search: TargetSearch? = null
    private var entryNodeId: Int? = null
    private var lastRoutePosition: Vec3? = null
    private var waitingAtTarget = false
    private val recentlyVisited = mutableMapOf<Int, Long>()

    fun update(playerPosition: Vec3) {
        val commission = commission ?: return hideRoute()
        if (waitingAtTarget) return

        val now = System.currentTimeMillis()
        recentlyVisited.entries.removeIf { it.value <= now }

        target?.let { currentTarget ->
            if (playerPosition.distanceTo(currentTarget.position) <= ARRIVAL_RADIUS) {
                markReachedTargetCluster(commission, playerPosition, now)
                waitAtTarget()
                return
            }
        }

        val movedEnough = lastRoutePosition?.distanceToSqr(playerPosition)
            ?.let { it >= ROUTE_UPDATE_DISTANCE_SQUARED }
            ?: true
        if (!movedEnough && route.isNotEmpty()) return

        if (search == null) search = createSearch(commission)
        val nodes = search?.routeFrom(playerPosition, graph(), entryNodeId, directlyReachable)
            ?: return clearRoute(keepTarget = true)
        entryNodeId = nodes.first().id
        lastRoutePosition = playerPosition
        val destination = nodes.last()
        target = destination

        if (playerPosition.distanceTo(destination.position) <= ARRIVAL_RADIUS) {
            markReachedTargetCluster(commission, playerPosition, now)
            waitAtTarget()
            return
        }

        route = nodes.dropVisiblePrefix(playerPosition)
    }

    fun startCommission(commission: String) {
        graph().targets(commission).forEach { recentlyVisited.remove(it.id) }
        this.commission = commission
        startRoute()
    }

    fun clearCommission() {
        commission = null
        waitingAtTarget = false
        clearPath()
    }

    fun skipCurrentTarget() {
        val commission = commission ?: return
        target?.let { markReachedTargetCluster(commission, it.position, System.currentTimeMillis()) }
        startRoute()
    }

    fun hideRoute() {
        clearRoute()
    }

    fun reset() {
        clearCommission()
        recentlyVisited.clear()
    }

    private fun createSearch(commission: String): TargetSearch? {
        val graph = graph()
        val allTargets = graph.targets(commission)
        val available = allTargets.filter { it.id !in recentlyVisited }
        if (available.isNotEmpty()) return graph.searchFromTargets(available)

        allTargets.forEach { recentlyVisited.remove(it.id) }
        return graph.searchFromTargets(allTargets)
    }

    private fun markReachedTargetCluster(commission: String, position: Vec3, now: Long) {
        graph().targets(commission)
            .filter { position.distanceTo(it.position) <= ARRIVAL_RADIUS }
            .forEach { recentlyVisited[it.id] = now + VISITED_COOLDOWN_MS }
    }

    private fun waitAtTarget() {
        waitingAtTarget = true
        clearPath()
    }

    private fun startRoute() {
        waitingAtTarget = false
        clearPath()
        search = commission?.let(::createSearch)
    }

    private fun clearPath() {
        search = null
        entryNodeId = null
        lastRoutePosition = null
        target = null
        route = emptyList()
    }

    private fun clearRoute(keepTarget: Boolean = false) {
        route = emptyList()
        if (!keepTarget) target = null
    }

    private fun List<TunnelNode>.dropVisiblePrefix(position: Vec3): List<TunnelNode> {
        if (size < 2) return this
        val furthestVisible = indices.reversed().firstOrNull {
            directlyReachable(position, this[it].position)
        } ?: 0
        return drop(furthestVisible)
    }

    private companion object {
        const val ARRIVAL_RADIUS = 7.0
        const val VISITED_COOLDOWN_MS = 60_000L
        const val ROUTE_UPDATE_DISTANCE_SQUARED = 1.0
    }
}
