package quoi.api.customtriggers

import quoi.api.abobaui.constraints.impl.positions.Centre
import quoi.api.abobaui.constraints.impl.size.Bounding
import quoi.api.abobaui.constraints.impl.size.Copying
import quoi.api.abobaui.dsl.*
import quoi.api.abobaui.elements.Layout
import quoi.api.abobaui.elements.ElementScope
import quoi.api.abobaui.elements.impl.Text.Companion.string
import quoi.api.abobaui.elements.impl.Text.Companion.textSupplied
import quoi.api.abobaui.elements.impl.TextInput.Companion.maxWidth
import quoi.api.abobaui.elements.impl.TextInput.Companion.onTextChanged
import quoi.api.input.CursorShape
import quoi.utils.ThemeManager.theme
import quoi.utils.ui.cursor
import quoi.api.customtriggers.ui.triggerDropdown
import quoi.utils.ui.elements.slider
import quoi.utils.ui.elements.switch
import quoi.utils.ui.elements.themedInput
import kotlin.reflect.KMutableProperty0

fun ElementScope<*>.textField(label: String, value: String, update: (String) -> Unit) = column(size(w = 360.px.coerceAtMost(Copying)), gap = 4.px) {
    text(string = label, size = 14.px, colour = theme.onSurfaceVariant)
    themedInput(size = size(Copying, 28.px)) {
        textInput(string = value, pos = at(x = 8.px, y = Centre), size = 14.px,
            colour = theme.onSurface, caretColour = theme.primary) {
            maxWidth(Copying - 16.px)
            onTextChanged { update(it.string) }
        }
    }
}

fun ElementScope<*>.toggleField(label: String, value: KMutableProperty0<Boolean>) = column(size(w = Bounding), gap = 4.px) {
    text(string = label, size = 14.px, colour = theme.onSurfaceVariant)
    group(size(40.px, 28.px)) {
        switch(value, size = 18.px, pos = at(x = 0.px, y = Centre))
    }
}

fun <T> ElementScope<*>.choiceField(label: String, value: () -> T, entries: List<T>, display: (T) -> String = { it.toString() }, update: (T) -> Unit) = column(size(w = 200.px.coerceAtMost(Copying)), gap = 4.px) {
    text(string = label, size = 14.px, colour = theme.onSurfaceVariant)
    block(size(Copying, 28.px), colour = theme.surfaceContainerHighest, radius = 5.radius()) {
        textSupplied(supplier = { display(value()) }, size = 14.px, colour = theme.onSurface, pos = at(x = 8.px, y = Centre)).apply {
            quoi.api.abobaui.elements.impl.Text.run { maxWidth(Copying - 36.px) }
        }
        image(image = theme.chevronImage, constraints = constrain(x = 8.px.alignOpposite, y = Centre, w = 12.px, h = 12.px), colour = theme.onSurfaceVariant) {
            rotation(90f)
        }
        cursor(CursorShape.HAND)
        onClick {
            triggerDropdown(entries, displayString = display, onSelect = update)
            true
        }
    }
}

fun ElementScope<*>.numberField(label: String, value: () -> Double, min: Double = -30000000.0, max: Double = 30000000.0, update: (Double) -> Unit) = column(size(w = 104.px.coerceAtMost(Copying)), gap = 4.px) {
    text(string = label, size = 14.px, colour = theme.onSurfaceVariant)
    themedInput(size = size(Copying, 28.px)) {
        textInput(string = value().toString(), pos = at(x = 8.px, y = Centre), size = 14.px,
            colour = theme.onSurface, caretColour = theme.primary) {
            maxWidth(Copying - 16.px)
            onTextChanged { event ->
                event.string.toDoubleOrNull()?.takeIf { it.isFinite() && it in min..max }?.let(update)
            }
            onFocusLost { string = value().toString() }
        }
    }
}

fun ElementScope<*>.optionalNumberField(label: String, value: () -> Float?, update: (Float?) -> Unit) = column(size(w = 104.px.coerceAtMost(Copying)), gap = 4.px) {
    text(string = label, size = 14.px, colour = theme.onSurfaceVariant)
    themedInput(size = size(Copying, 28.px)) {
        textInput(string = value()?.toString() ?: "", pos = at(x = 8.px, y = Centre), size = 14.px,
            colour = theme.onSurface, caretColour = theme.primary) {
            maxWidth(Copying - 16.px)
            onTextChanged { event ->
                if (event.string.isBlank()) update(null)
                else event.string.toFloatOrNull()?.takeIf { it.isFinite() && it >= 0 }?.let(update)
            }
            onFocusLost { string = value()?.toString() ?: "" }
        }
    }
}

