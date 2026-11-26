package quoi.api.autoroutes

import quoi.api.vec.MutableVec3
import quoi.config.TypeNamed

abstract class RouteAwait : TypeNamed {

    abstract fun check(pos: MutableVec3, node: RouteNode): Boolean

    open fun reset() {}

    open fun onSecret() {}
}
