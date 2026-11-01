package quoi.api.skyblock.dungeon.odonscanning

import quoi.QuoiMod.mc
import quoi.api.abobaui.constraints.impl.positions.Centre
import quoi.api.abobaui.constraints.impl.size.Copying
import quoi.api.abobaui.dsl.*
import quoi.api.abobaui.elements.Element
import quoi.api.abobaui.elements.ElementScope
import quoi.api.abobaui.elements.impl.Block.Companion.outline
import quoi.api.abobaui.elements.impl.Text.Companion.shadow
import quoi.api.abobaui.elements.impl.Text.Companion.string
import quoi.api.animations.Animation
import quoi.api.colour.*
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.ScoreCalculation
import quoi.api.skyblock.dungeon.odonscanning.tiles.*
import quoi.api.vec.Vec2i
import quoi.module.impl.dungeon.DungeonMap
import quoi.module.impl.dungeon.autoclear.impl.InteractiveMap
import quoi.module.impl.dungeon.autoclear.pathToDoor
import quoi.module.impl.dungeon.autoclear.pathToRoom
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.StringUtils.width
import quoi.utils.ui.rendering.Font
import quoi.utils.ui.rendering.NVGRenderer
import quoi.utils.ui.rendering.NVGRenderer.image
import quoi.utils.ui.watch

object MapRenderer {

    val mapSize: Vec2i get() = MapScan.mapSize
    var layoutRevision = 0
        private set


    fun refresh() {
        layoutRevision++
    }

    fun ElementScope<*>.renderMap(config: MapConfig): ElementScope<*> {
        val (scale, radius) = config

        val size = 116f * scale
        val mapHeight = 128f * scale

        return block(
            size(mapHeight.px, config.height.px),
            colour = DungeonMap.bgColour,
            (radius * 1.5f).radius()
        ) {
            if (DungeonMap.border) outline(DungeonMap.borderColour, thickness = DungeonMap.borderThickness.px)

            val viewport = group(constrain(y = 0.px, w = Copying, h = mapHeight.px))
            val content = viewport.group(constrain(Centre, Centre, 0.px, 0.px))
            val doors = content.group(size(Copying, Copying))
            val rooms = content.group(size(Copying, Copying))
            val markers = PlayerMarkers(config)
            content.apply { markers.scope {} }
            val roomViews = mutableMapOf<OdonRoom, RoomView>()
            val doorViews = mutableMapOf<OdonDoor, Element>()
            var previousScale = Float.NaN
            var previousOverlay: OverlaySettings? = null

            if (DungeonMap.showInfo) renderInfo(config, mapHeight)

            watch({ layoutRevision to OverlaySettings() }, immediate = true) { (_, overlay) ->
                val overlayChanged = previousOverlay != overlay
                previousOverlay = overlay
                val baseW = (mapSize.x * 16 + (mapSize.x - 1) * 4).toFloat().coerceAtLeast(1f)
                val baseH = (mapSize.z * 16 + (mapSize.z - 1) * 4).toFloat().coerceAtLeast(1f)
                val newScale = size / maxOf(baseW, baseH)
                val resized = previousScale != newScale
                previousScale = newScale
                val new = config.copy(
                    scale = newScale,
                    radius = config.radius * (newScale / scale),
                )
                content.element.constraints.width = (baseW * newScale).px
                content.element.constraints.height = (baseH * newScale).px
                markers.config = new

                doorViews.entries.removeIf { (door, element) ->
                    (resized || door !in ScanUtils.scannedDoors).also { remove ->
                        if (remove) doors.element.removeElement(element)
                    }
                }
                for (door in ScanUtils.scannedDoors) {
                    doorViews.getOrPut(door) { doors.renderDoor(new, door) }
                }
                roomViews.entries.removeIf { (room, view) ->
                    (room !in ScanUtils.scannedRooms).also { remove ->
                        if (remove) rooms.element.removeElement(view.element)
                    }
                }
                for (room in ScanUtils.scannedRooms) {
                    val view = roomViews.getOrPut(room) { RoomView(rooms.group(size(Copying, Copying)).element) }
                    val label = buildList {
                        if (room.identified && room.data.type !in unlabelledTypes) {
                            val secrets = if (room.data.secrets == 0) "0" else "${room.secretsFound ?: 0}/${room.data.secrets}"
                            when (overlay.style) {
                                "Secrets" -> add(secrets)
                                "Room Name" -> addAll(room.name.split(" "))
                                "Room Name + Secrets" -> {
                                    addAll(room.name.split(" "))
                                    if (room.data.secrets > 0) add(secrets)
                                }
                                "Secrets + Puzzle Name" -> {
                                    if (room.data.type == RoomType.PUZZLE || room.data.secrets == 0) addAll(room.name.split(" "))
                                    else add(secrets)
                                }
                            }
                        }
                    }
                    if (!resized && !overlayChanged && view.tiles == room.tiles && view.label == label && view.state == room.data.state) continue
                    view.element.removeAll()
                    ElementScope(view.element).apply {
                        renderTiles(new, room)
                        if (overlay.style == "Checkmarks") renderCheckmark(new, room)
                        renderName(new, room, label)
                    }
                    view.tiles = room.tiles.toSet()
                    view.label = label
                    view.state = room.data.state
                }
                if (ui.initialised) {
                    element.size()
                    element.positionChildren()
                    element.clip()
                }
                ui.recalculateMouse = true
            }
        }
    }

