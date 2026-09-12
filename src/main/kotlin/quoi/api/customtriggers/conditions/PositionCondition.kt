package quoi.api.customtriggers.conditions

import quoi.QuoiMod.mc
import quoi.api.abobaui.constraints.impl.size.Copying
import quoi.api.abobaui.dsl.*
import quoi.api.abobaui.elements.ElementScope
import quoi.api.customtriggers.TriggerContext
import quoi.config.TypeName
import quoi.utils.ThemeManager.theme
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import quoi.api.customtriggers.numberField
import quoi.api.customtriggers.fieldRow

@TypeName("player_position")
class PositionCondition(var aabb: AABB = AABB(BlockPos(0, 0, 0))) : TriggerCondition {

    override fun validationError() = if (listOf(aabb.minX, aabb.minY, aabb.minZ, aabb.maxX, aabb.maxY, aabb.maxZ).any { !it.isFinite() } || aabb.xsize <= 0 || aabb.ysize <= 0 || aabb.zsize <= 0) "Position bounds need positive size on each axis." else null

    override fun matches(ctx: TriggerContext): Boolean {
        return mc.player?.let { aabb.intersects(it.boundingBox) } == true
    }

    override fun displayString(): String {
        val x = (aabb.minX + aabb.maxX) / 2.0
        val y = (aabb.minY + aabb.maxY) / 2.0
        val z = (aabb.minZ + aabb.maxZ) / 2.0
        val size = "${aabb.maxX - aabb.minX}x${aabb.maxY - aabb.minY}x${aabb.maxZ - aabb.minZ}"
        return "Player at $x,$y,$z [$size]"
    }

    override fun ElementScope<*>.draw() = column(size(w = Copying), gap = 8.px) {
        val values = doubleArrayOf(aabb.minX, aabb.minY, aabb.minZ, aabb.maxX, aabb.maxY, aabb.maxZ)
        listOf("Min", "Max").forEachIndexed { row, prefix ->
            fieldRow(*listOf("X", "Y", "Z").mapIndexed { axis, label ->
                val index = row * 3 + axis
                val field: ElementScope<*>.() -> Unit = {
                    numberField("$prefix $label", { values[index] }) {
                        values[index] = it
                        aabb = AABB(values[0], values[1], values[2], values[3], values[4], values[5])
                    }
                }
                field
            }.toTypedArray())
        }
    }
}