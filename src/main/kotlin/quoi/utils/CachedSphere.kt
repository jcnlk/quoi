package quoi.utils

import net.minecraft.core.BlockPos

object CachedSphere {
    const val RADIUS = 50
    const val RADIUS_SQ = RADIUS * RADIUS

    val table: LongArray
    val indices: IntArray = IntArray(RADIUS_SQ + 2)

    init {
        for (x in -RADIUS..RADIUS) {
            for (y in -RADIUS..RADIUS) {
                for (z in -RADIUS..RADIUS) {
                    val dist = x * x + y * y + z * z
                    if (dist > RADIUS_SQ) continue
                    indices[dist + 1]++
                }
            }
        }

        for (i in 1 until indices.size) {
            indices[i] += indices[i - 1]
        }

        table = LongArray(indices.last())
        val offsets = indices.copyOf()
        // Keep the original x/y/z order within each squared-distance bucket.
        for (x in -RADIUS..RADIUS) {
            for (y in -RADIUS..RADIUS) {
                for (z in -RADIUS..RADIUS) {
                    val dist = x * x + y * y + z * z
                    if (dist > RADIUS_SQ) continue
                    table[offsets[dist]++] = BlockPos.asLong(x, y, z)
                }
            }
        }
    }

    inline fun forEachInRadius(
        x: Int, y: Int, z: Int,
        radius: Float,
        block: (x: Int, y: Int, z: Int) -> Unit
    ) {
        val r = radius.sq.toInt().coerceAtMost(RADIUS_SQ)
        val end = indices[r + 1]

        for (i in 0 until end) {
            val packed = table[i]

            val dx = (packed shr 38).toInt()
            val dy = (packed shl 52 shr 52).toInt()
            val dz = (packed shl 26 shr 38).toInt()

            block(x + dx, y + dy, z + dz)
        }
    }
}