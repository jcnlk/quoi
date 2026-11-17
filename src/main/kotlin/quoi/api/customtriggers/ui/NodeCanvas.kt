package quoi.api.customtriggers.ui

import quoi.api.abobaui.constraints.Constraints
import quoi.api.abobaui.dsl.onClick
import quoi.api.abobaui.dsl.onMouseExit
import quoi.api.abobaui.dsl.onMouseMove
import quoi.api.abobaui.dsl.onRelease
import quoi.api.abobaui.dsl.onScroll
import quoi.api.abobaui.elements.Element
import quoi.api.abobaui.elements.ElementScope
import quoi.api.colour.Colour
import quoi.api.colour.blue
import quoi.api.colour.green
import quoi.api.colour.red
import quoi.api.colour.withAlpha
import quoi.api.customtriggers.TriggerRule
import quoi.api.customtriggers.conditions.TriggerCondition
import quoi.api.customtriggers.ui.FlowLayout.Companion.NODE_H
import quoi.api.customtriggers.ui.FlowLayout.Companion.NODE_W
import quoi.api.input.CursorShape
import quoi.api.input.Mouse
import quoi.config.TypeNamed
import quoi.config.typeName
import quoi.utils.ThemeManager.theme
import quoi.utils.ui.rendering.UIRenderer
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/**
 * Draws nodes directly so rendering and hit testing share canvas coordinates.
 */
class NodeCanvas(constraints: Constraints, private val host: Host) : Element(constraints) {
    interface Host {
        val rule: TriggerRule?
        val selected: Int?
        fun select(index: Int?)
        /** Opens the picker at screen coordinates [x], [y] for insertion at [slot]. */
        fun requestAdd(slot: Int, kind: TriggerGraph.Kind, x: Float, y: Float)
        fun reordered()
        // runs before canvas drawing
        fun onFrame()
        /** Width covered by a floating panel at the right edge, in screen pixels. */
        val coveredRight: Float
    }

    // keep fitted until the user pans or zooms
    private class View(var panX: Float = 0f, var panY: Float = 0f, var zoom: Float = 1f, var auto: Boolean = true, var fittedFor: Any? = null)

    private val views = hashMapOf<String, View>()

    private var dragNode: Int? = null
    private var dragComponent: Any? = null
    private var dragMoved = false
    private var dragWasSelected = false
    private var dragX = 0f
    private var dragY = 0f
    private var dragOffsetX = 0f
    private var dragOffsetY = 0f
    private var dropSlot: Int? = null
    private var panning = false
    private var panStartX = 0f
    private var panStartY = 0f
    private var pressX = 0f
    private var pressY = 0f
    private var cursor = CursorShape.NORMAL
    private var revealPending: Pair<String, Int>? = null
    // screen offset to keep the selected node clear of floating panels
    private var shift = 0f

    private var toast: String? = null
    private var toastError = false
    private var toastTime = 0L

    init {
        scissors = true
    }

    fun toast(message: String, error: Boolean = false) {
        toast = message
        toastError = error
        toastTime = System.currentTimeMillis()
    }

    /** Fits the flow on the next frame and keeps it fitted until the user pans or zooms. */
    fun fit(rule: TriggerRule) {
        view(rule).apply {
            auto = true
            fittedFor = null
        }
    }

    /** Reveals [index] on the next frame, unless the view is fitted. */
    fun reveal(rule: TriggerRule, index: Int) {
        revealPending = rule.id to index
    }

    fun cancelDrag() {
        dragNode = null
        dragComponent = null
        dragMoved = false
        dropSlot = null
    }

    // edits can invalidate the dragged node index
    private fun dragIndex(graph: TriggerGraph): Int? {
        val index = dragNode ?: return null
        if (index !in 1 until graph.size || graph.component(index) !== dragComponent) {
            cancelDrag()
            return null
        }
        return index
    }

    fun zoomBy(factor: Float) {
        val rule = host.rule ?: return
        zoomBy(view(rule), factor, x + width / 2f, y + height / 2f)
    }

    fun zoomPercent(): Int? = host.rule?.let { (view(it).zoom * 100).toInt() }

    private fun view(rule: TriggerRule) = views.getOrPut(rule.id) { View() }

    // stack the columns if fitting them would make the text too small
    private fun layout(rule: TriggerRule) = FlowLayout(TriggerGraph(rule), width >= (FlowLayout.WIDE_WIDTH + MARGIN * 2) * WIDE_MIN_ZOOM)

