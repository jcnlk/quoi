package quoi.module.impl.render

import net.minecraft.world.phys.Vec3
import quoi.api.colour.Colour
import quoi.api.events.RenderEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.on
import quoi.module.Module
import quoi.utils.render.drawLine

// based on: https://github.com/Noamm9/CatgirlAddons/blob/ac425312ab5437ef535fdca1eecf284ab2510925/src/main/kotlin/catgirlroutes/module/impl/render/Trail.kt
object Trail : Module(
    "Trail",
    desc = "Draws a trail behind you as you move."
) {
    private val ticks by slider("Ticks", 100, 1, 400, unit = "t", desc = "Length of the trail in client ticks. Pauses while stationary if Stay is enabled.")
    private val colour by colourPicker("Colour", Colour.MINECRAFT_AQUA, desc = "Colour of the trail.")
    private val thickness by slider("Thickness", 3f, 1f, 5f, 0.1f, desc = "Width of the trail.")
    private val stay by switch("Stay", true, desc = "Keeps the trail while you stand still.")
    private val phase by switch("Phase", desc = "Shows the trail through blocks.")

    private val history = TrailHistory()

    @Suppress("unused")
    private val resetKey by keybind("Reset trail", desc = "Clears the current trail when pressed in game.")
        .onPress {
            if (enabled && mc.gui.screen() == null) history.clear()
        }

    init {
        on<TickEvent.End> {
            history.record(player.position().add(0.0, 0.05, 0.0), ticks, stay)
        }

        on<RenderEvent.World> {
            val points = history.points.toList()
            if (points.size < 2) return@on

            ctx.drawLine(points, colour, depth = !phase, thickness = thickness)
        }

        on<WorldEvent.Change> { history.clear() }
    }

    override fun onDisable() {
        history.clear()
    }
}

internal class TrailHistory {
    val points: Collection<Vec3>
        field = ArrayDeque<Vec3>()

    fun record(position: Vec3, ticks: Int, stay: Boolean) {
        if (!stay || points.lastOrNull() != position) points.addLast(position)
        while (points.size > ticks + 1) points.removeFirst()
    }

    fun clear() {
        points.clear()
    }
}
