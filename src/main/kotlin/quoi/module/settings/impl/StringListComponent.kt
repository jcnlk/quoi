package quoi.module.settings.impl

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import quoi.api.abobaui.constraints.Constraints
import quoi.api.abobaui.constraints.impl.positions.Centre
import quoi.api.abobaui.constraints.impl.size.Bounding
import quoi.api.abobaui.constraints.impl.size.Copying
import quoi.api.abobaui.dsl.*
import quoi.api.abobaui.elements.Element
import quoi.api.abobaui.elements.ElementScope
import quoi.api.abobaui.elements.impl.Scrollable
import quoi.api.abobaui.elements.impl.Scrollable.Companion.scroll
import quoi.api.abobaui.elements.impl.Text.Companion.string
import quoi.api.abobaui.elements.impl.Text.Companion.maxWidth
import quoi.api.abobaui.elements.impl.TextInput.Companion.maxWidth as inputMaxWidth
import quoi.api.abobaui.elements.impl.TextInput
import quoi.api.animations.Animation
import quoi.api.input.CursorShape
import quoi.module.impl.render.clickgui.ClickGui.description
import quoi.module.settings.Saving
import quoi.module.settings.UIComponent
import quoi.utils.ThemeManager.theme
import quoi.utils.ui.cursor
import quoi.utils.ui.elements.suggestionInput
import quoi.utils.ui.elements.themedInput
import quoi.utils.ui.rendering.UIRenderer

/**
 * An editable list of strings.
 *
 * Entries are added through the input below the list (Enter or the plus button)
 * and removed with the cross next to each entry. Blank entries and duplicates (case-insensitive) are ignored.
 *
 * If [defaults] are given, the header offers a button that adds every default that is missing.
 *
 * The backing collection is kept, so it can be passed to other code like a [ListSetting].
 *
 * @param defaults Recommended entries the user can add with one click. They aren't added automatically.
 * @param maxLength Maximum length of a single entry, `0` for no limit.
 * @param visibleRows Number of rows shown before the list starts scrolling.
 */
