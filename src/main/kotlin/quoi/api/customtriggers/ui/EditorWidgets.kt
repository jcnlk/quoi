package quoi.api.customtriggers.ui

import quoi.api.abobaui.constraints.Constraint
import quoi.api.abobaui.constraints.Constraints
import quoi.api.abobaui.constraints.impl.positions.Centre
import quoi.api.abobaui.constraints.impl.size.Bounding
import quoi.api.abobaui.dsl.*
import quoi.api.abobaui.elements.Element
import quoi.api.abobaui.elements.ElementScope
import quoi.api.abobaui.elements.impl.Block
import quoi.api.abobaui.elements.impl.Block.Companion.outline
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.abobaui.elements.impl.TextInput
import quoi.api.input.CursorShape
import quoi.api.input.Keybinds
import quoi.utils.ThemeManager.theme
import quoi.utils.ui.cursor
import quoi.utils.ui.rendering.UIRenderer

/** Text truncated to the available width. */
class Label(
    private val supplier: () -> String,
    private val fontSize: Float,
    private val textColour: () -> Colour,
    constraints: Constraints,
) : Element(constraints) {
    init {
        constraints.height = fontSize.px
    }

    override fun drawCustom() {
        val font = UIRenderer.defaultFont
        val text = ellipsize(supplier(), width, fontSize)
        UIRenderer.text(text, x, y, fontSize, textColour().rgb, font)
    }
}

/**
 * Wraps text to the element width and sizes its height to fit.
 * Adds padding when [background] is set; empty text takes no space.
 */
open class Paragraph(
    private val supplier: () -> String,
    private val fontSize: Float,
    private val textColour: () -> Colour,
    constraints: Constraints,
    private val background: Colour? = null,
    private val border: Colour? = null,
) : Element(constraints) {
    private val lineHeight get() = fontSize * 1.4f
    private val padding get() = if (background != null) 10f else 0f
    private var lines: List<String> = emptyList()
    private var wrappedFor: Pair<String, Float>? = null

    init {
        constraints.height = object : Constraint.Size {
            override fun calculateSize(element: Element, horizontal: Boolean): Float {
                val count = wrapped().size
                return if (count == 0) 0f else count * lineHeight + padding * 2
            }
        }
    }

    private fun wrapped(): List<String> {
        val key = supplier() to width
        if (wrappedFor != key) {
            wrappedFor = key
            val available = width - padding * 2
            lines = if (key.first.isEmpty() || available <= 0f) emptyList()
            else key.first.split('\n').flatMap { UIRenderer.wrapText(it, available, fontSize, UIRenderer.defaultFont).ifEmpty { listOf("") } }
        }
        return lines
    }

    override fun drawCustom() {
        val before = lines.size
        val current = wrapped()
        // read while drawing so detached elements never show stale state
        if (current.size != before) redraw()
        if (current.isEmpty()) return
        background?.let { UIRenderer.rect(x, y, width, height, it.rgb, 8f) }
        border?.let { UIRenderer.hollowRect(x, y, width, height, 1f, it.rgb, 8f) }
        current.forEachIndexed { index, line ->
            UIRenderer.text(line, x + padding, y + padding + index * lineHeight, fontSize, textColour().rgb, UIRenderer.defaultFont)
        }
    }
}

/**
 * Coloured [status] followed by muted [detail], truncated to fit.
 */
class StatusLine(
    private val status: () -> Pair<String, Colour>,
    private val detail: () -> String,
    private val fontSize: Float,
    constraints: Constraints,
) : Element(constraints) {
    init {
        constraints.height = fontSize.px
    }

    override fun drawCustom() {
        val font = UIRenderer.defaultFont
        val (label, colour) = status()
        val dot = fontSize * 0.5f
        UIRenderer.circle(x + dot / 2f, y + fontSize / 2f, dot / 2f, colour.rgb)
        val labelX = x + dot + 6f
        UIRenderer.text(label, labelX, y, fontSize, colour.rgb, font)
        val rest = detail()
        if (rest.isEmpty()) return
        val restX = labelX + UIRenderer.textWidth(label, fontSize, font)
        UIRenderer.text(ellipsize(rest, x + width - restX, fontSize), restX, y, fontSize, theme.onSurfaceVariant.rgb, font)
    }
}

class CentredLabel(
    private val supplier: () -> String,
    private val fontSize: Float,
    private val textColour: () -> Colour,
    constraints: Constraints,
) : Element(constraints) {
    override fun drawCustom() {
        val font = UIRenderer.defaultFont
        val text = ellipsize(supplier(), width, fontSize)
        val w = UIRenderer.textWidth(text, fontSize, font)
        UIRenderer.text(text, x + (width - w) / 2f, y + (height - fontSize) / 2f, fontSize, textColour().rgb, font)
    }
}

