package quoi.api.customtriggers.ui

import quoi.api.abobaui.constraints.impl.positions.Centre
import quoi.api.abobaui.constraints.impl.size.Bounding
import quoi.api.abobaui.constraints.impl.size.Copying
import quoi.api.abobaui.constraints.impl.size.Fill
import quoi.api.abobaui.dsl.*
import quoi.api.abobaui.elements.ElementScope
import quoi.api.abobaui.elements.impl.Block.Companion.outline
import quoi.api.abobaui.elements.impl.Popup
import quoi.api.abobaui.elements.impl.RefreshableGroup
import quoi.api.abobaui.elements.impl.Scrollable.Companion.scroll
import quoi.api.abobaui.elements.impl.Text
import quoi.utils.ui.rendering.NVGRenderer
import quoi.api.abobaui.elements.impl.Text.Companion.textSupplied
import quoi.api.abobaui.elements.impl.Text.Companion.maxWidth
import quoi.api.abobaui.elements.impl.popup
import quoi.api.abobaui.elements.impl.refreshableGroup
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.customtriggers.*
import quoi.api.customtriggers.TriggerManager.triggers
import quoi.api.input.CursorShape
import quoi.config.TypeNamed
import quoi.config.typeName
import quoi.QuoiMod.mc
import quoi.utils.ui.elements.switch
import quoi.utils.ui.elements.themedInput
import quoi.api.abobaui.constraints.Constraint
import quoi.api.abobaui.elements.Element
import quoi.api.abobaui.elements.impl.Scrollable
import quoi.utils.ThemeManager.theme
import quoi.utils.ui.cursor

class CustomTriggerEditor {
    private var selectedGroup = "General"
    private val collapsedGroups = mutableSetOf<String>()
    private var groupScroll: Scrollable? = null
    private var selectedId: String? = null
    private var search = ""
    private lateinit var searchInput: quoi.api.abobaui.elements.impl.TextInput
    private var status = ""
    private lateinit var groupArea: RefreshableGroup
    private lateinit var mainArea: RefreshableGroup
    private var componentPopup: Popup? = null
    private var closingEditor = false
    private var triggerScroll: Scrollable? = null
    private var renderedTrigger: String? = null

    private fun save() {
        TriggerManager.reset()
        triggers.save()
    }

    private fun refresh() {
        groupArea.refresh()
        mainArea.refresh()
    }

