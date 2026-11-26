package quoi.api.customtriggers.ui

/**
 * Flow positions in canvas units at 100% zoom.
 * Uses three columns when [wide], otherwise stacks the sections vertically.
 */
class FlowLayout(val graph: TriggerGraph, val wide: Boolean) {
    data class Box(val x: Float, val y: Float, val w: Float, val h: Float) {
        val right get() = x + w
        val bottom get() = y + h
        val centreX get() = x + w / 2f
        val centreY get() = y + h / 2f
        fun contains(px: Float, py: Float) = px in x..right && py in y..bottom
    }

    /** [slot] is the insertion point for the + button, or `null` for links without one. */
    data class Link(
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
        val horizontal: Boolean,
        val slot: Int?,
        val dashed: Boolean
    )

    data class Caption(val kind: TriggerGraph.Kind, val x: Float, val y: Float)

    // indices match TriggerGraph
    val nodes: List<Box>
    val conditionGroup: Box
    val addCondition: Box
    val addAction: Box

    // centres of the AND labels between conditions
    val ands: List<Pair<Float, Float>>
    val links: List<Link>
    val captions: List<Caption>
    val bounds: Box

    private val actionX: Float

    init {
        val conditions = graph.rule.conditions.size
        val actions = graph.rule.actions.size
        val boxes = mutableListOf(Box(0f, 0f, NODE_W, NODE_H))
        val event = boxes[0]

        val groupX = if (wide) NODE_W + COLUMN_GAP - GROUP_PAD else -GROUP_PAD
        val groupY = if (wide) 0f else event.bottom + STAGE_GAP
        val innerX = groupX + GROUP_PAD
        var y = groupY + GROUP_PAD
        val andCentres = mutableListOf<Pair<Float, Float>>()
        repeat(conditions) { i ->
            if (i > 0) {
                andCentres += innerX + NODE_W / 2f to y + AND_GAP / 2f
                y += AND_GAP
            }
            boxes += Box(innerX, y, NODE_W, NODE_H)
            y += NODE_H
        }
        if (conditions > 0) y += ADD_GAP
        addCondition = Box(innerX, y, NODE_W, ADD_H)
        conditionGroup = Box(groupX, groupY, NODE_W + GROUP_PAD * 2, addCondition.bottom + GROUP_PAD - groupY)

        actionX = if (wide) (NODE_W + COLUMN_GAP) * 2 else 0f
        y = if (wide) 0f else conditionGroup.bottom + STAGE_GAP
        repeat(actions) { i ->
            if (i > 0) y += ACTION_GAP
            boxes += Box(actionX, y, NODE_W, NODE_H)
            y += NODE_H
        }
        addAction = Box(actionX, if (actions > 0) y + ADD_GAP * 2 else y, NODE_W, ADD_H)
        nodes = boxes
        ands = andCentres

        val firstAction = if (actions > 0) nodes[graph.boundary] else addAction
        links = buildList {
            if (wide) {
                add(Link(event.right, event.centreY, conditionGroup.x, event.centreY, true, null, false))
                add(
                    Link(
                        conditionGroup.right,
                        event.centreY,
                        firstAction.x,
                        firstAction.centreY,
                        true,
                        graph.boundary.takeIf { actions > 0 },
                        actions == 0
                    )
                )
            } else {
                add(Link(event.centreX, event.bottom, event.centreX, conditionGroup.y, false, null, false))
                add(
                    Link(
                        event.centreX,
                        conditionGroup.bottom,
                        firstAction.centreX,
                        firstAction.y,
                        false,
                        graph.boundary.takeIf { actions > 0 },
                        actions == 0
                    )
                )
            }
            for (i in 1 until actions) {
                val above = nodes[graph.boundary + i - 1]
                add(
                    Link(
                        above.centreX,
                        above.bottom,
                        above.centreX,
                        nodes[graph.boundary + i].y,
                        false,
                        graph.boundary + i,
                        false
                    )
                )
            }
            if (actions > 0) add(
                Link(
                    addAction.centreX,
                    nodes.last().bottom,
                    addAction.centreX,
                    addAction.y,
                    false,
                    null,
                    true
                )
            )
        }

        captions = if (wide) listOf(
            Caption(TriggerGraph.Kind.EVENT, 0f, groupY - CAPTION_GAP),
            Caption(TriggerGraph.Kind.CONDITION, innerX, groupY - CAPTION_GAP),
            Caption(TriggerGraph.Kind.ACTION, actionX, groupY - CAPTION_GAP),
        ) else listOf(
            Caption(TriggerGraph.Kind.EVENT, 0f, -CAPTION_GAP),
            Caption(TriggerGraph.Kind.CONDITION, event.centreX + 18f, conditionGroup.y - CAPTION_GAP),
            Caption(TriggerGraph.Kind.ACTION, event.centreX + 18f, firstAction.y - CAPTION_GAP),
        )

        val parts =
            nodes + conditionGroup + addCondition + addAction + captions.map { Box(it.x, it.y, CAPTION_W, CAPTION_H) }
        val left = parts.minOf { it.x }
        val top = parts.minOf { it.y }
        bounds = Box(left, top, parts.maxOf { it.right } - left, parts.maxOf { it.bottom } - top)
    }

    /** Centre of the gap where an action dropped at [slot] lands. */
    fun dropPoint(slot: Int): Pair<Float, Float> {
        val first = graph.boundary
        val centreX = actionX + NODE_W / 2f
        return when {
            slot <= first -> centreX to nodes[first].y - ACTION_GAP / 2f
            slot >= graph.size -> centreX to (nodes[graph.size - 1].bottom + addAction.y) / 2f
            else -> centreX to (nodes[slot - 1].bottom + nodes[slot].y) / 2f
        }
    }

    companion object {
        const val NODE_W = 280f
        const val NODE_H = 88f
        const val COLUMN_GAP = 100f
        const val STAGE_GAP = 84f
        const val ACTION_GAP = 48f
        const val AND_GAP = 36f
        const val ADD_GAP = 14f
        const val ADD_H = 46f
        const val GROUP_PAD = 14f
        const val CAPTION_GAP = 34f
        const val CAPTION_W = 230f
        const val CAPTION_H = 18f

        const val WIDE_WIDTH = NODE_W * 3 + COLUMN_GAP * 2
    }
}
