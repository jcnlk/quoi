package quoi.api.customtriggers.ui

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import quoi.QuoiMod.mc
import quoi.api.abobaui.constraints.impl.positions.Centre
import quoi.api.abobaui.constraints.impl.size.Bounding
import quoi.api.abobaui.constraints.impl.size.Copying
import quoi.api.abobaui.constraints.impl.size.Fill
import quoi.api.abobaui.dsl.*
import quoi.api.abobaui.elements.ElementScope
import quoi.api.abobaui.elements.impl.Block.Companion.outline
import quoi.api.abobaui.elements.impl.Popup
import quoi.api.abobaui.elements.impl.RefreshableGroup
import quoi.api.abobaui.elements.impl.Scrollable
import quoi.api.abobaui.elements.impl.Scrollable.Companion.scroll
import quoi.api.abobaui.elements.impl.TextInput
import quoi.api.abobaui.elements.impl.TextInput.Companion.maxWidth
import quoi.api.abobaui.elements.impl.TextInput.Companion.onTextChanged
import quoi.api.abobaui.elements.impl.popup
import quoi.api.abobaui.elements.impl.refreshableGroup
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.customtriggers.Abobable
import quoi.api.customtriggers.TriggerManager
import quoi.api.customtriggers.TriggerManager.triggers
import quoi.api.customtriggers.TriggerRule
import quoi.api.customtriggers.conditions.TriggerCondition
import quoi.api.abobaui.elements.Element
import quoi.api.input.CursorShape
import quoi.api.input.Keybinds
import quoi.config.Config
import quoi.config.ConfigSystem
import quoi.config.TypeNamed
import quoi.config.typeName
import quoi.module.impl.misc.CustomTriggers
import quoi.utils.ThemeManager.theme
import quoi.utils.ui.cursor
import quoi.utils.ui.elements.switch
import java.util.UUID

class TriggerEditor : NodeCanvas.Host {
    private var selectedGroup = triggers.keys.firstOrNull() ?: "General"
    private var selectedId: String? = null
    override var selected: Int? = null
        private set(value) {
            field = value
            if (value != null) overviewOpen = false
            syncPanels()
        }
    private var search = ""
    private lateinit var searchInput: TextInput
    private val collapsed = mutableSetOf<String>()
    private var closing = false
    private var savedAt = 0L
    private var checkedAt = 0L
    private var savedSnapshot: Pair<String, String>? = null
    private var headerWidth = 0f

    // user preferences for docked panels
    private var sidebarHidden = false
    private var inspectorHidden = false

    // floating panels in windows too narrow to dock them
    private var sidebarOpen = false
        set(value) {
            field = value
            syncPanels()
        }
    private var overviewOpen = false
        set(value) {
            field = value
            syncPanels()
        }
    private var panelKey: Any? = null
    private var shell: Element? = null
    private lateinit var sidebarElement: Element
    private lateinit var scrimElement: Element
    private lateinit var inspectorElement: Element
    private lateinit var drawerBar: Element
    private lateinit var banner: Element

    private lateinit var sidebar: RefreshableGroup
    private lateinit var header: RefreshableGroup
    private lateinit var inspector: RefreshableGroup
    private lateinit var canvas: NodeCanvas
    private lateinit var root: ElementScope<*>
    private var listScroll: Scrollable? = null
    private var overlay: Popup? = null

    override val rule: TriggerRule? get() = triggers[selectedGroup]?.firstOrNull { it.id == selectedId }

    private val windowWidth get() = shell?.width ?: 0f

    // dock only when the flow still fits at 100% zoom
    private val sidebarDocked get() = windowWidth >= LEFT_DOCK_MIN && !sidebarHidden
    private val sidebarFloating get() = windowWidth < LEFT_DOCK_MIN && sidebarOpen
    private val inspectorDocked get() = windowWidth >= RIGHT_DOCK_MIN && !inspectorHidden
    private val inspectorShown get() = inspectorDocked || inspectorFloating
    private val inspectorFloating get() = !inspectorDocked && (selected != null || overviewOpen)
    private val moduleOff get() = !CustomTriggers.enabled
    private val leftInset get() = if (sidebarDocked) SIDEBAR_W else 0f
    private val rightInset get() = if (inspectorDocked) INSPECTOR_W else 0f
    private val canvasTop get() = 65f + if (moduleOff) BANNER_H else 0f

    override val coveredRight get() = if (inspectorFloating) INSPECTOR_W else 0f

    private fun syncPanels() {
        val root = shell ?: return
        val sidebarShown = sidebarDocked || sidebarFloating
        val inspectorShown = inspectorShown
        // hidden fields would otherwise keep receiving keys
        val focused = root.ui.eventManager.focused
        if (focused != null && generateSequence(focused) { it.parent }.any {
                (!sidebarShown && it === sidebarElement) || (!inspectorShown && it === inspectorElement)
            }) root.ui.unfocus()
        sidebarElement.enabled = sidebarShown
        scrimElement.enabled = sidebarFloating
        inspectorElement.enabled = inspectorShown
        drawerBar.enabled = inspectorFloating
        banner.enabled = moduleOff
        val key = panelState()
        if (key != panelKey) {
            panelKey = key
            root.redraw = true
        }
    }

    private fun panelState() =
        listOf(windowWidth, sidebarDocked, sidebarFloating, inspectorDocked, inspectorFloating, moduleOff)

    private fun toggleSidebar() {
        if (windowWidth >= LEFT_DOCK_MIN) {
            sidebarHidden = !sidebarHidden
            syncPanels()
        } else sidebarOpen = !sidebarOpen
    }

    private fun toggleInspector() {
        root.ui.unfocus()
        if (windowWidth >= RIGHT_DOCK_MIN) {
            inspectorHidden = !inspectorHidden
            selected = null
            overviewOpen = false
            inspector.refresh()
        } else if (inspectorFloating) closeInspector()
        else showOverview()
    }

    private fun showOverview() {
        val floating = !inspectorDocked
        val wasOpen = floating && overviewOpen && selected == null
        selected = null
        overviewOpen = floating && !wasOpen
        root.ui.unfocus()
        inspector.refresh()
    }

    private fun closeInspector() {
        root.ui.unfocus()
        selected = null
        overviewOpen = false
        inspector.refresh()
    }

