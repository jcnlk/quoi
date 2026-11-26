package quoi.api.autoroutes.nodes

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import quoi.api.autoroutes.RouteNode
import quoi.api.colour.Colour
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonRoom
import quoi.api.vec.MutableVec3
import quoi.api.world.RaycastResult
import quoi.config.TypeName
import quoi.module.impl.dungeon.autoclear.impl.AutoRoutes
import quoi.utils.addVec
import quoi.utils.getDirection
import quoi.utils.getLook
import quoi.utils.rayCastVec
import quoi.utils.render.drawLine
import quoi.utils.skyblock.item.ItemUtils.skyblockId
import quoi.utils.skyblock.item.TeleportUtils.getEtherwarpLandingY
import quoi.utils.skyblock.item.TeleportUtils.traverseVoxels
import quoi.utils.skyblock.player.PlayerUtils.getEyeHeight
import quoi.utils.skyblock.player.SwapManager
import quoi.utils.skyblock.player.SwapResult

@TypeName("etherwarp")
class EtherwarpNode : RouteNode() {
    var target: Vec3 = Vec3.ZERO

    var yaw: Float? = null // legacy compat
    var pitch: Float? = null

    @Transient
    lateinit var realTarget: Vec3
        private set

    @Transient
    var realYaw: Float? = null

    override val colour: Colour
        get() = Colour.CYAN

    override fun update(room: OdonRoom) {
        super.update(room)
        realYaw = null

        if (yaw != null && pitch != null) {
            realYaw = room.getRealYaw(yaw!!)
            val from = pos.addVec(y = getEyeHeight(true))
            val to = getLook(realYaw!!, pitch!!).scale(61.0)

            realTarget = rayCastVec(from, to).vec
            return
        }

        realTarget = room.getRealCoords(target)
    }

    override fun execute(pos: MutableVec3, onMissingItem: ((String) -> Unit)?): Boolean {
        if (!player.lastSentInput.shift) return false

        val from = pos.add(y = getEyeHeight(true)).immutable()
        val actualTarget = targetFrom(from)
        val ether = raycast(from, actualTarget)
        if (!ether.succeeded || ether.pos == null) return false

        if (player.mainHandItem.skyblockId != "ASPECT_OF_THE_VOID") {
            val swap = SwapManager.swapById("ASPECT_OF_THE_VOID", reportMissing = onMissingItem == null)
            if (swap == SwapResult.NOT_FOUND) onMissingItem?.invoke("Put an Etherwarp item in your hotbar.")
            if (!swap.success) return swap == SwapResult.NOT_FOUND
        }

        val dir = getDirection(from, actualTarget)

        AutoRoutes.queueInteract(dir)

        pos.x = ether.pos.x + 0.5
        pos.y = getEtherwarpLandingY(ether.pos) + 0.05
        pos.z = ether.pos.z + 0.5
        return true
    }

    private fun targetFrom(from: Vec3): Vec3 = realYaw?.let {
        rayCastVec(from, getLook(it, pitch!!).scale(61.0)).vec
    } ?: realTarget

    fun raycast(pos: MutableVec3): RaycastResult {
        val from = pos.add(y = getEyeHeight(true)).immutable()
        return raycast(from, targetFrom(from))
    }

    private fun raycast(from: Vec3, target: Vec3): RaycastResult {
        if (from.distanceToSqr(target) > 61.0 * 61.0) return RaycastResult.NONE
        val to = target.add(target.subtract(from).normalize().scale(0.001))
        val hit = traverseVoxels(from, to, true)
        return if (hit.pos == BlockPos.containing(to)) hit else RaycastResult.NONE
    }

    override fun create(room: OdonRoom): RouteNode? {
        val res = rayCastVec()
        if (!res.succeeded) return null
        val vec = res.vec
        val rel = room.getRelativeCoords(vec)
        target = rel
        return this
    }

    override fun render(
        ctx: LevelRenderContext,
        style: String,
        colour: Colour,
        fillColour: Colour,
        activeColour: Colour,
        thickness: Float
    ) {
        super.render(ctx, style, colour, fillColour, activeColour, thickness)
        val col = if (this.chain != null) Colour.GREEN else Colour.WHITE
        val final = if (yaw != null) Colour.ORANGE else col

        val inside = inside()
        val actualFinal = if (inside) Colour.CYAN else final
        val thickness = if (inside) 3f else 1.5f
        ctx.drawLine(listOf(pos, realTarget), actualFinal, depth = true, thickness = thickness)
    }
}