    private fun sx(view: View, cx: Float) = x + (cx + view.panX) * view.zoom - shift
    private fun sy(view: View, cy: Float) = y + (cy + view.panY) * view.zoom
    private fun cx(view: View, sx: Float) = (sx - x + shift) / view.zoom - view.panX
    private fun cy(view: View, sy: Float) = (sy - y) / view.zoom - view.panY

    private fun applyFit(view: View, layout: FlowLayout) {
        val b = layout.bounds
        // large flows start at the top left instead of shrinking text further
        val zoom = minOf(1f, (width - MARGIN * 2) / b.w, (height - MARGIN * 2) / b.h).coerceIn(FIT_MIN_ZOOM, 1f)
        view.zoom = zoom
        val viewW = width / zoom
        val viewH = height / zoom
        val margin = MARGIN / zoom
        view.panX = (if (b.w <= viewW - margin * 2) (viewW - b.w) / 2f else margin) - b.x
        view.panY = (if (b.h <= viewH - margin * 2) (viewH - b.h) / 2f else margin) - b.y
    }

    private fun applyReveal(rule: TriggerRule, view: View, layout: FlowLayout) {
        val (id, index) = revealPending ?: return
        if (id != rule.id) return
        revealPending = null
        if (view.auto) return
        val box = layout.nodes.getOrNull(index) ?: return
        val margin = 60f / view.zoom
        val viewW = width / view.zoom
        val viewH = height / view.zoom
        val minX = -view.panX
        val minY = -view.panY
        if (box.x - margin < minX) view.panX = -(box.x - margin)
        else if (box.right + margin > minX + viewW) view.panX = -(box.right + margin - viewW)
        if (box.y - margin < minY) view.panY = -(box.y - margin)
        else if (box.bottom + margin > minY + viewH) view.panY = -(box.bottom + margin - viewH)
    }

    private fun updateShift(view: View, layout: FlowLayout) {
        // dragged node follows the pointer in canvas units, so the view holds still
        if (dragNode != null) return
        val covered = host.coveredRight
        val box = host.selected?.let { layout.nodes.getOrNull(it) }
        val target = if (covered <= 0f || box == null) 0f else {
            val left = x + (box.x + view.panX) * view.zoom
            val right = x + (box.right + view.panX) * view.zoom
            (right - (x + width - covered - SHIFT_MARGIN)).coerceAtMost(left - x - SHIFT_MARGIN).coerceAtLeast(0f)
        }
        shift += (target - shift) * 0.3f
        if (abs(target - shift) < 0.5f) shift = target
    }

    private fun point(link: FlowLayout.Link, t: Float): Pair<Float, Float> {
        val u = 1 - t
        val a = u * u * u
        val b = 3 * u * u * t
        val c = 3 * u * t * t
        val d = t * t * t
        return if (link.horizontal) {
            val bend = max(30f, abs(link.x2 - link.x1) * 0.5f)
            (a * link.x1 + b * (link.x1 + bend) + c * (link.x2 - bend) + d * link.x2) to (a * link.y1 + b * link.y1 + c * link.y2 + d * link.y2)
        } else {
            val bend = max(20f, abs(link.y2 - link.y1) * 0.5f)
            (a * link.x1 + b * link.x1 + c * link.x2 + d * link.x2) to (a * link.y1 + b * (link.y1 + bend) + c * (link.y2 - bend) + d * link.y2)
        }
    }

    private fun middle(link: FlowLayout.Link) = point(link, 0.5f)

    private fun drawLink(view: View, link: FlowLayout.Link, colour: Int) {
        val z = view.zoom
        val thickness = 2f * z.coerceAtLeast(0.75f)
        val straight = if (link.horizontal) link.y1 == link.y2 else link.x1 == link.x2
        val steps = if (straight && !link.dashed) 1 else 24
        var (px, py) = point(link, 0f)
        for (i in 1..steps) {
            val (nx, ny) = point(link, i / steps.toFloat())
            if (!link.dashed || i % 2 == 0) UIRenderer.line(sx(view, px), sy(view, py), sx(view, nx), sy(view, ny), thickness, colour)
            px = nx
            py = ny
        }
        val ex = sx(view, link.x2)
        val ey = sy(view, link.y2)
        val s = 6f * z
        if (link.horizontal) {
            UIRenderer.line(ex - s, ey - s, ex, ey, thickness, colour)
            UIRenderer.line(ex - s, ey + s, ex, ey, thickness, colour)
        } else {
            UIRenderer.line(ex - s, ey - s, ex, ey, thickness, colour)
            UIRenderer.line(ex + s, ey - s, ex, ey, thickness, colour)
        }
    }