    override fun select(index: Int?) {
        if (selected == index) return
        selected = index
        root.ui.unfocus()
        inspector.refresh()
    }

    override fun requestAdd(slot: Int, kind: TriggerGraph.Kind, x: Float, y: Float) {
        val rule = rule ?: return
        val graph = TriggerGraph(rule)
        if (slot !in graph.slots(kind)) return
        val (title, sections) = if (kind == TriggerGraph.Kind.ACTION) "Add an action" to listOf(
            PickerSection(
                "Action", "Then do…", kind,
                TriggerManager.actionEntries.filter { it.second().supports(rule.trigger) }.map { it.first to it.second }
            )) else "Add a condition" to listOf(
            PickerSection(
                "Condition", "Only continue if…", kind,
                TriggerManager.conditionEntries.map { it.first to it.second }
            ))
        root.picker(x, y, title, sections) { component ->
            canvas.cancelDrag()
            val index = graph.insert(slot, component) ?: return@picker
            commit()
            selected = index
            inspector.refresh()
            canvas.reveal(rule, index)
            canvas.toast("Added ${NodeCatalog.info(component.typeNameOf()).name}")
        }
    }

    override fun reordered() {
        commit()
        inspector.refresh()
    }

    private fun commit() {
        TriggerManager.reset()
        save()
    }

    private fun save() {
        triggers.save()
        savedAt = System.currentTimeMillis()
        savedSnapshot = rule?.let { it.id to ConfigSystem.gson.toJson(it) }
    }

    override fun onFrame() {
        // window size or module state can change at any time, e.g. from the module list
        if (panelState() != panelKey) {
            syncPanels()
            headerWidth = -1f
        }
        if (canvas.width != headerWidth) {
            headerWidth = canvas.width
            // false defers the rebuild to the next frame, outside of drawing
            var deferred = false
            root.operation {
                if (!deferred) {
                    deferred = true
                    false
                } else {
                    // name field gets replaced, unfocus first so typing stays visible
                    val focused = root.ui.eventManager.focused
                    if (focused != null && generateSequence(focused) { it.parent }.any { it === header }) root.ui.unfocus()
                    syncPanels()
                    header.refresh()
                    true
                }
            }
        }
        val now = System.currentTimeMillis()
        if (now - checkedAt < AUTOSAVE_MS) return
        checkedAt = now
        // settings fields change components without notifying the editor
        saveIfChanged()
    }

    private fun saveIfChanged() {
        val rule = rule ?: return
        val previous = savedSnapshot
        if (previous == null || previous.first != rule.id) track()
        else if (previous.second != ConfigSystem.gson.toJson(rule)) save()
    }

    private fun track() {
        savedSnapshot = rule?.let { it.id to ConfigSystem.gson.toJson(it) }
    }

    private fun saveState(): Pair<String, Boolean> {
        TriggerManager.saveError?.let { return "Not saved: $it" to true }
        val age = System.currentTimeMillis() - savedAt
        return (if (age < 2500L) "Saved" else "Saves automatically") to false
    }

    // shared by the header, list dots and overview
    private fun status(rule: TriggerRule): RuleStatus = when {
        !rule.enabled -> RuleStatus("Off", theme.outline, "Switched off, so it never runs.")
        TriggerGraph(rule).let { g -> (0 until g.size).any { g.error(it) != null } || g.flowError() != null } ->
            RuleStatus("Incomplete", theme.error, "Fix the problems below so it can run.")

        moduleOff -> RuleStatus(
            "Paused",
            NodeCanvas.CONDITION_ACCENT,
            "Nothing runs while the Custom Triggers module is off."
        )

        else -> RuleStatus(
            "Active",
            NodeCanvas.ACTION_ACCENT,
            "Runs in SkyBlock whenever its event happens, except while this editor is open."
        )
    }

    private class RuleStatus(val label: String, val colour: Colour, val explanation: String)

    private fun openRule(group: String, id: String?) {
        root.ui.unfocus()
        canvas.cancelDrag()
        saveIfChanged()
        selectedGroup = group
        selectedId = id
        selected = null
        overviewOpen = false
        if (sidebarFloating) sidebarOpen = false
        track()
        refreshAll()
    }

    private fun refreshAll() {
        sidebar.refresh()
        header.refresh()
        inspector.refresh()
    }

    private fun Abobable.typeNameOf() = (this as TypeNamed).typeName

    fun build() = aboba("Custom Triggers") {
        root = this
        closing = false
        TriggerManager.editing = true
        if (selectedGroup !in triggers) selectedGroup = triggers.keys.firstOrNull() ?: "General"
        selectedId = triggers[selectedGroup]?.firstOrNull()?.id
        track()

        onAdd {
            // UIScreen.removed isn't called when mc replaces the screen directly
            mc.gui.screen()?.let { screen ->
                ScreenEvents.remove(screen).register { if (!closing) ui.close() }
            }
        }
        onRemove {
            closing = true
            ui.unfocus()
            overlay = null
            commit()
            TriggerManager.editing = false
        }
        onKeyPressed { (key, _) -> handleKey(key) }

        // absolute so panels can dock beside the flow or float over it
        block(copies(), colour = theme.background) {
            group(
                constrain(
                    Computed { leftInset },
                    0.px,
                    Computed { it.parent!!.width - leftInset - rightInset },
                    Copying
                )
            ) {
                header = refreshableGroup(constrain(0.px, 0.px, Copying, 64.px)) { headerBar() }
                block(constrain(0.px, 64.px, Copying, 1.px), colour = theme.outlineVariant)
                banner = moduleBanner().element
                group(constrain(0.px, Computed { canvasTop }, Copying, Computed { it.parent!!.height - canvasTop })) {
                    canvas = NodeCanvas(size(Copying, Copying), this@TriggerEditor)
                    val scope = canvas.scope {}
                    with(canvas) { scope.registerInput() }
                    canvasToolbar(scope)
                }
            }
            inspectorElement = block(
                constrain(0.px.alignOpposite, 0.px, INSPECTOR_W.px, Copying),
                colour = theme.surfaceContainerLow
            ) {
                block(constrain(0.px, 0.px, 1.px, Copying), colour = theme.outlineVariant)
                val top = { if (inspectorFloating) 84f else 22f }
                inspector = refreshableGroup(
                    constrain(20.px, Computed { top() }, Copying - 40.px, Computed { it.parent!!.height - top() - 22f })
                ) { inspectorPanel() }
                drawerBar = group(constrain(0.px, 0.px, Copying, 64.px)) {
                    label(
                        { if (selected != null) "Step" else "Overview" },
                        15f,
                        { theme.onSurface },
                        constrain(20.px, Centre, Copying - 140.px)
                    )
                    button(
                        "Close",
                        ButtonStyle.OUTLINE,
                        constrain(16.px.alignOpposite, Centre, Bounding + 28.px, 32.px),
                        fontSize = 13f
                    ) { closeInspector() }
                    block(constrain(0.px, 0.px.alignOpposite, Copying, 1.px), colour = theme.outlineVariant)
                }.element
            }.element
            scrimElement = block(copies(), colour = Colour.BLACK.withAlpha(0.35f)) {
                onClick {
                    sidebarOpen = false
                    true
                }
                onScroll { true }
            }.element
            sidebarElement = sidebarPanel().element
            shell = element
        }
        onAdd { syncPanels() }
    }

