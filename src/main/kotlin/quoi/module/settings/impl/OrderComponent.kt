package quoi.module.settings.impl

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import quoi.api.abobaui.constraints.impl.positions.Centre
import quoi.api.abobaui.constraints.impl.size.Copying
import quoi.api.abobaui.dsl.*
import quoi.api.abobaui.elements.ElementScope
import quoi.api.abobaui.elements.impl.Block.Companion.outline
import quoi.api.abobaui.elements.impl.Text
import quoi.api.abobaui.elements.impl.Text.Companion.string
import quoi.api.colour.colour
import quoi.api.input.CursorShape
import quoi.module.settings.Saving
import quoi.module.settings.UIComponent
import quoi.utils.ThemeManager.theme
import quoi.utils.ui.cursor

/**
 * A setting that lets the user rearrange a fixed set of entries.
 *
 * Entries are reordered in the ClickGui by dragging a row to a new position.
 * The order is saved by display name, so new options added later are appended
 * and removed options are dropped when loading an older config.
 *
 * ### Example
 * ```kotlin
 * private val priority by order("Priority", listOf(Type.A, Type.B, Type.C))
 *
 * val first = priority.first()
 * val sorted = things.sortedWith(priority.comparator { it.type })
 * ```
 */
class OrderComponent<T>(
    name: String,
    defaultOrder: List<T>,
    desc: String = "",
    collapsible: Boolean = true,
    private val displayString: ((T) -> String)? = null,
) : UIComponent<OrderComponent<T>>(name, desc), Saving, Iterable<T> {

    init {
        require(defaultOrder.isNotEmpty()) { "order component must have at least one entry" }
        require(defaultOrder.distinctBy(::nameOf).size == defaultOrder.size) { "order component entries must have unique names" }
    }

    override val default: OrderComponent<T> = this

    private val defaults: List<T> = defaultOrder.toList()
    private val entries: MutableList<T> = defaultOrder.toMutableList()
    private val editor = CollapsibleEditor(name, collapsible)

    /**
     * The current order, from first to last.
     */
    val order: List<T> get() = entries

    override var value: OrderComponent<T>
        get() = this
        set(value) {
            if (value === this) return
            setOrder(value.entries)
        }

    val size: Int get() = entries.size

    operator fun get(index: Int): T = entries[index]

    override fun iterator(): Iterator<T> = entries.toList().iterator()

    /**
     * Position of [item] in the current order, or `-1` if it isn't an entry.
     */
    fun indexOf(item: T): Int = entries.indexOf(item)

    /**
     * Comparator that sorts values by the position of their key in the current order.
     * Values whose key isn't an entry are placed last.
     */
    inline fun <V> comparator(crossinline key: (V) -> T): Comparator<V> = compareBy {
        indexOf(key(it)).let { i -> if (i < 0) Int.MAX_VALUE else i }
    }

    /**
     * Moves the entry at [from] to [to], shifting the entries in between.
     */
    fun move(from: Int, to: Int) {
        if (from !in entries.indices) return
        val target = to.coerceIn(entries.indices)
        if (from == target) return
        entries.add(target, entries.removeAt(from))
    }

    /**
     * Applies [order], keeping only known entries and appending missing ones in default order.
     */
    fun setOrder(order: List<T>) {
        val known = order.filter { it in defaults }.distinct()
        entries.clear()
        entries.addAll(known)
        entries.addAll(defaults.filter { it !in known })
    }

    fun nameOf(item: T): String =
        displayString?.invoke(item) ?: if (item is Enum<*>) item.name.replace(Regex("(?<=[a-z])(?=[A-Z])"), " ") else item.toString()

    override fun reset() {
        entries.clear()
        entries.addAll(defaults)
    }

    override fun write(): JsonElement = JsonArray().apply {
        entries.forEach { add(JsonPrimitive(nameOf(it))) }
    }

    override fun read(element: JsonElement) {
        if (!element.isJsonArray) return
        val names = element.asJsonArray.mapNotNull { runCatching { it.asString }.getOrNull() }
        setOrder(names.mapNotNull { name -> defaults.find { nameOf(it).equals(name, ignoreCase = true) } })
    }

    override fun hashCode(): Int = entries.hashCode()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OrderComponent<*>) return false
        return name == other.name && defaults == other.defaults
    }

    override fun ElementScope<*>.draw(asSub: Boolean): ElementScope<*> = editor.draw(this) {
        var dragging = -1
        val rowHeight = if (asSub) 20.px else 22.px
        val labels = mutableListOf<ElementScope<Text>>()

        val list = column(size(w = Copying), gap = 3.px) {
            entries.indices.forEach { slot ->
                block(
                    size(w = Copying, h = rowHeight),
                    colour = colour { if (slot == dragging) theme.primaryContainer.rgb else theme.surfaceContainerHighest.rgb },
                    radius = if (asSub) 4.radius() else 5.radius()
                ) {
                    outline(colour { if (slot == dragging) theme.primary.rgb else theme.outline.rgb }, thickness = 2.px)
                    cursor(CursorShape.VRESIZE)

                    text(
                        string = "${slot + 1}.",
                        size = theme.textSize,
                        colour = colour { if (slot == dragging) theme.onPrimaryContainer.rgb else theme.onSurfaceVariant.rgb },
                        pos = at(x = 6.px, y = Centre)
                    )

                    labels += text(
                        string = nameOf(entries[slot]),
                        size = theme.textSize,
                        colour = colour { if (slot == dragging) theme.onPrimaryContainer.rgb else theme.onSurface.rgb },
                        pos = at(x = 24.px, y = Centre)
                    )

                    image(
                        image = theme.moveImage,
                        constraints = constrain(x = 6.px.alignOpposite, y = Centre, w = 12.px, h = 12.px),
                        colour = theme.onSurfaceVariant
                    )

                    onClick {
                        dragging = slot
                        redraw()
                        true
                    }
                }
            }
        }

        fun refresh() {
            labels.forEachIndexed { i, label -> label.string = nameOf(entries[i]) }
            list.element.redraw()
        }

        list.onMouseMove {
            if (dragging < 0) return@onMouseMove false
            val rows = list.element.children ?: return@onMouseMove false
            val target = rows.indexOfLast { ui.my >= it.y }.coerceAtLeast(0)
            if (target != dragging) {
                move(dragging, target)
                dragging = target
                refresh()
            }
            false
        }

        list.onRelease {
            if (dragging >= 0) {
                dragging = -1
                list.element.redraw()
            }
        }

        list.onValueChanged { refresh() }
    }
}
