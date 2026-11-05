package quoi.module.impl.dungeon.puzzlesolvers.impl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import quoi.QuoiMod.scope
import quoi.api.colour.Colour
import quoi.api.events.DungeonEvent
import quoi.api.events.PacketEvent
import quoi.api.events.RenderEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.Event
import quoi.api.events.core.await
import quoi.api.events.core.on
import quoi.api.events.core.onAsync
import quoi.api.events.core.removeAsyncScope
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonRoom
import quoi.module.impl.dungeon.autoclear.executor.ClearExecutor
import quoi.module.impl.dungeon.puzzlesolvers.PuzzleSolvers
import quoi.module.impl.dungeon.puzzlesolvers.Repositionable
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.group.SettingGroup
import quoi.utils.Ticker
import quoi.utils.WorldUtils.state
import quoi.utils.blockPos
import quoi.utils.equalsOneOf
import quoi.utils.getDirection
import quoi.utils.render.drawLine
import quoi.utils.skyblock.item.ItemUtils.skyblockId
import quoi.utils.skyblock.player.MovementUtils.isMoving
import quoi.utils.skyblock.player.PlayerUtils.useItem

object IceFillSolver : SettingGroup(PuzzleSolvers, "Ice fill"), Repositionable {
    private val solver by switch("Solver", desc = "Shows the solution for the ice fill puzzle.")
    private val colour by colourPicker("Colour", Colour.MAGENTA, allowAlpha = true).childOf(::solver)
    private val auto by switch("Auto", desc = "Automatically completes the ice fill puzzle.")
    private val delay by slider("Delay", 2, 0, 10, 1, desc = "Ticks between teleports. At 0, waits only for the server to confirm each teleport.", unit = "t").childOf(::auto)
    private val reposition by switch("Auto reposition").childOf(::auto)

    private var currentPatterns: List<Vec3> = emptyList()
    private var patternRoom: OdonRoom? = null
    private var solveJob: Job? = null
    private var scanTicks = 0

    override var repositionTicker: Ticker? = null

    init {
        on<RenderEvent.World> {
            if (!solver || Dungeon.currentRoom !== patternRoom) return@on
            ctx.drawLine(currentPatterns, colour, true)
        }

        on<DungeonEvent.Room.Enter> {
            if (room !== patternRoom) reset(room?.takeIf { it.name == "Ice Fill" })
        }

        on<TickEvent.End> {
            if (!solver && !auto) return@on
            val room = Dungeon.currentRoom ?: return@on
            if (room !== patternRoom) reset(room)
            if (currentPatterns.isEmpty() && solveJob?.isActive != true && scanTicks-- <= 0) {
                scanTicks = 10
                val spaces = IceFillPathfinder.scan { pos ->
                    val realPos = room.getRealCoords(pos)
                    val chunk = level.chunkSource.getChunk(realPos.x shr 4, realPos.z shr 4, false) ?: return@scan null
                    val floor = chunk.getBlockState(realPos.below()).block
                    floor.equalsOneOf(Blocks.ICE, Blocks.PACKED_ICE) && chunk.getBlockState(realPos).isAir
                } ?: return@on
                if (spaces.isNotEmpty()) solveJob = scope.launch(Dispatchers.Default) {
                    val path = IceFillPathfinder.solve(spaces) ?: return@launch
                    mc.submit {
                        if (isActive && patternRoom === room) currentPatterns = path.map { room.getRealCoords(it) }
                    }.join()
                }
            }
        }

        onAsync<TickEvent.End> {
            val room = Dungeon.currentRoom ?: return@onAsync
            do {
                if (currentPatterns.isEmpty() || Dungeon.currentRoom !== room || !inEnvironment()) return@onAsync
                if (!auto || ClearExecutor.active || PuzzleSolvers.screenBlocksAuto) return@onAsync
                if (room.getRealCoords(BlockPos(15, 71, 26)).state.block == Blocks.PACKED_ICE) return@onAsync

                repositionTicker?.let {
                    if (it.tick()) repositionTicker = null
                    return@onAsync
                }

                if (reposition && player.y !in (69.5..72.5) ) { // untested
                    if (player.isMoving) return@onAsync
                    val spot = getBlock()
                    if (spot != null) {
                        reposition(spot, bow = false, stand = true, awaitStand = true)
                        return@onAsync
                    }
                }
                if (player.mainHandItem.skyblockId?.equalsOneOf("ASPECT_OF_THE_VOID", "ASPECT_OF_THE_END") == false) return@onAsync

                val index = currentPatterns.indexOfFirst {
                    player.x == it.x && (player.y + 0.1) == it.y && player.z == it.z
                }
                if (index == -1 || index >= currentPatterns.lastIndex) {
                    lastIndex = -1
                    return@onAsync
                }

                if (delay == 0 || lastIndex == -1 || index > lastIndex) {
                    lastIndex = index
                    ticks = 0
                }

                if (lastIndex >= currentPatterns.lastIndex) return@onAsync

                if (delay == 0 || ++ticks == delay) {
                    val current = currentPatterns[lastIndex]
                    val next = currentPatterns[lastIndex + 1]
                    val from = Vec3(current.x, current.y - 0.1 + player.eyeHeight, current.z)
                    player.useItem(getDirection(from, next))
                    if (delay == 0) await<PacketEvent.ReceivedPost, ClientboundPlayerPositionPacket> {
                        player.x == next.x && player.y + 0.1 == next.y && player.z == next.z
                    } else lastIndex++
                    ticks = 0
                }
            } while (delay == 0)
        }

        on<WorldEvent.Change> {
            reset()
        }
    }

    override fun shouldHandle(event: Event): Boolean {
        if (event is WorldEvent.Change) return true
        if (!super.shouldHandle(event)) return false

        if (event is DungeonEvent.Room.Enter) return true

        return Dungeon.currentRoom?.name == "Ice Fill"
    }

    private var ticks = 0
    private var lastIndex = -1

    private fun getBlock(): BlockPos? = currentPatterns.firstNotNullOfOrNull { vec ->
        vec.blockPos.below().takeIf { it.state.block == Blocks.ICE }
    }

    private fun reset(room: OdonRoom? = null) {
        solveJob?.cancel()
        solveJob = null
        removeAsyncScope()
        currentPatterns = emptyList()
        patternRoom = room
        scanTicks = 0
        ticks = 0
        lastIndex = -1
        repositionTicker = null
    }
}