    fun build() = aboba("Custom Triggers") {
        closingEditor = false
        TriggerManager.editing = true
        if (selectedGroup !in triggers) selectedGroup = triggers.keys.firstOrNull() ?: "General"
        selectedId = triggers[selectedGroup]?.firstOrNull()?.id
        onAdd {
            // UIScreen.onClose is not called when Minecraft replaces a screen directly.
            mc.screen?.let { screen ->
                net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.remove(screen).register {
                    if (!closingEditor) ui.close()
                }
            }
        }
        onRemove {
            closingEditor = true
            ui.unfocus()
            componentPopup = null
            save()
            TriggerManager.editing = false
        }

        onKeyPressed { (key, _) ->
            if (key == quoi.api.input.Keybinds.KEY_ESCAPE && componentPopup != null) {
                componentPopup?.closePopup()
                componentPopup = null
                true
            } else false
        }
        column(constrain(x = Centre, y = Centre, w = 94.percent.coerceAtMost(1500.px), h = 90.percent.coerceAtMost(900.px)), gap = 10.px) {
            block(size(Copying, 62.px), colour = theme.background, radius = 6.radius()) {
                text(string = "Custom Triggers", colour = theme.onSurface, size = 26.px, pos = at(x = 18.px, y = Centre))
                group(constrain(x = 14.px.alignOpposite, y = Centre, w = 20.percent, h = 36.px)) {
                    themedInput(size = size(Copying, Copying)) {
                        textInput(string = search, placeholder = "Search triggers…", pos = at(x = 12.px, y = Centre), size = 16.px,
                            colour = theme.onSurface, caretColour = theme.primary) {
                            searchInput = element
                            quoi.api.abobaui.elements.impl.TextInput.run {
                                maxWidth(Copying - 24.px)
                                onTextChanged {
                                    search = it.string
                                    groupArea.refresh()
                                }
                            }
                        }
                    }
                }
            }
            row(size(Copying, Fill), gap = 10.px) {
                block(size(26.percent.coerceAtMost(340.px), Copying), colour = theme.background, radius = 6.radius()) {
                    column(constrain(x = 12.px, y = 12.px, w = Copying - 24.px, h = Copying - 24.px), gap = 10.px) {
                        group(size(Copying, 28.px)) {
                            text(string = "My Triggers", colour = theme.primary, size = 20.px, pos = at(x = 0.px, y = Centre))
                        }
                        groupArea = refreshableGroup(size(Copying, Copying - 84.px)) {
                            val offset = groupScroll?.scrollOffset ?: 0f
                            val list = scrollable(size(Copying, Copying)) {
                                column(constrain(x = 2.px, w = Copying - 4.px), gap = 8.px) {
                                    triggers.forEach { (groupName, children) ->
                                        val matches = children.filter { search.isBlank() || groupName.contains(search, true) || it.name.contains(search, true) }
                                        if (search.isNotBlank() && matches.isEmpty() && !groupName.contains(search, true)) return@forEach
                                        val expanded = groupName !in collapsedGroups || search.isNotBlank()
                                        column(size(w = Copying), gap = 4.px) {
                                            block(size(Copying, 40.px), colour = theme.surfaceContainerLow, radius = 4.radius()) {
                                                image(image = theme.chevronImage, constraints = constrain(x = 10.px, y = Centre, w = 14.px, h = 14.px), colour = theme.onSurfaceVariant) {
                                                    rotation(if (expanded) 90f else 0f)
                                                }
                                                summaryText({ groupName }, Copying - 108.px, fontSize = 16f, x = 32f)
                                                row(constrain(x = 4.px.alignOpposite, y = Centre, h = 28.px), gap = 4.px) {
                                                    orderButton("…", true) {
                                                        triggerDropdown(listOf("Rename group", "Delete group")) { action ->
                                                            ui.unfocus()
                                                            if (action == "Rename group") renameGroupDialog(groupName)
                                                            else confirm("Delete group", "Delete '$groupName' and all ${children.size + TriggerManager.unsupportedCount(groupName)} triggers?") {
                                                                TriggerManager.deleteGroup(groupName)
                                                                collapsedGroups.remove(groupName)
                                                                if (selectedGroup == groupName) {
                                                                    selectedGroup = triggers.keys.firstOrNull() ?: "General"
                                                                    selectedId = triggers[selectedGroup]?.firstOrNull()?.id
                                                                }
                                                                refresh()
                                                            }
                                                        }
                                                    }
                                                    orderButton("+", true) {
                                                        ui.unfocus()
                                                        val trigger = TriggerRule("New trigger").apply { enabled = false }
                                                        TriggerManager.addTrigger(groupName, trigger)
                                                        selectedGroup = groupName
                                                        selectedId = trigger.id
                                                        collapsedGroups.remove(groupName)
                                                        searchInput.text = ""
                                                        status = ""
                                                        refresh()
                                                    }
                                                }
                                                cursor(CursorShape.HAND)
                                                onClick {
                                                    ui.unfocus()
                                                    if (!collapsedGroups.add(groupName)) collapsedGroups.remove(groupName)
                                                    groupArea.refresh()
                                                    true
                                                }
                                            }
                                            if (expanded) column(constrain(x = 16.px, w = Copying - 16.px), gap = 4.px) {
                                                if (matches.isEmpty()) text(string = "No triggers yet", colour = theme.onSurfaceVariant, size = 14.px, pos = at(x = 12.px))
                                                matches.forEach { trigger ->
                                                    block(size(Copying, 44.px), colour = if (trigger.id == selectedId) theme.primaryContainer else theme.background, radius = 4.radius()) {
                                                        if (trigger.id == selectedId) outline(theme.primary, 1.px)
                                                        summaryText({ trigger.name }, Copying - 66.px, fontSize = 16f, x = 12f)
                                                        switch(trigger::enabled, size = 20.px, pos = at(x = 10.px.alignOpposite, y = Centre), onToggle = { TriggerManager.reset() })
                                                        cursor(CursorShape.HAND)
                                                        onClick {
                                                            ui.unfocus()
                                                            selectedGroup = groupName
                                                            selectedId = trigger.id
                                                            status = ""
                                                            refresh()
                                                            true
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                }
                            }
                            groupScroll = list.element
                            if (offset > 0f) list.scroll(offset, duration = 0f)
                            onScroll { (amount) -> list.scroll(amount * -52f) }
                        }
                        editorButton("+ New Group", accent = true) {
                            ui.unfocus()
                            val name = TriggerManager.addGroup()
                            collapsedGroups.remove(name)
                            searchInput.text = ""
                            groupArea.refresh()
                        }
                    }
                }
                block(size(Fill, Copying), colour = theme.background, radius = 6.radius()) {
                    mainArea = refreshableGroup(constrain(x = 14.px, y = 14.px, w = Copying - 28.px, h = Copying - 28.px)) {
                        val trigger = triggers[selectedGroup]?.firstOrNull { it.id == selectedId }
                        if (trigger == null) {
                            text(string = "Select a trigger or create a new one", colour = theme.onSurfaceVariant, size = 18.px)
                            return@refreshableGroup
                        }
                        drawTrigger(trigger)
                    }
                }
            }
        }
    }

    private fun ElementScope<*>.renameGroupDialog(groupName: String) {
        var name = groupName
        var error = ""
        showDialog("Rename group") {
            column(size(w = Copying), gap = 0.px) {
                textField("Group name", name) { name = it }
                group(size(Copying, object : Constraint.Size {
                    override fun calculateSize(element: Element, horizontal: Boolean) = if (error.isEmpty()) 0f else 20f
                })) {
                    summaryText({ error }, Copying, fontSize = 13f, colour = theme.error, x = 0f)
                }
            }
            fieldRow(
                { editorButton("Cancel") { componentPopup?.closePopup() } },
                { editorButton("Save", accent = true) {
                    ui.unfocus()
                    val renamed = name.trim()
                    if (renamed == groupName) componentPopup?.closePopup()
                    else if (TriggerManager.renameGroup(groupName, renamed)) {
                        if (selectedGroup == groupName) selectedGroup = renamed
                        if (collapsedGroups.remove(groupName)) collapsedGroups.add(renamed)
                        componentPopup?.closePopup()
                    } else error = "Use a unique name with 1–40 characters."
                } }
            )
        }
    }

    private fun ElementScope<*>.drawTrigger(trigger: TriggerRule) {
        val offset = if (renderedTrigger == trigger.id) triggerScroll?.scrollOffset ?: 0f else 0f
        renderedTrigger = trigger.id
        group(size(Copying, Copying)) {
            group(constrain(y = 0.px, w = Copying, h = 42.px)) {
                textInput(string = trigger.name, colour = theme.onSurface, caretColour = theme.primary,
                    size = 24.px, pos = at(x = 4.px, y = Centre)) {
                    quoi.api.abobaui.elements.impl.TextInput.run {
                        maxWidth(Copying - 150.px)
                        onTextChanged { if (it.string.isNotBlank()) trigger.name = it.string.take(60) }
                    }
                    onFocusLost {
                        element.text = trigger.name
                        groupArea.refresh()
                    }
                }
            }
            val list = scrollable(constrain(y = 54.px, w = Copying, h = Copying - 124.px)) {
                column(constrain(x = 2.px, w = Copying - 4.px), gap = 10.px) {
                    section("1. Trigger") {
                        column(size(w = 360.px.coerceAtMost(Copying))) {
                            choiceField("Event", { trigger.trigger.typeName }, TriggerManager.triggerEntries.map { it.first }, { humanName(it) }) { selected ->
                                ui.unfocus()
                                if (selected != trigger.trigger.typeName) {
                                    trigger.trigger = TriggerManager.triggerEntries.first { it.first == selected }.second()
                                    TriggerManager.reset()
                                    mainArea.refresh()
                                }
                            }
                        }
                        with(trigger.trigger) { draw() }
                    }
                    section("2. Conditions (optional)") {
                        trigger.conditions.toList().forEachIndexed { index, condition ->
                            componentRow(condition, {
                                trigger.conditions.removeAt(index)
                                mainArea.refresh()
                            })
                        }
                        editorButton("+ Add Condition", compact = true, accent = true) {
                            val entries = TriggerManager.conditionEntries
                            triggerDropdown(entries, displayString = { humanName(it.first) }) { (_, factory) ->
                                ui.unfocus()
                                trigger.conditions.add(factory())
                                mainArea.refresh()
                            }
                        }
                    }
                    section("3. Actions") {
                        trigger.actions.toList().forEachIndexed { index, action ->
                            componentRow(action, {
                                trigger.actions.removeAt(index)
                                mainArea.refresh()
                            }, index, trigger)
                        }
                        editorButton("+ Add Action", compact = true, accent = true) {
                            triggerDropdown(TriggerManager.actionEntries.filter { it.second().supports(trigger.trigger) }, displayString = { humanName(it.first) }) { (_, factory) ->
                                ui.unfocus()
                                trigger.actions.add(factory())
                                mainArea.refresh()
                            }
                        }
                    }
                }
            }
            triggerScroll = list.element
            if (offset > 0) list.scroll(offset, duration = 0f)
            onScroll { (amount) -> list.scroll(amount * -60f) }
            group(constrain(y = 48.px.alignOpposite, w = Copying, h = 18.px)) {
                summaryText({ trigger.validationError() ?: status }, Copying, fontSize = 13f, colour = theme.onSurfaceVariant, x = 0f)
            }
            group(constrain(y = 0.px.alignOpposite, w = Copying, h = 36.px)) {
                row(at(x = 0.px.alignOpposite, y = Centre), gap = 8.px) {
                    editorButton("Test Actions", compact = true, accent = true) {
                        ui.unfocus()
                        status = TriggerManager.test(trigger) ?: "Testing actions without event captures."
                    }
                    editorButton("Delete", compact = true, danger = true) {
                        confirm("Delete trigger", "Delete '${trigger.name}'?") {
                            triggers[selectedGroup]?.remove(trigger)
                            selectedId = triggers[selectedGroup]?.firstOrNull()?.id
                            save()
                            refresh()
                        }
                    }
                }
            }
        }
    }

    private fun ElementScope<*>.section(title: String, content: ElementScope<*>.() -> Unit) = block(
        size(Copying, Bounding + 10.px), colour = theme.surfaceContainerLow, radius = 5.radius()
    ) {
        outline(theme.outlineVariant, 1.px)
        column(constrain(x = 10.px, y = 10.px, w = Copying - 20.px), gap = 8.px) {
            text(string = title, colour = theme.primary, size = 18.px, pos = at(x = 0.px))
            content()
        }
    }

    private fun ElementScope<*>.componentRow(
        component: Abobable, remove: () -> Unit, index: Int = 0, trigger: TriggerRule? = null
    ) = block(size(Copying, Bounding + 8.px), colour = theme.background, radius = 4.radius()) {
        column(constrain(x = 8.px, y = 8.px, w = Copying - 16.px), gap = 4.px) {
            group(size(Copying, 28.px)) {
                text(string = humanName((component as TypeNamed).typeName), colour = theme.onSurface, size = 14.px, pos = at(x = 0.px, y = Centre)) { maxWidth(Copying - if (trigger != null) 100.px else 40.px) }
                row(at(x = 0.px.alignOpposite, y = Centre), gap = 4.px) {
                    if (trigger != null) {
                        orderButton("↑", index > 0) {
                            ui.unfocus()
                            trigger.actions.add(index - 1, trigger.actions.removeAt(index))
                            mainArea.refresh()
                        }
                        orderButton("↓", index < trigger.actions.lastIndex) {
                            ui.unfocus()
                            trigger.actions.add(index + 1, trigger.actions.removeAt(index))
                            mainArea.refresh()
                        }
                    }
                    orderButton("×", true) {
                        ui.unfocus()
                        remove()
                    }
                }
            }
            with(component) { draw() }

        }
    }

    private fun ElementScope<*>.showDialog(title: String, content: ElementScope<*>.() -> Unit) {
        componentPopup?.closePopup()
        ui.unfocus()
        componentPopup = popup(copies(), smooth = false) {
            val popup = this
            onRemove {
                componentPopup = null
                ui.unfocus()
                if (!closingEditor) refresh()
            }
            block(copies(), colour = Colour.BLACK.withAlpha(0.6f)) {
                onClick {
                    popup.closePopup()
                    true
                }
            }
            block(size(480.px.coerceAtMost(90.percent), Bounding + 20.px), colour = theme.surfaceContainer, radius = 8.radius()) {
                outline(theme.outlineVariant, 1.px)
                onClick { true }
                column(constrain(x = 20.px, y = 20.px, w = Copying - 40.px), gap = 12.px) {
                    text(string = title, colour = theme.onSurface, size = 20.px, pos = at(x = 0.px))
                    content()
                }
            }
        }
    }

    private fun ElementScope<*>.confirm(title: String, message: String, action: () -> Unit) {
        showDialog(title) {
            column(size(w = Copying), gap = 6.px) {
                group(size(Copying, 20.px)) {
                    summaryText({ message }, Copying, fontSize = 14f, colour = theme.onSurfaceVariant, x = 0f)
                }
                text(string = "This cannot be undone.", colour = theme.onSurfaceVariant, size = 13.px, pos = at(x = 0.px))
            }
            fieldRow(
                { editorButton("Cancel") { componentPopup?.closePopup() } },
                {
                    editorButton("Delete", danger = true) {
                        action()
                        componentPopup?.closePopup()
                    }
                }
            )
        }
    }

    private fun ElementScope<*>.editorButton(label: String, compact: Boolean = false, accent: Boolean = false, danger: Boolean = false, action: ElementScope<*>.() -> Unit) = block(
        size(if (compact) Bounding + 24.px else Copying, 36.px), colour = theme.background, radius = 4.radius()
    ) {
        outline(if (danger) theme.error else if (accent) theme.primary else theme.outline, 1.px)
        text(string = label, colour = if (danger) theme.error else if (accent) theme.primary else theme.onSurface, size = 14.px)
        cursor(CursorShape.HAND)
        tonalHover()
        onClick {
            action()
            true
        }
    }

    private fun ElementScope<*>.orderButton(label: String, available: Boolean, action: () -> Unit) = block(
        size(28.px, 28.px), colour = theme.surfaceContainerHigh, radius = 4.radius()
    ) {
        element.constraints.y = Centre
        if (label == "↑" || label == "↓") image(image = theme.chevronImage, constraints = size(12.px, 12.px),
            colour = if (available) theme.onSurface else theme.outlineVariant) {
            rotation(if (label == "↑") -90f else 90f)
        } else if (label == "×") image(image = theme.closeImage, constraints = size(12.px, 12.px),
            colour = if (available) theme.onSurface else theme.outlineVariant)
        else text(string = label, colour = if (available) theme.onSurface else theme.outlineVariant, size = 14.px)
        if (available) {
            cursor(CursorShape.HAND)
            tonalHover()
            onClick {
                action()
                true
            }
        } else onClick { true }
    }

    // Truncate long labels to fit without shrinking the font.
    private fun ElementScope<*>.summaryText(
        value: () -> String, limit: Constraint.Size, fontSize: Float = 14f,
        colour: Colour = theme.onSurface, x: Float? = null
    ) = object : Text(value(), NVGRenderer.defaultFont, colour, at(x = x?.px ?: quoi.api.abobaui.constraints.impl.measurements.Undefined, y = Centre), fontSize.px) {
        override fun prePosition() {
            val full = value()
            val available = limit.calculateSize(this, true).coerceAtLeast(0f)
            text = if (textWidth(full) <= available) full else {
                var low = 0
                var high = full.length
                while (low < high) {
                    val middle = (low + high + 1) / 2
                    if (textWidth(full.take(middle) + "…") <= available) low = middle else high = middle - 1
                }
                if (low == 0) "" else full.take(low) + "…"
            }
            super.prePosition()
        }
    }.scope {}

    private fun humanName(name: String) = name.split('_').joinToString(" ") { it.replaceFirstChar(Char::titlecase) }
}