    private fun ElementScope<*>.moduleBanner() = block(
        constrain(0.px, 65.px, Copying, BANNER_H.px), colour = NodeCanvas.CONDITION_ACCENT.withAlpha(0.12f)
    ) {
        block(constrain(0.px, 0.px.alignOpposite, Copying, 1.px), colour = NodeCanvas.CONDITION_ACCENT.withAlpha(0.35f))
        block(constrain(24.px, Centre, 8.px, 8.px), colour = NodeCanvas.CONDITION_ACCENT, radius = 4.radius())
        label(
            { "The Custom Triggers module is off, so no trigger runs." }, 13.5f, { theme.onSurface },
            constrain(42.px, Centre, Copying - 210.px)
        )
        button(
            "Turn on module",
            ButtonStyle.OUTLINE,
            constrain(16.px.alignOpposite, Centre, Bounding + 28.px, 30.px),
            fontSize = 13f
        ) {
            if (!CustomTriggers.enabled) {
                CustomTriggers.toggle()
                Config.save()
            }
            syncPanels()
            refreshAll()
            canvas.toast("Custom Triggers is on. Enabled triggers run in SkyBlock.")
        }
    }

    private fun handleKey(key: Int): Boolean {
        if (key == Keybinds.KEY_ESCAPE) {
            overlay?.let {
                it.closePopup()
                overlay = null
                return true
            }
            if (sidebarFloating) {
                sidebarOpen = false
                return true
            }
            if (selected != null || overviewOpen) {
                closeInspector()
                return true
            }
            return false
        }
        if (overlay != null) return false
        if (key == Keybinds.KEY_DELETE || key == Keybinds.KEY_BACKSPACE) {
            val index = selected ?: return false
            if (index == 0) return false
            deleteNode(index)
            return true
        }
        return false
    }

    private fun deleteNode(index: Int) {
        val rule = rule ?: return
        canvas.cancelDrag()
        val removed = TriggerGraph(rule).remove(index) ?: return
        commit()
        selected = null
        inspector.refresh()
        canvas.toast("Removed ${NodeCatalog.info(removed.typeNameOf()).name}")
    }

    private fun ElementScope<*>.sidebarPanel() = block(
        constrain(0.px, 0.px, SIDEBAR_W.px, Copying), colour = theme.surfaceContainerLow
    ) {
        block(constrain(0.px.alignOpposite, 0.px, 1.px, Copying), colour = theme.outlineVariant)
        iconButton(
            PANEL_LEFT,
            constrain(14.px.alignOpposite, 18.px, 36.px, 36.px),
            fill = theme.surfaceContainerHigh
        ) { toggleSidebar() }
        column(constrain(18.px, 20.px, Copying - 70.px, Bounding), gap = 4.px) {
            text(string = "Custom Triggers", size = 19.px, colour = theme.onSurface)
            label({ "Run actions on game events." }, 12.5f, { theme.onSurfaceVariant }, size(Copying))
        }
        block(
            constrain(18.px, 76.px, Copying - 36.px, 34.px),
            colour = theme.surfaceContainerHigh,
            radius = 8.radius()
        ) {
            outline(theme.outlineVariant, 1.px)
            textInput(
                string = search, placeholder = "Search triggers", pos = at(12.px, Centre), size = 13.5.px,
                colour = theme.onSurface, placeHolderColour = theme.onSurfaceVariant, caretColour = theme.primary
            ) {
                searchInput = element
                maxWidth(Copying - 24.px)
                escapeLeavesField()
                onTextChanged {
                    search = it.string
                    sidebar.refresh()
                }
            }
            cursor(CursorShape.IBEAM)
        }
        sidebar =
            refreshableGroup(constrain(10.px, 124.px, Copying - 20.px, Copying - 124.px - 108.px)) { triggerList() }
        column(constrain(18.px, 18.px.alignOpposite, Copying - 36.px, Bounding), gap = 8.px) {
            button("+  New trigger", ButtonStyle.PRIMARY, size(Copying, 36.px)) { newTrigger(selectedGroup) }
            button("New group", ButtonStyle.GHOST, size(Copying, 32.px)) {
                val name = TriggerManager.addGroup()
                collapsed.remove(name)
                sidebar.refresh()
                renameGroupDialog(name)
            }
        }
    }

    private fun ElementScope<RefreshableGroup>.triggerList() {
        val offset = listScroll?.scrollOffset ?: 0f
        val list = scrollable(size(Copying, Copying)) {
            column(size(w = Copying), gap = 2.px) {
                var shown = 0
                triggers.forEach { (group, rules) ->
                    val groupMatches = search.isBlank() || group.contains(search, true)
                    val matches = rules.filter { groupMatches || it.name.contains(search, true) }
                    if (search.isNotBlank() && matches.isEmpty()) return@forEach
                    shown++
                    val open = group !in collapsed || search.isNotBlank()
                    groupRow(group, rules.size, open)
                    if (open) {
                        matches.forEach { triggerRow(group, it) }
                        if (rules.isEmpty()) group(size(Copying, 30.px)) {
                            text(
                                string = "No triggers yet",
                                size = 12.5.px,
                                colour = theme.onSurfaceVariant.withAlpha(0.7f),
                                pos = at(30.px, Centre)
                            )
                        }
                        val hidden = TriggerManager.unsupportedCount(group)
                        if (hidden > 0) group(size(Copying, 26.px)) {
                            text(
                                string = "$hidden unsupported, kept in file",
                                size = 12.px,
                                colour = theme.error,
                                pos = at(30.px, Centre)
                            )
                        }
                    }
                    group(size(Copying, 6.px))
                }
                if (shown == 0) group(size(Copying, 40.px)) {
                    text(
                        string = "Nothing matches \"$search\"",
                        size = 13.px,
                        colour = theme.onSurfaceVariant,
                        pos = at(12.px, Centre)
                    )
                }
            }
        }
        listScroll = list.element
        if (offset > 0f) list.scroll(offset, duration = 0f)
        onScroll { (amount) -> list.scroll(amount * -48f); true }
    }

