package quoi.utils.ui.rendering

/**
 * Minimal shelf rectangle packer for glyph atlases. Pure logic, unit tested.
 * Coordinates returned include no padding; callers should pass padded sizes.
 */
class ShelfPacker(val width: Int, val height: Int) {

    private class Shelf(val y: Int, val height: Int) {
        var x = 0
    }

    private val shelves = ArrayList<Shelf>()
    private var nextShelfY = 0

    /** Places a w×h rectangle, returning intArrayOf(x, y) or null if the atlas is full. */
    fun place(w: Int, h: Int): IntArray? {
        if (w > width || h > height) return null

        // best-fit existing shelf: smallest height that still fits
        var best: Shelf? = null
        for (shelf in shelves) {
            if (h <= shelf.height && shelf.x + w <= width) {
                if (best == null || shelf.height < best.height) best = shelf
            }
        }

        if (best == null) {
            if (nextShelfY + h > height) return null
            best = Shelf(nextShelfY, h)
            shelves.add(best)
            nextShelfY += h
        }

        val result = intArrayOf(best.x, best.y)
        best.x += w
        return result
    }
}