class StringListComponent<T : MutableCollection<String>>(
    name: String,
    override val default: T,
    desc: String = "",
    val placeholder: String = "Add entry...",
    val maxLength: Int = 0,
    val visibleRows: Int = 6,
    val defaults: Collection<String> = emptyList(),
) : UIComponent<T>(name, desc), Saving {

    private val initialContents = default.toList()

    override var value: T = default

    private var suggestions: () -> List<String> = { emptyList() }

    private var normaliser: (String) -> String = { it }

    /**
     * Transforms entries before they are added, e.g. `{ it.uppercase() }` for item ids.
     */
    fun normalises(block: (String) -> String) = apply {
        normaliser = block
    }

    fun suggests(supplier: () -> Iterable<Any?>) = apply {
        suggestions = { supplier().mapNotNull { it?.toString() } }
    }

    private fun normalise(entry: String) =
        normaliser(entry.trim()).trim().let { if (maxLength > 0) it.take(maxLength) else it }

    private fun contains(entry: String) = value.any { it.equals(entry, ignoreCase = true) }

    /**
     * Adds [entry] if it isn't blank or already present. Returns `true` if it was added.
     */
    fun add(entry: String): Boolean {
        val normalised = normalise(entry)
        if (normalised.isEmpty() || contains(normalised)) return false
        return value.add(normalised)
    }

    fun remove(entry: String): Boolean = value.remove(entry)

    /**
     * Defaults that aren't in the list yet.
     */
    val missingDefaults: List<String>
        get() = defaults.map(::normalise).filter { it.isNotEmpty() && !contains(it) }.distinctBy { it.lowercase() }

    /**
     * Adds every missing default. Returns the number of added entries.
     */
    fun addDefaults(): Int = defaults.count(::add)

    override fun reset() {
        value.clear()
        value.addAll(initialContents)
    }

    override fun write(): JsonElement = JsonArray().apply {
        value.forEach { add(JsonPrimitive(it)) }
    }

    override fun read(element: JsonElement) {
        if (!element.isJsonArray) return
        value.clear()
        element.asJsonArray.forEach { entry ->
            runCatching { entry.asString }.getOrNull()?.let(value::add)
        }
    }

    override fun ElementScope<*>.draw(asSub: Boolean): ElementScope<*> = column(size(w = Copying), gap = 4.px) {
        val rowHeight = if (asSub) 20f else 22f
        val inputHeight = if (asSub) 22f else 25f
        val gap = 3f
        val scrollbarSpace = 10f

        val header = group(size(w = Copying, h = 18.px))

        val listArea = group(size(w = Copying))
        val maxHeight = (rowHeight * visibleRows + gap * (visibleRows - 1)).px
        val scrollable = listArea.scrollable(constrain(x = 0.px, y = 0.px, w = Copying, h = Bounding.coerceAtMost(maxHeight))) {}
        val list = scrollable.column(size(w = Copying), gap = gap.px) {}
        val scrollBar = ScrollBar(constrain(x = 0.px.alignOpposite, y = 0.px, w = 4.px, h = 1.px), scrollable.element, list.element)
        listArea.apply { scrollBar.add() }

        fun rebuildHeader() {
            header.element.removeAll()
            header.apply {
                text(
                    string = if (value.isEmpty()) name else "$name (${value.size})",
                    size = theme.textSize,
                    colour = theme.onSurfaceVariant,
                    pos = at(x = 0.px, y = Centre)
                )

                val missing = missingDefaults.size
                if (missing == 0) return@apply

                block(
                    constrain(x = 0.px.alignOpposite, y = Centre, w = Bounding + 5.px, h = 18.px),
                    colour = theme.surfaceContainerHighest,
                    radius = 4.radius()
                ) {
                    tonalHover()
                    cursor(CursorShape.HAND)

                    text(
                        string = if (missing == defaults.size) "+ Defaults" else "+ $missing defaults",
                        size = 14.px,
                        colour = theme.primary,
                        pos = at(x = 5.px, y = Centre)
                    )

                    onClick {
                        addDefaults()
                        true
                    }
                }.description("Adds the recommended entries that aren't in the list yet.")
            }
            header.element.redraw()
        }

        fun rebuild() {
            val overflowing = value.size > visibleRows
            scrollable.element.constraints.width = if (overflowing) Copying - scrollbarSpace.px else Copying

            list.element.removeAll()
            list.apply {
                if (value.isEmpty()) {
                    group(size(w = Copying, h = rowHeight.px)) {
                        text(
                            string = "No entries yet",
                            size = theme.textSize,
                            colour = theme.outline,
                            pos = at(x = 6.px, y = Centre)
                        )
                    }
                }

                value.toList().forEach { entry ->
                    block(
                        size(w = Copying, h = rowHeight.px),
                        colour = theme.surfaceContainerHighest,
                        radius = if (asSub) 4.radius() else 5.radius()
                    ) {
                        text(
                            string = entry,
                            size = theme.textSize,
                            colour = theme.onSurface,
                            pos = at(x = 6.px, y = Centre)
                        ) {
                            maxWidth(Copying - 30.px)
                        }

                        block(
                            constrain(x = 3.px.alignOpposite, y = Centre, w = (rowHeight - 6f).px, h = (rowHeight - 6f).px),
                            colour = theme.surfaceContainerHighest,
                            radius = 3.radius()
                        ) {
                            tonalHover()
                            cursor(CursorShape.HAND)

                            image(
                                image = theme.closeImage,
                                constraints = size(w = 10.px, h = 10.px),
                                colour = theme.onSurfaceVariant
                            )

                            onClick {
                                remove(entry)
                                true
                            }
                        }
                    }
                }
            }
            rebuildHeader()
            listArea.element.redraw()
        }

        rebuild()

        scrollable.onScroll { (amount) ->
            if (list.element.height <= scrollable.element.height) return@onScroll false
            scrollable.scroll(amount * -(rowHeight + gap) * 2f)
            true
        }

        list.onValueChanged { rebuild() }

        lateinit var input: ElementScope<TextInput>

        fun submit() {
            if (add(input.string)) scrollBar.scrollToEnd()
            input.string = ""
        }

        row(size(w = Copying), gap = 4.px) {
            input = suggestionInput(suggestions = suggestions) {
                themedInput(size = size(w = Copying - (inputHeight + 4f).px, h = inputHeight.px)) {
                    textInput(
                        placeholder = placeholder,
                        size = theme.textSize,
                        colour = theme.onSurface,
                        caretColour = theme.primary,
                        placeHolderColour = theme.onSurfaceVariant,
                        pos = at(x = 3.percent),
                    ) {
                        inputMaxWidth(94.percent)
                        onFocusLost { submit() }
                    }
                }
            }

            block(
                size(w = inputHeight.px, h = inputHeight.px),
                colour = theme.primary,
                radius = 5.radius()
            ) {
                tonalHover()
                cursor(CursorShape.HAND)

                text(
                    string = "+",
                    size = 18.px,
                    colour = theme.onPrimary,
                    pos = at(x = Centre, y = Centre)
                )

                onClick {
                    submit()
                    true
                }
            }.description("Adds the typed entry. Pressing Enter in the field works too.")
        }
    }

    /**
     * Thin scrollbar next to the list. Only drawn while the list is taller than its visible area.
     */
    private class ScrollBar(
        constraints: Constraints,
        private val scrollable: Scrollable,
        private val content: Element,
    ) : Element(constraints) {

        // frames to wait until the rebuilt list has been laid out
        private var pendingScroll = -1

        /**
         * Scrolls to the bottom once the list has been laid out, so a newly added entry is visible.
         */
        fun scrollToEnd() {
            pendingScroll = 2
        }

        override fun drawCustom() {
            val viewHeight = scrollable.height
            val contentHeight = content.height

            if (pendingScroll >= 0 && pendingScroll-- == 0) {
                val target = (contentHeight - viewHeight).coerceAtLeast(0f)
                if (target != scrollable.scrollOffset) {
                    scrollable.scroll(target - scrollable.scrollOffset, 0.15.seconds, Animation.Style.EaseOutQuint)
                    scrollable.redraw()
                }
            }

            if (contentHeight <= viewHeight + 0.5f || viewHeight <= 0f) return

            val top = scrollable.y
            UIRenderer.rect(x, top, width, viewHeight, theme.outline.rgb, width / 2f)

            val thumbHeight = (viewHeight * viewHeight / contentHeight).coerceAtLeast(12f)
            val progress = (scrollable.scrollOffset / (contentHeight - viewHeight)).coerceIn(0f, 1f)
            UIRenderer.rect(x, top + (viewHeight - thumbHeight) * progress, width, thumbHeight, theme.primary.rgb, width / 2f)
        }
    }
}