    // based on: https://github.com/Noamm9/NoammAddons/blob/1bca286c77939638c13029b1b6d7f387cf92b3f4/src/main/kotlin/com/github/noamm9/features/impl/dungeon/map/MapRenderer.kt
    private fun ElementScope<*>.renderInfo(config: MapConfig, y: Float) {
        val textSize = 6.3f * config.scale
        val maxWidth = 116f * config.scale
        for (index in 0..1) {
            val label = text(
                "",
                font = config.font,
                pos = at(Centre, (y + config.scale * (-2f + index * 7f)).px),
                size = textSize.px
            ) {
                shadow = config.shadow
            }
            watch({ infoLines()[index] }, immediate = true) { line ->
                val size = MapTextLayout.fitSize(listOf(line), textSize, maxWidth, textSize) { text, size ->
                    textWidth(text, size, config.font)
                }
                val width = textWidth(line, size, config.font)
                label.string = line
                label.element.constraints.x = ((128f * config.scale - width) / 2f).px
                label.element.constraints.width = width.px
                label.element.constraints.height = size.px
            }
        }
    }

    private fun textWidth(line: String, size: Float, font: Font): Float =
        if (font.name == "Minecraft") line.width(size / mc.font.lineHeight)
        else NVGRenderer.textWidth(line.noControlCodes, size, font)

    private fun infoLines(): List<String> {
        val secrets = "§6Secrets: §b${Dungeon.secretCount}§f/§b${ScoreCalculation.secretsUntil300}§f/§e${ScoreCalculation.totalSecrets}"
        val crypts = "${if (Dungeon.cryptCount >= 5) "§a" else "§c"}C${Dungeon.cryptCount}"
        val bats = "${if (Dungeon.batKilled) "§a" else "§c"}B${Dungeon.batCount}"
        val score = Dungeon.score
        val deaths = "${if (Dungeon.deathCount == 0) "§a" else "§c"}${Dungeon.deathCount}"
        val mimic = "${if (Dungeon.mimicKilled) "§a" else "§c"}M §f| "
        val prince = "${if (Dungeon.princeKilled) "§a" else "§c"}P"
        return listOf(
            "$secrets    $crypts §f| $bats",
            "§eScore: ${ScoreCalculation.colour(score)}$score   §cDeaths: $deaths   $mimic$prince"
        )
    }

    private class RoomView(val element: Element) {
        var tiles: Set<RoomTile> = emptySet()
        var label: List<String>? = null
        var state: RoomState? = null
    }

    private data class OverlaySettings(
        val style: String = DungeonMap.checkmarkStyle.selected,
        val limitTextSize: Boolean = DungeonMap.limitRoomNameSize,
        val checkmarkScale: Float = DungeonMap.checkmarkScale,
    )

