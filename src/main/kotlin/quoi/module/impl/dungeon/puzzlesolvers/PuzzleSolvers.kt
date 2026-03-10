package quoi.module.impl.dungeon.puzzlesolvers

import net.minecraft.network.protocol.game.ClientboundSoundPacket
import quoi.api.colour.Colour
import quoi.api.events.*
import quoi.api.skyblock.Island
import quoi.api.skyblock.invoke
import quoi.module.Module
import quoi.module.settings.Setting.Companion.json
import quoi.module.settings.Setting.Companion.withDependency
import quoi.module.settings.impl.*

object PuzzleSolvers : Module(
    "Puzzle Solvers",
    area = Island.Dungeon(inClear = true)
) {
    private val fillDropdown by DropdownSetting("Ice fill").collapsible()
    private val fillSolver by BooleanSetting("Ice fill solver", desc = "Shows the solution for the ice fill puzzle.").withDependency(fillDropdown)
    private val fillColour by ColourSetting("Colour", Colour.MAGENTA, allowAlpha = true).json("Ice fill colour").withDependency(fillDropdown) { fillSolver }
    private val fillAuto by BooleanSetting("Auto", desc = "Automatically completes the ice fill puzzle.").json("Auto ice fill").withDependency(fillDropdown) { fillSolver }
    private val fillDelay by NumberSetting("Delay", 2, 1, 10, 1, unit = "t").json("Auto ice fill delay").withDependency(fillDropdown) { fillSolver && fillAuto }

    private val beamsDropdown by DropdownSetting("Creeper beams").collapsible()
    private val beamsSolver by BooleanSetting("Creeper beams solver", desc = "Shows the solution for the creeper beams puzzle.").withDependency(beamsDropdown)
    private val beamsAnnounce by BooleanSetting("Announce completion").withDependency(beamsDropdown) { beamsSolver }
    private val beamsTracer by BooleanSetting("Tracer").json("Beams tracer").withDependency(beamsDropdown) { beamsSolver }
    private val beamsStyle by SelectorSetting("Style", "Box", arrayListOf("Box", "Filled box"), desc = "Esp render style to be used.").json("Beams style").withDependency(beamsDropdown) { beamsSolver }
    private val beamsAlpha by NumberSetting("Colour alpha", 0.7f, 0f, 1f, 0.05f).json("Beams colour alpha").withDependency(beamsDropdown) { beamsSolver }
    private val beamsAuto by BooleanSetting("Auto").json("Auto beams").withDependency(beamsDropdown) { beamsSolver }


    private val bowDropdown by DropdownSetting("Bow settings").collapsible().withDependency { beamsSolver && beamsAuto }
    private val shootCd by NumberSetting("Shoot cooldown", 500L, 250L, 1000L, 50L, unit = "ms").withDependency(bowDropdown)
    private val missCd by NumberSetting("Miss cooldown", 550L, 300L, 1050L, 50L, unit = "ms").withDependency(bowDropdown)

    init {
        on<WorldEvent.Change> {
            IceFillSolver.reset()
            BeamsSolver.reset()
        }

        on<DungeonEvent.Room.Enter> {
            IceFillSolver.onRoomEnter(room)
            BeamsSolver.onRoomEnter(room)
        }

        on<RenderEvent.World> {
            if (fillSolver)  IceFillSolver.onRenderWorld(ctx, fillColour)
            if (beamsSolver) BeamsSolver.onRenderWorld(ctx, beamsStyle.selected, beamsTracer, beamsAlpha)
        }

        on<TickEvent.End> {
            if (fillSolver && fillAuto)   IceFillSolver.onTick(player, fillDelay)
            if (beamsSolver && beamsAuto) BeamsSolver.onTick(player, shootCd, missCd)
        }

        on<BlockUpdateEvent> {
            if (beamsSolver) BeamsSolver.onBlockChange(this@on, beamsAnnounce)
        }

        on<PacketEvent.Received, ClientboundSoundPacket> {
            if (beamsSolver && beamsAuto) BeamsSolver.onSound(packet)
        }
    }
}