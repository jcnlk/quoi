package quoi.api.skyblock.dungeon.odonscanning

import quoi.api.vec.Vec2i

internal object MapTextLayout {
    // NoammAddons uses the room's footprint, with conservative bounds for L and 2x2 rooms.
    fun roomBounds(placements: Collection<Vec2i>): Vec2i {
        val width = placements.maxOf { it.x } - placements.minOf { it.x } + 16
        val height = placements.maxOf { it.z } - placements.minOf { it.z } + 16
        return when {
            width > 16 && height > 16 -> Vec2i(32, if (placements.size == 3) 16 else 32)
            else -> Vec2i(width, height)
        }
    }

    fun fitSize(
        lines: List<String>,
        preferredSize: Float,
        maxWidth: Float,
        maxHeight: Float,
        measure: (String, Float) -> Float,
    ): Float {
        val size = minOf(preferredSize, maxHeight / lines.size)
        fun fits(size: Float) = lines.all { measure(it, size) <= maxWidth }
        if (fits(size)) return size

        // Custom font advances are quantized, so measure again at each candidate size.
        var low = 0f
        var high = size
        repeat(12) {
            val candidate = (low + high) / 2f
            if (fits(candidate)) low = candidate else high = candidate
        }
        return low
    }
}