    private fun ElementScope<*>.groupRow(group: String, count: Int, open: Boolean) = block(
        size(Copying, 32.px), colour = theme.surfaceContainerLow, radius = 7.radius()
    ) {
        image(theme.chevronImage, constrain(10.px, Centre, 11.px, 11.px), colour = theme.onSurfaceVariant) {
            rotation(if (open) 90f else 0f)
        }
        label({ group.uppercase() }, 11.5f, { theme.onSurfaceVariant }, constrain(28.px, Centre, Copying - 96.px))
        text(
            string = "$count",
            size = 11.5.px,
            colour = theme.onSurfaceVariant.withAlpha(0.7f),
            pos = at(44.px.alignOpposite, Centre)
        )
        iconButton(MORE, constrain(6.px.alignOpposite, Centre, 26.px, 24.px), fill = theme.surfaceContainerLow) {
            menu(
                element.x, element.y + element.height + 4f, listOf(
                    "New trigger here" to { newTrigger(group) },
                    "Rename group" to { renameGroupDialog(group) },
                    "Delete group" to {
                        val total = count + TriggerManager.unsupportedCount(group)
                        confirm(
                            "Delete group \"$group\"?",
                            "This removes the group and its $total trigger${if (total == 1) "" else "s"}."
                        ) {
                            TriggerManager.deleteGroup(group)
                            if (selectedGroup == group) openRule(triggers.keys.firstOrNull() ?: "General", null)
                            else sidebar.refresh()
                        }
                    },
                )
            )
        }
        tonalHover()
        cursor(CursorShape.HAND)
        onClick {
            if (!collapsed.add(group)) collapsed.remove(group)
            sidebar.refresh()
            true
        }
    }

    private fun ElementScope<*>.triggerRow(group: String, rule: TriggerRule) {
        val active = rule.id == selectedId
        block(
            size(Copying, 36.px),
            colour = if (active) theme.primary.withAlpha(0.14f) else theme.surfaceContainerLow,
            radius = 7.radius()
        ) {
            if (active) block(constrain(0.px, Centre, 3.px, 18.px), colour = theme.primary, radius = 2.radius())
            val status = { statusColour(rule) }
            block(constrain(16.px, Centre, 8.px, 8.px), colour = Colour.TRANSPARENT, radius = 4.radius()) {
                element.colour = object : Colour {
                    override val rgb get() = status().rgb
                }
            }
            label(
                { rule.name },
                13.5f,
                { if (active) theme.onSurface else theme.onSurface.withAlpha(0.85f) },
                constrain(34.px, Centre, Copying - 100.px)
            )
            label(
                { if (rule.enabled) "" else "Off" },
                12f,
                { theme.onSurfaceVariant.withAlpha(0.8f) },
                constrain(40.px.alignOpposite, Centre, 24.px)
            )
            iconButton(
                MORE,
                constrain(6.px.alignOpposite, Centre, 26.px, 24.px),
                fill = if (active) theme.surfaceContainerHigh else theme.surfaceContainerLow
            ) {
                ruleMenu(group, rule, element.x, element.y + element.height + 4f)
            }
            if (!active) tonalHover()
            cursor(CursorShape.HAND)
            onClick {
                if (rule.id != selectedId) openRule(group, rule.id)
                true
            }
        }
    }

    private fun statusColour(rule: TriggerRule): Colour = status(rule).colour

    private fun runActions(rule: TriggerRule) {
        val error = TriggerManager.test(rule)
        canvas.toast(error ?: "Running the actions once. The event and conditions are skipped.", error = error != null)
    }

    private fun ElementScope<*>.ruleMenu(group: String, rule: TriggerRule, x: Float, y: Float) {
        val others = triggers.keys.filter { it != group }
        menu(x, y, buildList {
            add("Duplicate" to { duplicate(group, rule) })
            if (others.isNotEmpty()) add("Move to group…" to {
                menu(x, y, others.map { target ->
                    "Move to \"$target\"" to {
                        triggers[group]?.remove(rule)
                        triggers.getOrPut(target) { mutableListOf() }.add(rule)
                        collapsed.remove(target)
                        commit()
                        openRule(target, rule.id)
                    }
                })
            })
            add("Delete trigger" to {
                confirm("Delete \"${rule.name}\"?", "This trigger and all of its steps will be removed.") {
                    triggers[group]?.remove(rule)
                    commit()
                    if (rule.id == selectedId) openRule(
                        group,
                        triggers[group]?.firstOrNull()?.id
                    ) else sidebar.refresh()
                }
            })
        })
    }

    private fun newTrigger(group: String) {
        val target = if (group in triggers) group else TriggerManager.addGroup()
        val rule = TriggerRule(nextName(target))
        TriggerManager.addTrigger(target, rule)
        collapsed.remove(target)
        // clearing the field also clears the filter through its change handler
        searchInput.text = ""
        openRule(target, rule.id)
        selected = 0
        inspector.refresh()
        canvas.toast("Choose what starts the trigger, then add an action.")
    }

    private fun nextName(group: String): String {
        val names = triggers[group].orEmpty().map { it.name }.toSet()
        return generateSequence(1) { it + 1 }.map { if (it == 1) "New trigger" else "New trigger $it" }
            .first { it !in names }
    }

    private fun duplicate(group: String, rule: TriggerRule) {
        val gson = ConfigSystem.gson
        val copy = gson.fromJson(gson.toJson(rule), TriggerRule::class.java)
            .copy(id = UUID.randomUUID().toString(), name = "${rule.name} copy".take(60))
        val list = triggers.getOrPut(group) { mutableListOf() }
        list.add(list.indexOf(rule) + 1, copy)
        commit()
        openRule(group, copy.id)
    }