    private fun ElementScope<*>.renderDoor(config: MapConfig, door: OdonDoor): Element {
        val pos = door.placement
        val size = door.size
        val col = Colour.Animated(
            from = colour { door.colour.rgb },
            to = colour { door.colour.rgb.multiply(1.15f) }
        )
        val outlineCol = Colour.Animated(
            from = Colour.TRANSPARENT,
            to = Colour.WHITE.withAlpha(180)
        )
        return block(
            constrain(
                (pos.x * config.scale).px,
                (pos.z * config.scale).px,
                (size.x * config.scale).px,
                (size.z * config.scale).px
            ),
            colour = col
        ) {
            outline(outlineCol, thickness = 2.px)
            if (config.autoClear) {
                onClick {
                    if (Dungeon.isDead) return@onClick false
                    pathToDoor(door, InteractiveMap.faceDoorOnArrival)
                    true
                }
                onMouseEnterExit {
                    col.animate(0.15.seconds, style = Animation.Style.EaseOutQuint)
                    outlineCol.animate(0.15.seconds, style = Animation.Style.EaseOutQuint)
                }
            }
        }.element
    }

    private fun ElementScope<*>.renderTiles(config: MapConfig, room: OdonRoom) {
        val components = room.tiles
        if (components.isEmpty()) return

        val (scale, radius, _, _, _, _, _, autoClear) = config

        val c = {
            val base = room.colour
            if (autoClear && Dungeon.currentRoom == room) base.mix(InteractiveMap.roomInCol.withAlpha(255), InteractiveMap.roomInCol.alpha).rgb else base.rgb
        }

        val outlineCol = Colour.Animated(
            from = Colour.TRANSPARENT,
            to = Colour.WHITE.withAlpha(180)
        )

        val byPos = components.associateBy { it.placement }
        fun has(p: Vec2i, dx: Int, dz: Int) = byPos.containsKey(p.add(dx, dz))

        val lCorners = components.filter {
            val p = it.placement
            val (n, s, w, e) = listOf(has(p, 0, -20), has(p, 0, 20), has(p, -20, 0), has(p, 20, 0))
            val long = (n && s) || (w && e)
            val twoByTwo =
                (n && e && has(p, 20, -20)) ||
                        (n && w && has(p, -20, -20)) ||
                        (s && e && has(p, 20, 20)) ||
                        (s && w && has(p, -20, 20))

            // l corner: 2 connections, not a line, not a part of 2x2
            listOf(n, s, w, e).count { c -> c } == 2 && !long && !twoByTwo
        }.map { it.placement }.toSet()

        components.forEach { comp ->

            val col = Colour.Animated(
                from = colour { c() },
                to = colour { c().multiply(1.15f) }
            )

            val p = comp.placement
            val isL = p in lCorners

            val (n, s, w, e) = listOf(has(p, 0, -20), has(p, 0, 20), has(p, -20, 0), has(p, 20, 0))

            // all blocks start at 16x 16
            // if L corner don't touch
            // if next to L corner 4
            // if 2x2 or long 2
            fun gap(exists: Boolean, dx: Int, dz: Int) = when {
                !exists || isL -> 0f
                p.add(dx, dz) in lCorners -> 4f
                else -> 2f
            }

            val ng = gap(n, 0, -20)
            val sg = gap(s, 0, 20)
            val wg = gap(w, -20, 0)
            val eg = gap(e, 20, 0)

            val width = (16f + wg + eg) * scale
            val height = (16f + ng + sg) * scale

            block(
                constrain(((p.x - wg) * scale).px, ((p.z - ng) * scale).px, width.px, height.px),
                colour = col,
                radius = radius(
                    tl = if (n || w) 0 else radius,
                    bl = if (s || w) 0 else radius,
                    br = if (s || e) 0 else radius,
                    tr = if (n || e) 0 else radius
                )
            ) {
                if (autoClear) outline(outlineCol, 2.px)

                val mx = wg * scale + 0.5f
                val my = ng * scale + 0.5f
                val mw = width - eg * scale - 0.5f
                val mh = height - sg * scale - 0.5f

                fun mask(vert: Boolean, start: Float, end: Float, pos: Float) {
                    val constraints =
                        if (vert) constrain(pos.px, start.px, 4.px, (end - start).px)
                        else constrain(start.px, pos.px, (end - start).px, 4.px)
                    block(constraints, colour = col)
                }

                if (n) mask(
                    false,
                    start = if (has(p, -20, -20)) -1f else mx,
                    end = if (has(p, 20, -20)) width + 1f else mw,
                    pos = -2f
                )

                if (s) mask(
                    false,
                    start = if (has(p, -20, 20)) -1f else mx,
                    end = if (has(p, 20, 20)) width + 1f else mw,
                    pos = height - 2f
                )

                if (w) mask(
                    true,
                    start = if (has(p, -20, -20)) -1f else my,
                    end = if (has(p, -20, 20)) height + 1f else mh,
                    pos = -2f
                )

                if (e) mask(
                    true,
                    start = if (has(p, 20, -20)) -1f else my,
                    end = if (has(p, 20, 20)) height + 1f else mh,
                    pos = width - 2f
                )

                if (autoClear) {
                    onMouseEnterExit {
                        col.animate(0.15.seconds, style = Animation.Style.EaseOutQuint)
                        outlineCol.animate(0.15.seconds, style = Animation.Style.EaseOutQuint)
                    }
                    onClick {
                        if (Dungeon.isDead) return@onClick false
                        pathToRoom(room, comp, type = 0)
                        true
                    }
                    onClick(button = 1) {
                        if (Dungeon.isDead) return@onClick false
                        pathToRoom(room, comp, type = 2)
                        true
                    }
                }
            }
        }
    }