fun ellipsize(text: String, maxWidth: Float, size: Float): String {
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

fun ElementScope<*>.statusLine(
    status: () -> Pair<String, Colour>, detail: () -> String, size: Float, constraints: Constraints,
    block: ElementScope<StatusLine>.() -> Unit = {},
) = StatusLine(status, detail, size, constraints).scope(block)

fun ElementScope<*>.centredLabel(text: () -> String, size: Float, colour: () -> Colour, constraints: Constraints) =
    CentredLabel(text, size, colour, constraints).scope {}

fun ElementScope<*>.label(
    text: () -> String, size: Float, colour: () -> Colour, constraints: Constraints
) = Label(text, size, colour, constraints).scope {}

fun ElementScope<*>.paragraph(
    text: () -> String, size: Float, colour: () -> Colour, constraints: Constraints,
    background: Colour? = null, border: Colour? = null,
) = Paragraph(text, size, colour, constraints, background, border).scope {}

enum class ButtonStyle { PRIMARY, OUTLINE, GHOST, DANGER }

fun ElementScope<*>.button(
    label: String,
    style: ButtonStyle = ButtonStyle.OUTLINE,
    constraints: Constraints = size(Bounding + 28.px, 32.px),
    fontSize: Float = 13.5f,
    action: ElementScope<Block>.() -> Unit,
): ElementScope<Block> {
    val fill = when (style) {
        ButtonStyle.PRIMARY -> theme.primary
        ButtonStyle.OUTLINE, ButtonStyle.DANGER -> theme.surfaceContainerHigh
        ButtonStyle.GHOST -> theme.surfaceContainerLow
    }
    val content = when (style) {
        ButtonStyle.PRIMARY -> theme.onPrimary
        ButtonStyle.DANGER -> theme.error
        else -> theme.onSurface
    }
    return block(constraints, colour = fill, radius = 8.radius()) {
        if (style == ButtonStyle.OUTLINE) outline(theme.outlineVariant, 1.px)
        if (style == ButtonStyle.DANGER) outline(theme.error.withAlpha(0.4f), 1.px)
        text(string = label, size = fontSize.px, colour = content)
        tonalHover()
        cursor(CursorShape.HAND)
        onClick {
            action()
            true
        }
    }
}

// drawn as dots because the font has no ellipsis glyph
const val MORE = "more"

const val PANEL_LEFT = "panel-left"

const val PANEL_RIGHT = "panel-right"

fun ElementScope<*>.iconButton(
    glyph: String,
    constraints: Constraints = size(28.px, 28.px),
    fill: Colour = theme.surfaceContainerHigh,
    action: ElementScope<Block>.() -> Unit,
) = block(constraints, colour = fill, radius = 7.radius()) {
    if (glyph == MORE) row(at(Centre, Centre), gap = 3.px) {
        repeat(3) { block(size(3.5.px, 3.5.px), colour = theme.outline, radius = 2.radius()) }
    } else if (glyph == PANEL_LEFT || glyph == PANEL_RIGHT) block(constrain(Centre, Centre, 18.px, 14.px), colour = Colour.TRANSPARENT, radius = 3.radius()) {
        outline(theme.onSurfaceVariant, 1.5.px)
        val x = if (glyph == PANEL_LEFT) 0.px else 0.px.alignOpposite
        block(constrain(x, 0.px, 7.px, 14.px), colour = theme.onSurfaceVariant, radius = 2.radius())
    } else text(string = glyph, size = 15.px, colour = theme.onSurfaceVariant, pos = at(Centre, Centre))
    tonalHover()
    cursor(CursorShape.HAND)
    onClick {
        action()
        true
    }
}

/** Recalculates a position or size on each layout pass. */
class Computed(private val value: (Element) -> Float) : Constraint.Measurement {
    override fun calculate(element: Element, type: Int) = value(element)
}

// keep floating panels 12 px from the screen edges
class ScreenClamp(private val value: Float, private val size: Float) : Constraint.Position {
    override fun calculatePos(element: Element, horizontal: Boolean): Float {
        val limit = element.ui.main.getSize(horizontal)
        return value.coerceIn(12f, (limit - size - 12f).coerceAtLeast(12f))
    }
}

fun ElementScope<TextInput>.escapeLeavesField() = onKeyPressed { (key, _) ->
    if (key != Keybinds.KEY_ESCAPE) return@onKeyPressed false
    ui.unfocus()
    true
}