    private fun ElementScope<RefreshableGroup>.headerBar() {
        val listToggle = !sidebarDocked
        if (listToggle) iconButton(
            PANEL_LEFT,
            constrain(16.px, Centre, 36.px, 36.px),
            fill = if (sidebarOpen) theme.surfaceContainerHigh else theme.background
        ) { toggleSidebar() }
        val rule = rule ?: return
        // narrow headers drop the switch label and shorten the run button
        val compact = headerWidth in 1f..1000f
        // very narrow headers leave running to the overview
        val tiny = headerWidth in 1f..640f
        val left = if (listToggle) 62 else 24
        val reserved = left + if (tiny) 130 else if (compact) 250 else 340
        column(constrain(left.px, Centre, Copying - reserved.px, Bounding), gap = 5.px) {
            textInput(
                string = rule.name, placeholder = "Trigger name", size = 19.px,
                colour = theme.onSurface, placeHolderColour = theme.onSurfaceVariant, caretColour = theme.primary
            ) {
                maxWidth(Copying)
                cursor(CursorShape.IBEAM)
                escapeLeavesField()
                onTextChanged {
                    if (it.string.length > 60) it.cancel()
                    else if (it.string.isNotBlank()) rule.name = it.string
                }
                onFocusLost {
                    element.text = rule.name
                    save()
                    sidebar.refresh()
                }
            }
            statusLine(
                { status(rule).let { it.label to it.colour } },
                { "   ·   $selectedGroup   ·   ${saveState().first}" }, 12.5f, size(Copying)
            ) {
                cursor(CursorShape.HAND)
                onClick {
                    showOverview()
                    true
                }
            }
        }
        row(constrain(16.px.alignOpposite, Centre, Bounding, 36.px), gap = 12.px) {
            group(size(Bounding, 36.px)) {
                row(at(0.px, Centre), gap = 8.px) {
                    group(size(Bounding, 36.px)) {
                        switch(rule::enabled, size = 20.px, pos = at(0.px, Centre), onToggle = {
                            TriggerManager.reset()
                            root.operation {
                                commit()
                                sidebar.refresh()
                                true
                            }
                        })
                    }
                    if (!compact) label({ "Enabled" }, 13.5f, { theme.onSurfaceVariant }, size(56.px)).apply {
                        element.constraints.y = Centre
                    }
                }
            }
            if (!tiny) block(
                size((if (compact) 104 else 150).px, 36.px),
                colour = theme.surfaceContainerHigh,
                radius = 8.radius()
            ) {
                outline(theme.outlineVariant, 1.px)
                centredLabel(
                    { if (TriggerManager.testRunning(rule)) "Running…" else if (compact) "Run once" else "Run actions once" },
                    13.5f,
                    {
                        if (TriggerManager.testError(rule) == null) theme.onSurface else theme.onSurfaceVariant.withAlpha(
                            0.6f
                        )
                    },
                    constrain(8.px, 0.px, Copying - 16.px, Copying)
                )
                tonalHover()
                cursor(CursorShape.HAND)
                onClick {
                    ui.unfocus()
                    runActions(rule)
                    true
                }
            }
            iconButton(
                PANEL_RIGHT,
                size(36.px, 36.px),
                fill = if (inspectorShown) theme.surfaceContainerHigh else theme.background
            ) { toggleInspector() }
        }
    }

    private fun canvasToolbar(scope: ElementScope<NodeCanvas>) = scope.row(
        constrain(Computed { it.parent!!.width - it.width - 14f - coveredRight }, 14.px, Bounding, 32.px), gap = 6.px
    ) {
        block(size(Bounding, 32.px), colour = theme.surfaceContainer, radius = 8.radius()) {
            outline(theme.outlineVariant, 1.px)
            row(size(Bounding, 32.px)) {
                iconButton("−", size(32.px, 32.px), fill = theme.surfaceContainer) { canvas.zoomBy(1 / 1.2f) }
                group(size(52.px, 32.px)) {
                    quoi.api.abobaui.elements.impl.Text.run {
                        textSupplied(
                            { canvas.zoomPercent()?.let { "$it%" } ?: "" },
                            colour = theme.onSurfaceVariant,
                            size = 12.5.px,
                            pos = at(Centre, Centre)
                        )
                    }
                }
                iconButton("+", size(32.px, 32.px), fill = theme.surfaceContainer) { canvas.zoomBy(1.2f) }
            }
        }
        button(
            "Fit view",
            ButtonStyle.OUTLINE,
            size(Bounding + 24.px, 32.px),
            fontSize = 12.5f
        ) { rule?.let(canvas::fit) }
    }

    private fun ElementScope<RefreshableGroup>.inspectorPanel() {
        val rule = rule
        if (rule == null) {
            text(string = "Inspector", size = 16.px, colour = theme.onSurface, pos = at(0.px, 0.px))
            paragraph(
                { "Select or create a trigger to see its steps here." },
                13f,
                { theme.onSurfaceVariant },
                constrain(0.px, 30.px, Copying)
            )
            return
        }
        val graph = TriggerGraph(rule)
        val index = selected?.takeIf { it < graph.size }
        if (index == null) overview(rule, graph) else nodeDetails(rule, graph, index)
    }

