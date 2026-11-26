package quoi.api.autoroutes.awaits

import quoi.api.autoroutes.RouteAwait
import quoi.api.autoroutes.RouteNode
import quoi.api.autoroutes.nodes.EtherwarpNode
import quoi.api.vec.MutableVec3
import quoi.config.TypeName

@TypeName("raycast")
class RaycastAwait : RouteAwait() {
    override fun check(pos: MutableVec3, node: RouteNode): Boolean {
        if (node !is EtherwarpNode) return true
        val ether = node.raycast(pos)
        return ether.succeeded && ether.pos != null
    }
}
