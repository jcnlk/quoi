package quoi.api.autoroutes.awaits

import quoi.api.autoroutes.RouteAwait
import quoi.api.autoroutes.RouteNode
import quoi.api.vec.MutableVec3
import quoi.config.TypeName

@TypeName("secret")
class SecretAwait(var amount: Int = 1) : RouteAwait() {

    @Transient
    var current = 0

    override fun check(pos: MutableVec3, node: RouteNode): Boolean {
        return current >= amount
    }

    override fun reset() {
        current = 0
    }

    override fun onSecret() {
        current++
    }
}