    private fun ElementScope<*>.overview(rule: TriggerRule, graph: TriggerGraph) {
        val list = scrollable(constrain((-SCROLL_BLEED).px, 0.px, Copying + (SCROLL_BLEED * 2).px, Copying)) {
            column(constrain(SCROLL_BLEED.px, 0.px, Copying - (SCROLL_BLEED * 2).px, Bounding), gap = 8.px) {
                statusLine({ status(rule).let { it.label to it.colour } }, { "" }, 20f, size(Copying))
                group(size(Copying, 2.px))
                paragraph({ status(rule).explanation }, 14f, { theme.onSurface }, size(Copying))
                paragraph({ runText(rule) }, 13f, { theme.onSurfaceVariant }, size(Copying))

                val problems = (0 until graph.size).mapNotNull { index -> graph.error(index)?.let { index to it } }
                val flowError = graph.flowError()
                if (problems.isNotEmpty() || flowError != null) {
                    sectionTitle("Needs fixing")
                    problems.forEach { (index, error) ->
                        val component = graph.component(index)
                        problemRow(NodeCatalog.info(component.typeNameOf()).name, error) { select(index) }
                    }
                    flowError?.let { problemRow("Actions", it, null) }
                }

                sectionTitle("Try it")
                block(size(Copying, 38.px), colour = theme.surfaceContainerHigh, radius = 8.radius()) {
                    outline(theme.outlineVariant, 1.px)
                    centredLabel(
                        { if (TriggerManager.testRunning(rule)) "Running…" else "Run actions once" }, 14f,
                        {
                            if (TriggerManager.testError(rule) == null) theme.onSurface else theme.onSurfaceVariant.withAlpha(
                                0.6f
                            )
                        },
                        constrain(8.px, 0.px, Copying - 16.px, Copying)
                    )
                    tonalHover()
                    cursor(CursorShape.HAND)
                    onClick {
                        ui.unfocus()
                        runActions(rule)
                        true
                    }
                }
                paragraph(
                    {
                        TriggerManager.testError(rule)
                            ?: "Runs the actions one time now and skips the event and conditions."
                    },
                    13f, { theme.onSurfaceVariant }, size(Copying)
                )

                val placeholders = NodeCatalog.info(rule.trigger.typeNameOf()).placeholders
                if (placeholders.isNotEmpty()) {
                    sectionTitle("Text placeholders")
                    row(size(Copying, 28.px), gap = 6.px) {
                        placeholders.forEach { placeholder ->
                            block(
                                size(Bounding + 18.px, 28.px),
                                colour = theme.surfaceContainerHigh,
                                radius = 6.radius()
                            ) {
                                text(string = placeholder, size = 13.px, colour = theme.onSurface)
                            }
                        }
                    }
                    paragraph(
                        { "Type them into an action's text to insert details of the event." },
                        13f,
                        { theme.onSurfaceVariant },
                        size(Copying)
                    )
                }
            }
        }
        onScroll { (amount) -> list.scroll(amount * -48f); true }
    }

    private fun runText(rule: TriggerRule): String {
        val stats = TriggerManager.runStats(rule) ?: return "Has not run since the game started."
        val seconds = (System.currentTimeMillis() - stats.lastMillis) / 1000
        val ago = when {
            seconds < 10 -> "just now"
            seconds < 60 -> "$seconds s ago"
            seconds < 3600 -> "${seconds / 60} min ago"
            else -> "${seconds / 3600} h ago"
        }
        val times = if (stats.count == 1) "once" else "${stats.count} times"
        return "Last ran $ago, $times since the game started."
    }

    private fun ElementScope<*>.sectionTitle(title: String) = group(size(Copying, 42.px)) {
        text(string = title, size = 15.px, colour = theme.onSurface, pos = at(0.px, 0.px.alignOpposite))
    }

    private fun ElementScope<*>.problemRow(title: String, error: String, onClick: (() -> Unit)?) = block(
        size(Copying, 52.px), colour = theme.surfaceContainer, radius = 8.radius()
    ) {
        outline(theme.error.withAlpha(0.45f), 1.px)
        block(constrain(12.px, 15.px, 7.px, 7.px), colour = theme.error, radius = 4.radius())
        label({ title }, 12f, { theme.onSurfaceVariant }, constrain(28.px, 10.px, Copying - 40.px))
        label({ error }, 13.5f, { theme.error }, constrain(28.px, 28.px, Copying - 40.px))
        if (onClick != null) {
            tonalHover()
            cursor(CursorShape.HAND)
            onClick {
                onClick()
                true
            }
        }
    }

    private fun ElementScope<*>.nodeDetails(rule: TriggerRule, graph: TriggerGraph, index: Int) {
        val kind = graph.kind(index)
        val component = graph.component(index)
        val info = NodeCatalog.info(component.typeNameOf())
        val accent = NodeCanvas.accent(kind)
        val step = index - graph.boundary + 1

        val list = scrollable(constrain((-SCROLL_BLEED).px, 0.px, Copying + (SCROLL_BLEED * 2).px, Copying - 56.px)) {
            column(constrain(SCROLL_BLEED.px, 0.px, Copying - (SCROLL_BLEED * 2).px, Bounding), gap = 10.px) {
                row(size(Bounding, 22.px), gap = 8.px) {
                    block(size(Bounding + 14.px, 22.px), colour = accent.withAlpha(0.2f), radius = 5.radius()) {
                        quoi.api.abobaui.elements.impl.Text.run {
                            textSupplied(
                                { NodeCanvas.chipLabel(kind, step) },
                                colour = NodeCanvas.accentText(kind),
                                size = 11.5.px,
                                pos = at(Centre, Centre)
                            )
                        }
                    }
                    text(
                        string = when (kind) {
                            TriggerGraph.Kind.EVENT -> "Starts the trigger"
                            TriggerGraph.Kind.CONDITION -> "Condition, checked before the actions"
                            TriggerGraph.Kind.ACTION -> "Action $step of ${rule.actions.size}"
                        }, size = 13.px, colour = theme.onSurfaceVariant, pos = at(y = Centre)
                    )
                }
                text(string = info.name, size = 20.px, colour = theme.onSurface)
                if (info.description.isNotEmpty()) paragraph(
                    { info.description },
                    13.5f,
                    { theme.onSurfaceVariant },
                    size(Copying)
                )

                if (kind == TriggerGraph.Kind.EVENT) {
                    button("Change event…", ButtonStyle.OUTLINE, size(Copying, 34.px)) {
                        val anchorX = element.x
                        val anchorY = element.y + element.height + 6f
                        root.picker(
                            anchorX, anchorY, "Start this trigger when…", listOf(
                                PickerSection(
                                    "Event", "Starts the trigger", TriggerGraph.Kind.EVENT,
                                    TriggerManager.triggerEntries.map { it.first to it.second }
                                ))) { picked ->
                            rule.trigger = picked as quoi.api.customtriggers.triggers.Trigger
                            commit()
                            refreshAll()
                        }
                    }
                }

                sectionTitle("Settings")
                group(size(Copying, Bounding)) {
                    column(size(w = Copying), gap = 10.px) { with(component) { draw() } }
                }

                if (component is TriggerCondition) {
                    group(size(Copying, 2.px))
                    block(size(Copying, 60.px), colour = theme.surfaceContainer, radius = 8.radius()) {
                        outline(theme.outlineVariant, 1.px)
                        column(constrain(14.px, Centre, Copying - 84.px, Bounding), gap = 4.px) {
                            text(string = "Invert this check", size = 14.px, colour = theme.onSurface)
                            label(
                                { "Passes when: ${component.summary()}" },
                                12.5f,
                                { theme.onSurfaceVariant },
                                size(Copying)
                            )
                        }
                        val inverted = object {
                            var value: Boolean
                                get() = !component.expected
                                set(v) {
                                    component.expected = !v
                                }
                        }
                        switch(inverted::value, size = 18.px, pos = at(14.px.alignOpposite, Centre))
                    }
                    if (rule.conditions.size > 1) paragraph(
                        { "All ${rule.conditions.size} conditions must pass before the actions run." },
                        12.5f, { theme.onSurfaceVariant }, size(Copying)
                    )
                }

                val placeholders = NodeCatalog.info(rule.trigger.typeNameOf()).placeholders
                if (kind == TriggerGraph.Kind.ACTION && placeholders.isNotEmpty()) {
                    paragraph(
                        {
                            "Values you can use in text: ${placeholders.joinToString(", ")}" +
                                if (rule.trigger.typeNameOf() == "chat_received") "\nWith Regex matching, %1%, %2%… insert the matched groups." else ""
                        },
                        12.5f, { theme.onSurfaceVariant }, size(Copying), background = theme.surfaceContainer
                    )
                }

                // live problem box, zero height while the node is valid
                paragraph(
                    { graph.error(index).orEmpty() }, 13f, { theme.error }, size(Copying),
                    background = theme.errorContainer.withAlpha(0.35f), border = theme.error.withAlpha(0.5f)
                )
            }
        }
        onScroll { (amount) -> list.scroll(amount * -48f); true }

        group(constrain(0.px, 0.px.alignOpposite, Copying, 38.px)) {
            if (kind == TriggerGraph.Kind.EVENT) {
                paragraph(
                    { "Every trigger has exactly one start event." },
                    12.5f,
                    { theme.onSurfaceVariant.withAlpha(0.8f) },
                    constrain(0.px, Centre, Copying)
                )
            } else {
                button("Delete step", ButtonStyle.DANGER, size(Copying, 38.px)) { deleteNode(index) }
            }
        }
    }

