package quoi.module.impl.render

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import quoi.api.skyblock.location.Location.inSkyblock
import quoi.module.Module
import java.util.Optional
import kotlin.math.min

object RevertMasterStars : Module(
    name = "Revert Master Stars",
    desc = "Reverts Master Stars to the old red star display."
) {
    private const val STAR_SEQUENCE = "✪✪✪✪✪"
    private const val FIRST_MASTER_STAR = '➊'
    private const val LAST_MASTER_STAR = '➎'

    @JvmStatic
    fun modifyHoverName(component: Component): Component {
        if (!active || !inSkyblock) return component

        val text = component.string
        val starsStart = findMasterStars(text)
        if (starsStart == -1) return component

        val masterStarIndex = starsStart + STAR_SEQUENCE.length
        val redStarsEnd = starsStart + (text[masterStarIndex] - FIRST_MASTER_STAR + 1)
        val result = Component.empty()
        var segmentStart = 0

        component.visit<Unit>({ style, value ->
            appendSegment(
                result = result,
                value = value,
                style = style,
                segmentStart = segmentStart,
                redStart = starsStart,
                redEnd = redStarsEnd,
                removedIndex = masterStarIndex
            )
            segmentStart += value.length
            Optional.empty()
        }, Style.EMPTY)

        return result
    }

    private fun findMasterStars(text: String): Int {
        var searchFrom = 0

        while (true) {
            val starsStart = text.indexOf(STAR_SEQUENCE, searchFrom)
            if (starsStart == -1) return -1

            val masterStar = text.getOrNull(starsStart + STAR_SEQUENCE.length)
            if (masterStar != null && masterStar in FIRST_MASTER_STAR..LAST_MASTER_STAR) return starsStart
            searchFrom = starsStart + 1
        }
    }

    private fun appendSegment(
        result: net.minecraft.network.chat.MutableComponent,
        value: String,
        style: Style,
        segmentStart: Int,
        redStart: Int,
        redEnd: Int,
        removedIndex: Int
    ) {
        val segmentEnd = segmentStart + value.length
        var cursor = segmentStart

        while (cursor < segmentEnd) {
            if (cursor == removedIndex) {
                cursor++
                continue
            }

            var nextBoundary = segmentEnd
            if (redStart > cursor) nextBoundary = min(nextBoundary, redStart)
            if (redEnd > cursor) nextBoundary = min(nextBoundary, redEnd)
            if (removedIndex > cursor) nextBoundary = min(nextBoundary, removedIndex)

            val localStart = cursor - segmentStart
            val localEnd = nextBoundary - segmentStart
            val outputStyle = if (cursor in redStart until redEnd) {
                style.withColor(ChatFormatting.RED)
            } else {
                style
            }

            result.append(Component.literal(value.substring(localStart, localEnd)).withStyle(outputStyle))
            cursor = nextBoundary
        }
    }
}