/**
 * Lays out [fields] using their relative [weights], subtracting the gaps from the available width.
 */
fun ElementScope<*>.fieldRow(vararg fields: ElementScope<*>.() -> Unit, weights: List<Float> = List(fields.size) { 1f }) = row(size(w = Copying), gap = 12.px) {
    require(weights.size == fields.size && weights.all { it > 0f })
    val total = weights.sum()
    fields.forEachIndexed { index, field ->
        val share = weights[index] / total
        column(size(w = (100f * share).percent - (12f * (fields.size - 1) * share).px)) { field() }
    }
}

/** Controls use their own widths; unused space stays at the right edge. */
fun ElementScope<*>.settingRow(vararg fields: ElementScope<*>.() -> Unit): ElementScope<*> {
    val layout = object : Layout(size(w = Copying), 16.px) {
        override fun prePosition() {
            val spacing = gap!!.calculateSize(this, horizontal = true)
            var nextX = 0f
            var nextY = 0f
            var rowHeight = 0f
            children?.filter { it.enabled }?.forEach { child ->
                if (nextX > 0f && nextX + child.width > width) {
                    nextX = 0f
                    nextY += rowHeight + spacing
                    rowHeight = 0f
                }
                child.internalX = nextX
                child.internalY = nextY
                nextX += child.width + spacing
                rowHeight = maxOf(rowHeight, child.height)
            }
        }
    }
    element.addElement(layout)
    return ElementScope(layout).apply { fields.forEach { it() } }
}

fun ElementScope<*>.intField(label: String, value: () -> Int, min: Int, max: Int, update: (Int) -> Unit) = column(size(w = 104.px.coerceAtMost(Copying)), gap = 4.px) {
    text(string = label, size = 14.px, colour = theme.onSurfaceVariant)
    themedInput(size = size(Copying, 28.px)) {
        textInput(string = value().toString(), pos = at(x = 8.px, y = Centre), size = 14.px,
            colour = theme.onSurface, caretColour = theme.primary) {
            maxWidth(Copying - 16.px)
            onTextChanged { event ->
                if (event.string.isNotEmpty() && event.string.toIntOrNull()?.let { it in min..max } != true) event.cancel()
                else event.string.toIntOrNull()?.let(update)
            }
            onFocusLost { string = value().toString() }
        }
    }
}

fun ElementScope<*>.sliderField(label: String, value: KMutableProperty0<Float>, min: Float, max: Float) = column(size(w = 200.px.coerceAtMost(Copying)), gap = 4.px) {
    textSupplied(supplier = { "$label: ${"%.2f".format(java.util.Locale.ROOT, value.get())}" }, size = 14.px, colour = theme.onSurfaceVariant)
    // The shared slider writes Double values. Bridge explicitly to the stored Float setting.
    val bridge = object {
        var number: Double
            get() = value.get().toDouble()
            set(new) { value.set(new.toFloat()) }
    }
    group(size(Copying, 28.px)) {
        slider(bridge::number, min.toDouble(), max.toDouble(), 0.01, pos = at(x = 6.px, y = Centre), size = size(Copying - 12.px, 12.px))
    }
}

fun ElementScope<*>.intSliderField(label: String, value: KMutableProperty0<Int>, min: Int, max: Int) = column(size(w = 200.px.coerceAtMost(Copying)), gap = 4.px) {
    textSupplied(supplier = { "$label: ${value.get()}" }, size = 14.px, colour = theme.onSurfaceVariant)
    val bridge = object {
        var number: Double
            get() = value.get().toDouble()
            set(new) { value.set(new.toInt().coerceIn(min, max)) }
    }
    group(size(Copying, 28.px)) {
        slider(bridge::number, min.toDouble(), max.toDouble(), 1, pos = at(x = 6.px, y = Centre), size = size(Copying - 12.px, 12.px))
    }
}

fun ElementScope<*>.optionalSliderField(label: String, value: KMutableProperty0<Float?>, max: Float) = column(size(w = 200.px.coerceAtMost(Copying)), gap = 8.px) {
    choiceField(label, { value.get() != null }, listOf(false, true), { if (it) "Exact value" else "Any value" }) {
        value.set(if (it) value.get() ?: 1f else null)
    }
    val bridge = object {
        var number: Float
            get() = value.get() ?: 1f
            set(new) { value.set(new) }
    }
    sliderField(label, bridge::number, 0f, max.coerceAtLeast(value.get() ?: 0f))
}