    class PickerSection(
        val title: String,
        val subtitle: String,
        val kind: TriggerGraph.Kind,
        val entries: List<Pair<String, () -> Any>>,
    )

    private fun ElementScope<*>.showOverlay(block: Popup.() -> Unit): Popup {
        overlay?.closePopup(false)
        ui.unfocus()
        val popup = popup(copies(), smooth = false) {
            val self = this
            onRemove { if (overlay === self) overlay = null }
            block(copies(), colour = Colour.TRANSPARENT) {
                onClick {
                    self.closePopup()
                    true
                }
                onClick(1) {
                    self.closePopup()
                    true
                }
            }
            block()
        }
        overlay = popup
        return popup
    }

    private fun closeOverlay() {
        overlay?.closePopup(false)
        overlay = null
    }

    private fun ElementScope<*>.picker(
        x: Float,
        y: Float,
        title: String,
        sections: List<PickerSection>,
        onPick: (Abobable) -> Unit
    ) {
        var query = ""
        val width = 360f
        val rows = sections.sumOf { it.entries.size } + sections.size
        val height = (96f + (rows * 50f)).coerceAtMost(460f)
        showOverlay {
            block(
                constrain(ScreenClamp(x, width), ScreenClamp(y, height), width.px, height.px),
                colour = theme.surfaceContainer, radius = 12.radius()
            ) {
                outline(theme.outlineVariant, 1.px)
                dropShadow(
                    blur = 30f,
                    spread = 0f,
                    colour = Colour.RGB(0, 0, 0, 0.5f),
                    offsetY = 6f,
                    radius = 12.radius()
                )
                onClick { true }
                text(string = title, size = 15.px, colour = theme.onSurface, pos = at(16.px, 16.px))
                lateinit var results: RefreshableGroup
                lateinit var input: ElementScope<TextInput>
                block(
                    constrain(12.px, 44.px, Copying - 24.px, 34.px),
                    colour = theme.surfaceContainerHigh,
                    radius = 8.radius()
                ) {
                    outline(theme.primary.withAlpha(0.6f), 1.px)
                    input = textInput(
                        placeholder = "Search…", pos = at(12.px, Centre), size = 13.5.px, colour = theme.onSurface,
                        placeHolderColour = theme.onSurfaceVariant, caretColour = theme.primary
                    ) {
                        maxWidth(Copying - 24.px)
                        onTextChanged {
                            query = it.string
                            results.refresh()
                        }
                        onKeyPressed { (key, _) ->
                            when (key) {
                                Keybinds.KEY_ESCAPE -> {
                                    closeOverlay(); true
                                }

                                Keybinds.KEY_ENTER -> {
                                    val first = sections.firstNotNullOfOrNull { section ->
                                        NodeCatalog.search(section.entries.map { it.first }, query).firstOrNull()
                                            ?.let { type -> section.entries.first { it.first == type } }
                                    }
                                    if (first != null) {
                                        closeOverlay()
                                        onPick(first.second() as Abobable)
                                    }
                                    true
                                }

                                else -> false
                            }
                        }
                    }
                    cursor(CursorShape.IBEAM)
                }
                results = refreshableGroup(constrain(8.px, 86.px, Copying - 16.px, Copying - 94.px)) {
                    val list = scrollable(size(Copying, Copying)) {
                        column(size(w = Copying), gap = 2.px) {
                            var any = false
                            sections.forEach { section ->
                                val types = NodeCatalog.search(section.entries.map { it.first }, query)
                                if (types.isEmpty()) return@forEach
                                any = true
                                val accent = NodeCanvas.accent(section.kind)
                                group(size(Copying, 28.px)) {
                                    row(at(8.px, Centre), gap = 8.px) {
                                        text(
                                            string = section.title.uppercase(),
                                            size = 11.px,
                                            colour = NodeCanvas.accentText(section.kind)
                                        )
                                        text(
                                            string = section.subtitle,
                                            size = 11.px,
                                            colour = theme.onSurfaceVariant.withAlpha(0.8f)
                                        )
                                    }
                                }
                                types.forEach { type ->
                                    val info = NodeCatalog.info(type)
                                    val factory = section.entries.first { it.first == type }.second
                                    block(size(Copying, 48.px), colour = theme.surfaceContainer, radius = 8.radius()) {
                                        block(
                                            constrain(10.px, Centre, 8.px, 8.px),
                                            colour = accent,
                                            radius = 4.radius()
                                        )
                                        label(
                                            { info.name },
                                            13.5f,
                                            { theme.onSurface },
                                            constrain(28.px, 8.px, Copying - 40.px)
                                        )
                                        label(
                                            { info.description },
                                            11.5f,
                                            { theme.onSurfaceVariant },
                                            constrain(28.px, 27.px, Copying - 40.px)
                                        )
                                        tonalHover()
                                        cursor(CursorShape.HAND)
                                        onClick {
                                            closeOverlay()
                                            onPick(factory() as Abobable)
                                            true
                                        }
                                    }
                                }
                            }
                            if (!any) group(size(Copying, 40.px)) {
                                text(
                                    string = "No matches",
                                    size = 13.px,
                                    colour = theme.onSurfaceVariant,
                                    pos = at(10.px, Centre)
                                )
                            }
                        }
                    }
                    onScroll { (amount) -> list.scroll(amount * -50f); true }
                }
                ui.focus(input.element)
            }
        }
    }

