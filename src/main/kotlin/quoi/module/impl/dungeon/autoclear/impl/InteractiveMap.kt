package quoi.module.impl.dungeon.autoclear.impl

import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket
import quoi.api.abobaui.constraints.impl.positions.Centre
import quoi.api.abobaui.constraints.impl.size.Bounding
import quoi.api.abobaui.constraints.impl.size.Copying
import quoi.api.abobaui.dsl.*
import quoi.api.abobaui.elements.impl.Text.Companion.maxWidth
import quoi.api.abobaui.elements.impl.Text.Companion.shadow
import quoi.api.abobaui.elements.impl.Text.Companion.textSupplied
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.events.PacketEvent
import quoi.api.events.core.on
import quoi.api.input.Keybinds
import quoi.api.skyblock.SkyblockPlayer
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.odonscanning.MapRenderer
import quoi.api.skyblock.dungeon.odonscanning.MapRenderer.renderMap
import quoi.api.skyblock.location.Island
import quoi.api.skyblock.location.invoke
import quoi.module.Module
import quoi.module.impl.dungeon.autoclear.getLockedDoor
import quoi.module.impl.dungeon.autoclear.pathToDoor
import quoi.module.impl.dungeon.autoclear.pathToRoom
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.impl.KeybindComponent
import quoi.utils.ChatUtils.modMessage
import quoi.utils.commas
import quoi.utils.ui.screens.UIScreen.Companion.open

/**
 * TODO:
 *  room queuing while auto routing
 */
object InteractiveMap : Module(
    "Interactive Map",
    desc = "Automatically teleports to a specified target. Appearance is configured in Dungeon Map.",
    area = Island.Dungeon(inClear = true),
    tag = Tag.BETA
) {
    private val keepChunks by switch("Keep chunks loaded", true, desc = "Keeps the chunks loaded. Good for long distances.")
    private val closeOn by segmented("Close on", "Release", listOf("Release", "Repress"))
    val faceDoorOnArrival by switch("Face door on arrival", desc = "Faces the targeted door after arriving.")

    private val openKey: KeybindComponent = register(keybind("Open key"))
        .onPress {
            if (!enabled || !Dungeon.inClear || Dungeon.isDead) return@onPress
            if (mapOpen && closeOn.index == 1) {
                mc.gui.setScreen(null)
            } else if (mc.gui.screen() == null) {
                open(map(), background = false)
            }
        }
        .onRelease {
            if (!enabled || closeOn.index != 0 || !Dungeon.inClear || Dungeon.isDead) return@onRelease
            if (mapOpen) mc.gui.setScreen(null)
        }

    private val lockedKey: KeybindComponent = register(keybind("Locked door key", desc = "Teleports to the closest locked door"))
        .onPress {
            if (!enabled || !Dungeon.inClear || Dungeon.isDead) return@onPress
            val door = getLockedDoor() ?: return@onPress modMessage("No locked doors found.")
            pathToDoor(door, faceDoorOnArrival)
        }

    private val visuals by text("Visuals")
    private val scale by slider("Map scale", 5f, 1f, 10f, 0.5f).childOf(::visuals)
    private val showHelp by switch("Show help", true, desc = "Shows the controls below the map.").childOf(::visuals)
    val roomInCol by colourPicker("Highlight colour", Colour.GREY.withAlpha(0.5f), allowAlpha = true, desc = "Room the player currently in colour.").childOf(::visuals)

    val mapOpen: Boolean
        get() = mc.gui.screen()?.title?.string == "quoi clear map"

    init {
        on<PacketEvent.Received, ClientboundForgetLevelChunkPacket> {
            if (keepChunks) cancel()
        }
    }

    private fun map() = aboba("quoi clear map") {
        onClick {
            active && !Dungeon.isDead && AutoRoutes.skipNode()
        }
        onClick(button = 1) {
            if (!active || Dungeon.isDead) return@onClick false
            val room = Dungeon.currentRoom ?: return@onClick false
            pathToRoom(room, type = 1)
            true
        }

        val cfg = MapRenderer.MapConfig(
            scale = scale,
            autoClear = true
        )

        val infoScale = cfg.fontScale.coerceAtLeast(1f)
        val helpHeight = if (showHelp) 72f * infoScale + 52f else 0f
        val availableHeight = (mc.window.height - (28f * infoScale + 64f + helpHeight)).coerceAtLeast(128f)
        val mapConfig = cfg.copy(scale = cfg.scale * (availableHeight / cfg.height).coerceAtMost(1f))

        column(constrain(w = Copying - 32.px), gap = 12.px) {
            block(bounds(x = Centre, padding = 10.px), Colour.BLACK.withAlpha(0.8f), 6.radius()) {
                textSupplied(
                    supplier = { "${SkyblockPlayer.mana.commas()} / ${SkyblockPlayer.maxMana.commas()} Mana" },
                    font = cfg.font,
                    colour = Colour.MINECRAFT_AQUA,
                    pos = at(10.px, 10.px),
                    size = (28f * infoScale).px
                ).shadow = cfg.shadow
            }

            renderMap(config = mapConfig).element.constraints.x = Centre

            if (showHelp) block(
                constrain(x = Centre, w = (1240f * infoScale).px.coerceAtMost(Copying), h = Bounding + 12.px),
                Colour.BLACK.withAlpha(0.8f),
                6.radius()
            ) {
                column(constrain(16.px, 12.px, Copying - 32.px), gap = 8.px) {
                    val hintSize = (24f * infoScale).px
                    val cellWidth = (Copying - 24.px) / 2.px
                    val rightColumn = 50.percent + 12.px

                    listOf(
                        listOf("§bLeft click room / door§f: Travel", "§bRight click room§f: Route start"),
                        listOf("§bLeft click empty space§f: Continue route", "§bRight click empty space§f: Return to route start")
                    ).forEach { hints ->
                        group(constrain(w = Copying, h = hintSize)) {
                            hints.forEachIndexed { index, hint ->
                                text(
                                    string = hint,
                                    font = cfg.font,
                                    pos = at(if (index == 0) 0.px else rightColumn, Centre),
                                    size = hintSize
                                ) {
                                    maxWidth(cellWidth)
                                }.shadow = cfg.shadow
                            }
                        }
                    }

                    group(constrain(w = Copying, h = hintSize)) {
                        textSupplied(
                            supplier = {
                                if (lockedKey.value.key == Keybinds.KEY_NONE) "§7Locked door: Unbound"
                                else "§b${lockedKey.getKeyName()}§f: Closest locked door"
                            },
                            font = cfg.font,
                            pos = at(0.px, Centre),
                            size = hintSize
                        ).also {
                            it.maxWidth(cellWidth)
                            it.shadow = cfg.shadow
                        }

                        textSupplied(
                            supplier = {
                                val closeHint = if (openKey.value.key == Keybinds.KEY_NONE) "Esc"
                                else "${if (closeOn.index == 0) "Release " else ""}${openKey.getKeyName()} / Esc"
                                "§b$closeHint§f: Close map"
                            },
                            font = cfg.font,
                            pos = at(rightColumn, Centre),
                            size = hintSize
                        ).also {
                            it.maxWidth(cellWidth)
                            it.shadow = cfg.shadow
                        }
                    }
                }
            }
        }
    }
}
