package quoi.module.settings.impl

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import quoi.api.abobaui.constraints.impl.positions.Centre
import quoi.api.abobaui.constraints.impl.size.Bounding
import quoi.api.abobaui.constraints.impl.size.Copying
import quoi.api.abobaui.dsl.*
import quoi.api.abobaui.elements.ElementScope
import quoi.api.abobaui.elements.impl.Block.Companion.outline
import quoi.api.abobaui.elements.impl.Popup
import quoi.api.abobaui.elements.impl.Text.Companion.string
import quoi.api.animations.Animation
import quoi.api.colour.Colour
import quoi.api.input.CursorShape
import quoi.module.settings.Saving
import quoi.module.settings.UIComponent
import quoi.utils.ThemeManager.theme
import quoi.utils.ui.cursor
import quoi.utils.ui.elements.selector
import quoi.utils.ui.popupX
import quoi.utils.ui.popupY

class MultiSelectComponent<T>(
    name: String,
    defaultSelected: Set<T>,
    val options: List<T>,
    desc: String = "",
) : UIComponent<MultiSelectComponent<T>>(name, desc), Saving {

    override val default: MultiSelectComponent<T> = this

    private val defaultNames = defaultSelected.map(::nameOf).toSet()

    private val selected = options
        .filter { nameOf(it) in defaultNames }
        .toMutableSet()

    override var value: MultiSelectComponent<T>
        get() = this
        set(value) {
            selected.clear()
            selected.addAll(value.selected)
        }

    val selectedValues: Set<T>
        get() = selected.toSet()

    operator fun contains(value: T): Boolean = selected.contains(value)

    fun any(predicate: (T) -> Boolean): Boolean = selected.any(predicate)

    private fun nameOf(item: T) =
        if (item is Enum<*>) item.name.replace(Regex("(?<=[a-z])(?=[A-Z])"), " ") else item.toString()

    private val selectedName: String
        get() = when (selected.size) {
            0 -> "None"
            1 -> nameOf(selected.first())
            else -> "${selected.size} selected"
        }

    override fun write(): JsonElement = JsonArray().apply {
        selected.forEach { add(JsonPrimitive(nameOf(it))) }
    }

    override fun read(element: JsonElement) {
        if (!element.isJsonArray) return
        val names = element.asJsonArray.mapNotNull { it.asString }.toSet()
        selected.clear()
        selected.addAll(options.filter { nameOf(it) in names })
    }

    override fun reset() {
        selected.clear()
        selected.addAll(options.filter { nameOf(it) in defaultNames })
    }

    override fun hashCode(): Int = selected.hashCode()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MultiSelectComponent<*>) return false
        return name == other.name && options == other.options
    }

    var popup: Popup? = null

    override fun ElementScope<*>.draw(asSub: Boolean): ElementScope<*> = group(size(w = Copying)) {
        text(
            string = name,
            size = theme.textSize,
            colour = theme.onSurfaceVariant,
            pos = at(x = 0.px, y = Centre)
        )

        val outlineCol = Colour.Animated(
            from = theme.outline,
            to = theme.primary
        )

        block(
            constrain(x = 0.px.alignOpposite, w = Bounding + 5.px, h = if (asSub) Bounding else 20.px),
            colour = theme.surfaceContainerHighest,
            if (asSub) 4.radius() else 5.radius()
        ) {
            outline(outlineCol, thickness = 2.px)
            cursor(CursorShape.HAND)

            val label = text(
                string = selectedName,
                size = theme.textSize,
                colour = theme.onSurface
            ) {
                onValueChanged {
                    string = selectedName
                }
            }
            onMouseEnterExit {
                outlineCol.animate(0.25.seconds, Animation.Style.Linear)
            }

            onClick {
                popup?.closePopup()
                val (x, y) = popupX(gap = -130f) to popupY(gap = 5f, corner = true)
                popup = selector(
                    entries = options,
                    selectedIndices = { options.indices.filter { options[it] in selected }.toSet() },
                    displayString = { nameOf(it) },
                    pos = at(x, y),
                    closeOnSelect = false
                ) { option ->
                    if (option in selected) selected.remove(option) else selected.add(option)
                    label.string = selectedName
                }
                true
            }

            onClick(button = 1) {
                selected.clear()
                label.string = selectedName
                true
            }
        }
    }
}