    private fun ElementScope<*>.menu(x: Float, y: Float, items: List<Pair<String, () -> Unit>>) {
        val width = 210f
        val height = items.size * 34f + 12f
        showOverlay {
            block(
                constrain(ScreenClamp(x, width), ScreenClamp(y, height), width.px, height.px),
                colour = theme.surfaceContainer, radius = 10.radius()
            ) {
                outline(theme.outlineVariant, 1.px)
                dropShadow(
                    blur = 24f,
                    spread = 0f,
                    colour = Colour.RGB(0, 0, 0, 0.45f),
                    offsetY = 4f,
                    radius = 10.radius()
                )
                onClick { true }
                column(constrain(6.px, 6.px, Copying - 12.px, Bounding)) {
                    items.forEach { (label, action) ->
                        val danger = label.startsWith("Delete")
                        block(size(Copying, 34.px), colour = theme.surfaceContainer, radius = 6.radius()) {
                            text(
                                string = label,
                                size = 13.px,
                                colour = if (danger) theme.error else theme.onSurface,
                                pos = at(10.px, Centre)
                            )
                            tonalHover()
                            cursor(CursorShape.HAND)
                            onClick {
                                closeOverlay()
                                action()
                                true
                            }
                        }
                    }
                }
            }
        }
    }

    private fun ElementScope<*>.dialog(title: String, content: ElementScope<*>.() -> Unit) {
        showOverlay {
            block(copies(), colour = Colour.BLACK.withAlpha(0.45f)) { onClick { closeOverlay(); true } }
            block(size(420.px, Bounding + 22.px), colour = theme.surfaceContainer, radius = 14.radius()) {
                outline(theme.outlineVariant, 1.px)
                onClick { true }
                column(constrain(22.px, 22.px, Copying - 44.px, Bounding), gap = 14.px) {
                    text(string = title, size = 17.px, colour = theme.onSurface)
                    content()
                }
            }
        }
    }

    private fun ElementScope<*>.dialogButtons(confirmLabel: String, style: ButtonStyle, onConfirm: () -> Unit) =
        row(size(Copying, 34.px), gap = 8.px) {
            group(size(Fill - 216.px, 34.px))
            button("Cancel", ButtonStyle.GHOST, size(100.px, 34.px)) { closeOverlay() }
            button(confirmLabel, style, size(108.px, 34.px)) { onConfirm() }
        }

    private fun ElementScope<*>.confirm(title: String, message: String, onConfirm: () -> Unit) = dialog(title) {
        paragraph({ "$message This cannot be undone." }, 13f, { theme.onSurfaceVariant }, size(Copying))
        dialogButtons("Delete", ButtonStyle.DANGER) {
            closeOverlay()
            onConfirm()
        }
    }

    private fun ElementScope<*>.renameGroupDialog(group: String) {
        var name = group
        var error = ""
        fun save() {
            val renamed = name.trim()
            when {
                renamed == group -> closeOverlay()
                TriggerManager.renameGroup(group, renamed) -> {
                    if (selectedGroup == group) selectedGroup = renamed
                    if (collapsed.remove(group)) collapsed.add(renamed)
                    closeOverlay()
                    refreshAll()
                }

                else -> error = "Use a unique name with 1–40 characters."
            }
        }
        dialog("Rename group") {
            block(size(Copying, 36.px), colour = theme.surfaceContainerHigh, radius = 8.radius()) {
                outline(theme.primary.withAlpha(0.6f), 1.px)
                val input = textInput(
                    string = group,
                    pos = at(12.px, Centre),
                    size = 14.px,
                    colour = theme.onSurface,
                    caretColour = theme.primary
                ) {
                    maxWidth(Copying - 24.px)
                    onTextChanged { name = it.string }
                    onKeyPressed { (key, _) ->
                        when (key) {
                            Keybinds.KEY_ESCAPE -> {
                                closeOverlay(); true
                            }

                            Keybinds.KEY_ENTER -> {
                                save(); true
                            }

                            else -> false
                        }
                    }
                }
                cursor(CursorShape.IBEAM)
                ui.focus(input.element)
            }
            paragraph({ error }, 12.5f, { theme.error }, size(Copying))
            dialogButtons("Save", ButtonStyle.PRIMARY) { save() }
        }
    }

    private companion object {
        const val AUTOSAVE_MS = 600L
        const val SIDEBAR_W = 280f
        const val INSPECTOR_W = 360f

        // outlines and switch tracks extend past the content bounds
        const val SCROLL_BLEED = 6f
        const val BANNER_H = 46f
        const val FLOW_MIN = FlowLayout.WIDE_WIDTH + NodeCanvas.MARGIN * 2
        const val RIGHT_DOCK_MIN = FLOW_MIN + INSPECTOR_W
        const val LEFT_DOCK_MIN = RIGHT_DOCK_MIN + SIDEBAR_W
    }
}
