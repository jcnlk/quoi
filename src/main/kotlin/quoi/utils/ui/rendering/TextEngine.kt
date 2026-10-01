package quoi.utils.ui.rendering

/**
 * Pure (Minecraft-independent) text helpers: legacy §/& formatting-code parsing and
 * word wrapping. Extracted from the renderer so they can be unit tested and shared
 * between backends. Behaviour mirrors the historical NanoVG renderer exactly.
 */
object TextEngine {

    val FORMATTING_CODE = Regex("[§&][0-9a-fk-or]", RegexOption.IGNORE_CASE)

    private val SPLIT_PATTERN = Regex("(?=${FORMATTING_CODE.pattern})")

    fun stripCodes(text: String): String = FORMATTING_CODE.replace(text, "")

    data class Segment(val text: String, val color: Int, val underline: Boolean, val strike: Boolean)

    /**
     * Splits [text] into renderable segments, applying legacy colour codes. The alpha
     * channel of [baseColour] is preserved for all colour codes; `§r`/`&r` restores the
     * base colour and clears decorations; `§l`, `§o` and `§k` are ignored.
     */
    fun parse(text: String, baseColour: Int): List<Segment> {
        val alpha = baseColour and -0x1000000

        var col = baseColour
        var underline = false
        var strike = false

        val segments = ArrayList<Segment>()

        for (string in text.split(SPLIT_PATTERN)) {
            var part = string
            if (part.length >= 2 && (part[0] == '§' || part[0] == '&')) {
                when (val code = part[1].lowercaseChar()) {
                    'n' -> underline = true
                    'm' -> strike = true
                    'r' -> {
                        col = baseColour
                        underline = false
                        strike = false
                    }
                    'l', 'o', 'k' -> {}
                    else -> {
                        val c = colourForCode(code)
                        if (c != -1) col = alpha or (c and 0xFFFFFF)
                    }
                }
                part = part.substring(2)
            }

            if (part.isNotEmpty()) segments.add(Segment(part, col, underline, strike))
        }
        return segments
    }

    /** Opaque ARGB for a legacy colour code, or -1 if [code] is not a colour code. */
    fun colourForCode(code: Char): Int = when (code) {
        '0' -> 0xFF000000.toInt()
        '1' -> 0xFF0000AA.toInt()
        '2' -> 0xFF00AA00.toInt()
        '3' -> 0xFF00AAAA.toInt()
        '4' -> 0xFFAA0000.toInt()
        '5' -> 0xFFAA00AA.toInt()
        '6' -> 0xFFFFAA00.toInt()
        '7' -> 0xFFAAAAAA.toInt()
        '8' -> 0xFF555555.toInt()
        '9' -> 0xFF5555FF.toInt()
        'a' -> 0xFF55FF55.toInt()
        'b' -> 0xFF55FFFF.toInt()
        'c' -> 0xFFFF5555.toInt()
        'd' -> 0xFFFF55FF.toInt()
        'e' -> 0xFFFFFF55.toInt()
        'f' -> 0xFFFFFFFF.toInt()
        else -> -1
    }

    /**
     * Greedy word wrap. [measure] receives candidate lines with control codes stripped,
     * mirroring the historical NanoVG implementation.
     */
    fun wrap(text: String, maxWidth: Float, measure: (String) -> Float): List<String> {
        val lines = mutableListOf<String>()

        text.split("\n").forEach { p ->
            var currentLine = ""

            p.split(" ").forEachIndexed { i, word ->
                val line = if (i == 0) word else "$currentLine $word"
                if (measure(stripCodes(line)) <= maxWidth) {
                    currentLine = line
                } else {
                    lines.add(currentLine)
                    currentLine = word
                }
            }

            lines.add(currentLine)
        }
        return lines
    }
}