    private fun plusAt(layout: FlowLayout, mx: Float, my: Float, zoom: Float): FlowLayout.Link? = layout.links.firstOrNull { link ->
        val (px, py) = middle(link)
        link.slot != null && hypot(mx - px, my - py) <= (plusRadius(zoom) + 4f) / zoom
    }

    private fun nodeAt(layout: FlowLayout, mx: Float, my: Float): Int? =
        layout.nodes.indices.lastOrNull { layout.nodes[it].contains(mx, my) }

    fun ElementScope<NodeCanvas>.registerInput() {
        onClick(0) {
            val rule = host.rule ?: return@onClick false
            val view = view(rule)
            val layout = layout(rule)
            val mx = cx(view, ui.mx)
            val my = cy(view, ui.my)
            pressX = ui.mx
            pressY = ui.my
            plusAt(layout, mx, my, view.zoom)?.let { link ->
                host.requestAdd(link.slot!!, TriggerGraph.Kind.ACTION, ui.mx + 18f, ui.my - 20f)
                return@onClick true
            }
            if (layout.addAction.contains(mx, my)) {
                host.requestAdd(layout.graph.size, TriggerGraph.Kind.ACTION, sx(view, layout.addAction.x), sy(view, layout.addAction.bottom) + 6f)
                return@onClick true
            }
            if (layout.addCondition.contains(mx, my)) {
                host.requestAdd(layout.graph.boundary, TriggerGraph.Kind.CONDITION, sx(view, layout.addCondition.x), sy(view, layout.addCondition.bottom) + 6f)
                return@onClick true
            }
            val node = nodeAt(layout, mx, my)
            if (node != null) {
                // select on release so a drag doesn't open the panel over the drop targets
                if (layout.graph.kind(node) != TriggerGraph.Kind.ACTION) host.select(node)
                else {
                    val box = layout.nodes[node]
                    dragNode = node
                    dragComponent = layout.graph.component(node)
                    dragWasSelected = host.selected == node
                    dragMoved = false
                    dragX = box.x
                    dragY = box.y
                    dragOffsetX = mx - box.x
                    dragOffsetY = my - box.y
                }
            } else {
                host.select(null)
                startPan(view)
            }
            true
        }
        onClick(2) {
            val rule = host.rule ?: return@onClick false
            pressX = ui.mx
            pressY = ui.my
            startPan(view(rule))
            true
        }
        onMouseMove {
            val rule = host.rule ?: return@onMouseMove false
            val view = view(rule)
            if (panning) {
                // plain click on empty space only deselects, view stays fitted
                if (view.auto && hypot(ui.mx - pressX, ui.my - pressY) < 4f) return@onMouseMove false
                view.auto = false
                view.panX = panStartX + (ui.mx - pressX) / view.zoom
                view.panY = panStartY + (ui.my - pressY) / view.zoom
            }
            val layout = layout(rule)
            val index = dragIndex(layout.graph) ?: return@onMouseMove false
            if (!dragMoved && hypot(ui.mx - pressX, ui.my - pressY) < 5f) return@onMouseMove false
            dragMoved = true
            dragX = cx(view, ui.mx) - dragOffsetX
            dragY = cy(view, ui.my) - dragOffsetY
            dropSlot = findDropSlot(layout, index)
            false
        }
        onRelease(0) { finishDrag() }
        onRelease(2) { panning = false }
        onScroll { (amount) ->
            val rule = host.rule ?: return@onScroll false
            zoomBy(view(rule), if (amount > 0) 1.12f else 1 / 1.12f, ui.mx, ui.my)
            true
        }
        onMouseExit { setCursor(CursorShape.NORMAL) }
    }

    private fun startPan(view: View) {
        panning = true
        panStartX = view.panX
        panStartY = view.panY
    }