    private fun ElementScope<*>.renderName(config: MapConfig, room: OdonRoom, lines: List<String>) {
        if (room.tiles.isEmpty() || lines.isEmpty()) return
        val (scale, _, font, fontScale, shadow) = config
        val bounds = MapTextLayout.roomBounds(room.tiles.map { it.placement })
        val preferredSize = 3.6f * fontScale * scale
        val textSize = if (DungeonMap.limitRoomNameSize) {
            MapTextLayout.fitSize(lines, preferredSize, bounds.x * scale, bounds.z * scale) { line, size ->
                textWidth(line, size, font)
            }
        } else preferredSize
        val placement = room.textPlacement
        val centerX = (placement.x + 8f) * scale
        val top = (placement.z + 8f) * scale - lines.size * textSize / 2f

        lines.forEachIndexed { i, line ->
            val width = textWidth(line, textSize, font)
            text(line, font, colour { room.textColour.rgb }, at((centerX - width / 2f).px, (top + i * textSize).px), textSize.px) {
                element.constraints.width = width.px
                this.shadow = shadow
            }
        }
    }

    private fun ElementScope<*>.renderCheckmark(config: MapConfig, room: OdonRoom) {
        if (room.tiles.isEmpty() || room.data.type in setOf(RoomType.ENTRANCE, RoomType.FAIRY)) return
        val texture = when (room.data.state) {
            RoomState.GREEN -> "green_check.png"
            RoomState.CLEARED -> "white_check.png"
            RoomState.FAILED -> "cross.png"
            else -> return
        }
        val size = 10f * config.scale * DungeonMap.checkmarkScale
        val placement = room.textPlacement
        image(
            "dungeonmap/$texture".image(),
            constrain(((placement.x + 8f) * config.scale - size / 2f).px, ((placement.z + 8f) * config.scale - size / 2f).px, size.px, size.px)
        )
    }

    private val unlabelledTypes = setOf(RoomType.ENTRANCE, RoomType.FAIRY, RoomType.BLOOD)

    data class MapConfig(
        val scale: Float = 3f,
        val radius: Float = DungeonMap.roomRadius,
        val font: Font = DungeonMap.font.selected.get(),
        val fontScale: Float = DungeonMap.fontScale,
        val shadow: Boolean = DungeonMap.shadow,
        val icons: Boolean = true,
        val icon: IconConfig = IconConfig(),
        val autoClear: Boolean = false,
    ) {
        val height: Float get() = (128f + if (DungeonMap.showInfo) 15f else 0f) * scale
    }

    data class IconConfig(
        val scale: Float = DungeonMap.iconScale,
        val heads: Boolean = DungeonMap.showHeads,
        val ownHead: Boolean = DungeonMap.showOwnHead,
        val border: Boolean = DungeonMap.iconBorder,
        val borderColour: Colour = DungeonMap.iconBorderColour,
        val classColour: Boolean = DungeonMap.classColour,
        val thickness: Int = DungeonMap.iconBorderThickness,
        val name: Boolean = DungeonMap.showNames,
        val whenLeap: Boolean = DungeonMap.whenLeap,
        val nameScale: Float = DungeonMap.nameScale,
    )
}
