package quoi.api.skyblock.dungeon.odonscanning.tiles

enum class RoomType {
    BLOOD, CHAMPION, ENTRANCE, FAIRY, NORMAL, PUZZLE, RARE, TRAP, UNKNOWN;

    companion object {
        fun fromMapColour(colour: Int): RoomType? = when (colour) {
            18 -> BLOOD
            30 -> ENTRANCE
            62 -> TRAP
            63 -> NORMAL
            66 -> PUZZLE
            74 -> CHAMPION
            82 -> FAIRY
            85, 119 -> UNKNOWN
            else -> null
        }
    }
}