    private fun zoomBy(view: View, factor: Float, screenX: Float, screenY: Float) {
        view.auto = false
        val beforeX = cx(view, screenX)
        val beforeY = cy(view, screenY)
        view.zoom = (view.zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        view.panX = (screenX - x + shift) / view.zoom - beforeX
        view.panY = (screenY - y) / view.zoom - beforeY
    }

    private fun validSlots(graph: TriggerGraph, index: Int) =
        graph.slots(TriggerGraph.Kind.ACTION).filter { it != index && it != index + 1 }

    private fun findDropSlot(layout: FlowLayout, index: Int): Int? {
        val centreX = dragX + NODE_W / 2f
        val centreY = dragY + NODE_H / 2f
        return validSlots(layout.graph, index)
            .map { slot -> slot to layout.dropPoint(slot).let { (px, py) -> hypot(centreX - px, centreY - py) } }
            .filter { it.second < DROP_DISTANCE }
            .minByOrNull { it.second }?.first
    }

    private fun finishDrag() {
        panning = false
        val rule = host.rule
        val graph = rule?.let(::TriggerGraph)
        val index = graph?.let(::dragIndex)
        val slot = dropSlot
        val moved = dragMoved
        val wasSelected = dragWasSelected
        cancelDrag()
        if (index == null) return
        if (!moved) {
            host.select(index)
            return
        }
        if (slot == null) return
        val target = graph.move(index, slot) ?: return
        // indices shift after a move, so only the moved action stays selected
        host.select(if (wasSelected) target else null)
        host.reordered()
    }

    private fun setCursor(shape: Long) {
        if (cursor == shape) return
        cursor = shape
        Mouse.setCursor(shape)
    }

    override fun drawCustom() {
        host.onFrame()
        UIRenderer.rect(x, y, width, height, theme.surface.rgb)
        val rule = host.rule
        if (rule == null) {
            drawGrid(View())
            drawCentred("No trigger selected", "Create one with \"New trigger\" on the left.")
            setCursor(CursorShape.NORMAL)
            return
        }
        val view = view(rule)
        val layout = layout(rule)
        val graph = layout.graph
        val fitKey = listOf(width, height, layout.bounds)
        if (view.auto && view.fittedFor != fitKey && width > 0f && height > 0f) {
            applyFit(view, layout)
            view.fittedFor = fitKey
        }
        applyReveal(rule, view, layout)
        updateShift(view, layout)
        drawGrid(view)

        val zoom = view.zoom
        val mx = cx(view, ui.mx)
        val my = cy(view, ui.my)
        val dragging = dragIndex(graph)?.takeIf { dragMoved }
        val pointer = hovered && dragNode == null && !panning
        val hoverPlus = if (pointer) plusAt(layout, mx, my, zoom) else null
        val free = pointer && hoverPlus == null
        val hoverNode = if (free) nodeAt(layout, mx, my) else null
        val hoverAddAction = free && hoverNode == null && layout.addAction.contains(mx, my)
        val hoverAddCondition = free && hoverNode == null && layout.addCondition.contains(mx, my)
        setCursor(
            when {
                !hovered -> CursorShape.NORMAL
                hoverPlus != null || hoverNode != null || hoverAddAction || hoverAddCondition -> CursorShape.HAND
                else -> CursorShape.NORMAL
            }
        )

        layout.captions.forEach { drawCaption(view, graph, it) }
        drawConditionGroup(view, layout)

        val linkColour = theme.outline.withAlpha(0.7f).rgb
        layout.links.forEach { drawLink(view, it, if (it.dashed) theme.outline.withAlpha(0.4f).rgb else linkColour) }

        layout.ands.forEach { (ax, ay) -> drawAnd(view, ax, ay) }
        drawAdd(view, layout.addCondition, "Add condition", hoverAddCondition, emphasise = false)
        drawAdd(view, layout.addAction, if (graph.rule.actions.isEmpty()) "Add an action" else "Add action", hoverAddAction,
            emphasise = graph.rule.actions.isEmpty())

        layout.nodes.forEachIndexed { index, box ->
            if (index != dragging) drawNode(view, graph, index, box, hovered = index == hoverNode)
        }

        if (dragging == null) layout.links.forEach { link -> if (link.slot != null) drawPlus(view, link, link == hoverPlus) }

        if (dragging != null) {
            validSlots(graph, dragging).forEach { slot ->
                val (px, py) = layout.dropPoint(slot)
                drawDropTarget(view, px, py, slot == dropSlot)
            }
            UIRenderer.push()
            UIRenderer.globalAlpha(1f)
            drawNode(view, graph, dragging, FlowLayout.Box(dragX, dragY, NODE_W, NODE_H), hovered = true, lifted = true)
            UIRenderer.pop()
        }

        drawOverlay(dragging != null)
    }

    private fun drawGrid(view: View) {
        var spacing = GRID * view.zoom
        while (spacing < 14f) spacing *= 2f
        val offsetX = ((view.panX * view.zoom - shift) % spacing + spacing) % spacing
        val offsetY = ((view.panY * view.zoom) % spacing + spacing) % spacing
        val dot = max(1.5f, 2f * view.zoom)
        val colour = theme.outlineVariant.withAlpha(0.4f).rgb
        var gx = x + offsetX
        while (gx < x + width) {
            var gy = y + offsetY
            while (gy < y + height) {
                UIRenderer.rect(gx, gy, dot, dot, colour)
                gy += spacing
            }
            gx += spacing
        }
    }

    private fun drawCaption(view: View, graph: TriggerGraph, caption: FlowLayout.Caption) {
        val font = UIRenderer.defaultFont
        val z = view.zoom
        val size = 13f * z
        val conditions = graph.rule.conditions.size
        val (title, detail) = when (caption.kind) {
            TriggerGraph.Kind.EVENT -> "WHEN" to "this happens"
            TriggerGraph.Kind.CONDITION -> "ONLY IF" to when (conditions) {
                0 -> "optional, no checks yet"
                1 -> "this is true"
                2 -> "both are true"
                else -> "all $conditions are true"
            }
            TriggerGraph.Kind.ACTION -> "THEN DO" to if (graph.rule.actions.size > 1) "these in order" else "this"
        }
        val cx = sx(view, caption.x)
        val cy = sy(view, caption.y)
        UIRenderer.text(title, cx, cy, size, accentText(caption.kind).rgb, font)
        UIRenderer.text(detail, cx + UIRenderer.textWidth(title, size, font) + 7f * z, cy, size, theme.onSurfaceVariant.rgb, font)
    }

    private fun drawConditionGroup(view: View, layout: FlowLayout) {
        val g = layout.conditionGroup
        val z = view.zoom
        val gx = sx(view, g.x)
        val gy = sy(view, g.y)
        UIRenderer.rect(gx, gy, g.w * z, g.h * z, CONDITION_ACCENT.withAlpha(0.05f).rgb, 14f * z)
        UIRenderer.hollowRect(gx, gy, g.w * z, g.h * z, 1f, CONDITION_ACCENT.withAlpha(0.3f).rgb, 14f * z)
    }

    private fun drawAnd(view: View, ax: Float, ay: Float) {
        val font = UIRenderer.defaultFont
        val z = view.zoom
        val size = 11.5f * z
        val w = UIRenderer.textWidth("AND", size, font) + 18f * z
        val h = 20f * z
        val cx = sx(view, ax)
        val cy = sy(view, ay)
        UIRenderer.line(cx, cy - 18f * z, cx, cy + 18f * z, 1.5f * z, CONDITION_ACCENT.withAlpha(0.35f).rgb)
        UIRenderer.rect(cx - w / 2f, cy - h / 2f, w, h, theme.surfaceContainerHigh.rgb, h / 2f)
        UIRenderer.hollowRect(cx - w / 2f, cy - h / 2f, w, h, 1f, CONDITION_ACCENT.withAlpha(0.45f).rgb, h / 2f)
        UIRenderer.text("AND", cx - w / 2f + 9f * z, cy - size / 2f, size, accentText(TriggerGraph.Kind.CONDITION).rgb, font)
    }

    private fun drawNode(view: View, graph: TriggerGraph, index: Int, box: FlowLayout.Box, hovered: Boolean, lifted: Boolean = false) {
        val z = view.zoom
        val nx = sx(view, box.x)
        val ny = sy(view, box.y)
        val w = box.w * z
        val h = box.h * z
        if (nx > x + width || ny > y + height || nx + w < x || ny + h < y) return

        val kind = graph.kind(index)
        val component = graph.component(index)
        val accent = accent(kind)
        val error = graph.error(index)
        val selected = host.selected == index
        val radius = 10f * z
        val headerH = HEADER_H * z

        UIRenderer.dropShadow(nx, ny + 2f * z, w, h, (if (lifted) 28f else 12f) * z, 0f, radius,
            Colour.RGB(0, 0, 0, if (lifted) 0.45f else 0.25f).rgb)
        UIRenderer.rect(nx, ny, w, h, (if (hovered) theme.surfaceContainerHigh else theme.surfaceContainer).rgb, radius)
        UIRenderer.rect(nx, ny, w, headerH, accent.withAlpha(0.12f).rgb, radius, 0f, 0f, radius)
        UIRenderer.rect(nx, ny + headerH - 1f, w, 1f, accent.withAlpha(0.2f).rgb)

        val font = UIRenderer.defaultFont
        val inverted = component is TriggerCondition && !component.expected
        val chip = chipLabel(kind, index - graph.boundary + 1)
        val chipSize = 11.5f * z
        val chipTextW = UIRenderer.textWidth(chip, chipSize, font)
        val chipW = max(chipTextW + 14f * z, 24f * z)
        val chipH = 20f * z
        val chipX = nx + 10f * z
        val chipY = ny + (headerH - chipH) / 2f
        UIRenderer.rect(chipX, chipY, chipW, chipH, accent.withAlpha(0.22f).rgb, 5f * z)
        UIRenderer.text(chip, chipX + (chipW - chipTextW) / 2f, chipY + (chipH - chipSize) / 2f, chipSize, accentText(kind).rgb, font)

        var titleRight = nx + w - 12f * z
        if (inverted) {
            val tagSize = 11.5f * z
            val tagW = UIRenderer.textWidth(INVERTED_TAG, tagSize, font)
            titleRight -= tagW + 10f * z
            UIRenderer.text(INVERTED_TAG, nx + w - 12f * z - tagW, ny + (headerH - tagSize) / 2f, tagSize, accentText(kind).withAlpha(0.85f).rgb, font)
        }
        val titleSize = 15f * z
        val titleX = chipX + chipW + 9f * z
        val name = NodeCatalog.info((component as TypeNamed).typeName).name
        UIRenderer.text(fit(name, titleRight - titleX, titleSize), titleX, ny + (headerH - titleSize) / 2f, titleSize, theme.onSurface.rgb, font)

        val bodySize = 13.5f * z
        val bodyX = nx + 12f * z
        val bodyW = w - 24f * z
        val lineH = bodySize * 1.4f
        var lineY = ny + headerH + 9f * z
        val summary = if (component is TriggerCondition) component.summary() else component.displayString()
        wrap(summary, bodyW, bodySize, if (error != null) 1 else 2).forEach { line ->
            UIRenderer.text(line, bodyX, lineY, bodySize, theme.onSurfaceVariant.rgb, font)
            lineY += lineH
        }
        if (error != null) {
            val dotR = 3f * z
            val errorY = ny + h - 10f * z - bodySize
            UIRenderer.circle(bodyX + dotR, errorY + bodySize / 2f, dotR, theme.error.rgb)
            UIRenderer.text(fit(error, bodyW - dotR * 2 - 6f * z, bodySize), bodyX + dotR * 2 + 6f * z, errorY, bodySize, theme.error.rgb, font)
        }

        when {
            selected -> UIRenderer.hollowRect(nx, ny, w, h, 2f, theme.primary.rgb, radius)
            error != null -> UIRenderer.hollowRect(nx, ny, w, h, 1f, theme.error.withAlpha(0.7f).rgb, radius)
            else -> UIRenderer.hollowRect(nx, ny, w, h, 1f, (if (hovered) theme.outline else theme.outlineVariant).rgb, radius)
        }
    }

    private fun drawAdd(view: View, box: FlowLayout.Box, label: String, hovered: Boolean, emphasise: Boolean) {
        val z = view.zoom
        val gx = sx(view, box.x)
        val gy = sy(view, box.y)
        val w = box.w * z
        val h = box.h * z
        if (gx > x + width || gy > y + height || gx + w < x || gy + h < y) return
        val colour = if (hovered || emphasise) theme.primary else theme.onSurfaceVariant
        UIRenderer.rect(gx, gy, w, h, (if (hovered) theme.primary.withAlpha(0.1f) else theme.surface.withAlpha(0.7f)).rgb, 10f * z)
        dashedRect(gx, gy, w, h, colour.withAlpha(if (hovered || emphasise) 0.9f else 0.45f).rgb, z)
        val font = UIRenderer.defaultFont
        val size = 14f * z
        val text = "+  $label"
        val tw = UIRenderer.textWidth(text, size, font)
        UIRenderer.text(text, gx + (w - tw) / 2f, gy + (h - size) / 2f, size, colour.rgb, font)
    }

    private fun plusRadius(zoom: Float) = PLUS_R * zoom.coerceAtLeast(0.85f)

    private fun drawPlus(view: View, link: FlowLayout.Link, hot: Boolean) {
        val (px, py) = middle(link)
        val cx = sx(view, px)
        val cy = sy(view, py)
        val r = plusRadius(view.zoom)
        UIRenderer.circle(cx, cy, r + 3f, theme.surface.rgb)
        UIRenderer.circle(cx, cy, r, (if (hot) theme.primary else theme.outline).rgb)
        UIRenderer.circle(cx, cy, r - 1.5f, (if (hot) theme.primary else theme.surfaceContainerHigh).rgb)
        val arm = r * 0.45f
        val thickness = 2f * view.zoom.coerceAtLeast(0.85f)
        val colour = (if (hot) theme.onPrimary else theme.onSurface).rgb
        UIRenderer.line(cx - arm, cy, cx + arm, cy, thickness, colour)
        UIRenderer.line(cx, cy - arm, cx, cy + arm, thickness, colour)
        if (hot) tooltip("Insert an action here", cx + r + 8f, cx - r - 8f, cy)
    }

    private fun drawDropTarget(view: View, px: Float, py: Float, active: Boolean) {
        val z = view.zoom
        val cx = sx(view, px)
        val cy = sy(view, py)
        val half = NODE_W * z / 2f
        UIRenderer.rect(cx - half, cy - 1.5f, half * 2, 3f, theme.primary.withAlpha(if (active) 1f else 0.3f).rgb, 1.5f)
        if (active) tooltip("Move here", cx + half + 10f, cx - half - 10f, cy, filled = true)
    }

    // place the tooltip to the left if it would cross the right edge
    private fun tooltip(text: String, left: Float, fallbackRight: Float, centreY: Float, filled: Boolean = false) {
        val font = UIRenderer.defaultFont
        val size = 13f
        val w = UIRenderer.textWidth(text, size, font) + 20f
        val left = if (left + w > x + width - host.coveredRight - 8f) fallbackRight - w else left
        val h = 26f
        UIRenderer.rect(left, centreY - h / 2f, w, h, (if (filled) theme.primary else theme.surfaceContainerHighest).rgb, 6f)
        UIRenderer.text(text, left + 10f, centreY - size / 2f, size, (if (filled) theme.onPrimary else theme.onSurface).rgb, font)
    }

    private fun dashedRect(x0: Float, y0: Float, w: Float, h: Float, colour: Int, z: Float) {
        val dash = 7f * z
        val gap = 5f * z
        val t = 1.25f
        var d = 6f * z
        while (d < w - 6f * z) {
            val e = minOf(d + dash, w - 6f * z)
            UIRenderer.line(x0 + d, y0, x0 + e, y0, t, colour)
            UIRenderer.line(x0 + d, y0 + h, x0 + e, y0 + h, t, colour)
            d += dash + gap
        }
        d = 6f * z
        while (d < h - 6f * z) {
            val e = minOf(d + dash, h - 6f * z)
            UIRenderer.line(x0, y0 + d, x0, y0 + e, t, colour)
            UIRenderer.line(x0 + w, y0 + d, x0 + w, y0 + e, t, colour)
            d += dash + gap
        }
    }

    private fun drawOverlay(dragging: Boolean) {
        val font = UIRenderer.defaultFont
        val help = if (dragging) "Drop the action on a highlighted line to move it there."
        else "Click a step to edit it  ·  Drag an action to reorder  ·  Drag empty space to move  ·  Scroll to zoom"
        val visible = width - host.coveredRight
        val shown = fit(help, visible - 48f, 13f)
        // backdrop keeps the hint readable over the flow
        UIRenderer.rect(x + 8f, y + height - 36f, UIRenderer.textWidth(shown, 13f, font) + 20f, 26f, theme.surface.withAlpha(0.85f).rgb, 6f)
        UIRenderer.text(shown, x + 18f, y + height - 29f, 13f, theme.onSurfaceVariant.withAlpha(0.8f).rgb, font)

        val message = toast ?: return
        val age = System.currentTimeMillis() - toastTime
        if (age > TOAST_MS) {
            toast = null
            return
        }
        val alpha = ((TOAST_MS - age) / 400f).coerceIn(0f, 1f)
        val size = 14f
        val tw = UIRenderer.textWidth(message, size, font)
        val bw = tw + 32f
        val bx = x + (visible - bw) / 2f
        val by = y + height - 84f
        val background = if (toastError) theme.errorContainer else theme.surfaceContainerHighest
        UIRenderer.dropShadow(bx, by, bw, 38f, 18f, 0f, 19f, Colour.RGB(0, 0, 0, 0.35f * alpha).rgb)
        UIRenderer.rect(bx, by, bw, 38f, background.withAlpha(alpha).rgb, 19f)
        val fg = if (toastError) theme.onErrorContainer else theme.onSurface
        UIRenderer.text(message, bx + 16f, by + (38f - size) / 2f, size, fg.withAlpha(alpha).rgb, font)
    }

    private fun drawCentred(title: String, subtitle: String) {
        val font = UIRenderer.defaultFont
        val tw = UIRenderer.textWidth(title, 20f, font)
        val sw = UIRenderer.textWidth(subtitle, 14f, font)
        UIRenderer.text(title, x + (width - tw) / 2f, y + height / 2f - 24f, 20f, theme.onSurface.rgb, font)
        UIRenderer.text(subtitle, x + (width - sw) / 2f, y + height / 2f + 6f, 14f, theme.onSurfaceVariant.rgb, font)
    }

    private fun fit(text: String, maxWidth: Float, size: Float): String {
        val font = UIRenderer.defaultFont
        if (UIRenderer.textWidth(text, size, font) <= maxWidth) return text
        var low = 0
        var high = text.length
        while (low < high) {
            val mid = (low + high + 1) / 2
            if (UIRenderer.textWidth(text.take(mid) + "…", size, font) <= maxWidth) low = mid else high = mid - 1
        }
        return if (low == 0) "" else text.take(low).trimEnd() + "…"
    }

    private fun wrap(text: String, maxWidth: Float, size: Float, maxLines: Int): List<String> {
        val lines = UIRenderer.wrapText(text, maxWidth, size, UIRenderer.defaultFont)
        if (lines.size <= maxLines) return lines
        val kept = lines.take(maxLines).toMutableList()
        kept[maxLines - 1] = fit(kept[maxLines - 1] + " " + lines.drop(maxLines).joinToString(" "), maxWidth, size)
        return kept
    }

    companion object {
        const val HEADER_H = 38f
        const val PLUS_R = 13f
        const val GRID = 24f
        const val DROP_DISTANCE = 110f
        const val MARGIN = 56f
        const val MIN_ZOOM = 0.4f
        const val FIT_MIN_ZOOM = 0.75f
        const val WIDE_MIN_ZOOM = 0.85f
        const val MAX_ZOOM = 1.6f
        const val TOAST_MS = 3500L
        const val SHIFT_MARGIN = 24f
        const val INVERTED_TAG = "inverted"

        val EVENT_ACCENT: Colour = Colour.RGB(122, 162, 247)
        val CONDITION_ACCENT: Colour = Colour.RGB(224, 175, 104)
        val ACTION_ACCENT: Colour = Colour.RGB(115, 201, 145)

        fun accent(kind: TriggerGraph.Kind) = when (kind) {
            TriggerGraph.Kind.EVENT -> EVENT_ACCENT
            TriggerGraph.Kind.CONDITION -> CONDITION_ACCENT
            TriggerGraph.Kind.ACTION -> ACTION_ACCENT
        }

        // darken text on light themes to keep enough contrast
        fun accentText(kind: TriggerGraph.Kind): Colour {
            val accent = accent(kind)
            if (theme.isDark) return accent
            return Colour.RGB((accent.red * 0.55f).toInt(), (accent.green * 0.55f).toInt(), (accent.blue * 0.55f).toInt())
        }

        fun chipLabel(kind: TriggerGraph.Kind, step: Int) = when (kind) {
            TriggerGraph.Kind.EVENT -> "WHEN"
            TriggerGraph.Kind.CONDITION -> "IF"
            TriggerGraph.Kind.ACTION -> "$step"
        }
    }
}
