package quoi.module.impl.dungeon

import quoi.api.events.*
import quoi.api.skyblock.Island
import quoi.api.skyblock.dungeon.Dungeon.currentRoom
import quoi.api.skyblock.dungeon.Dungeon.isDead
import quoi.module.Module
import quoi.utils.Ticker
import quoi.utils.WorldUtils.state
import quoi.utils.getDirection
import quoi.utils.skyblock.player.SwapManager
import quoi.utils.ticker
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.world.phys.Vec3
import quoi.api.skyblock.dungeon.odonscanning.ScanUtils
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonRoom
import quoi.api.skyblock.dungeon.odonscanning.tiles.RoomType
import quoi.utils.ChatUtils.modMessage
import quoi.utils.Scheduler.scheduleTask
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.Vec2
import quoi.utils.distanceTo2D
import quoi.utils.getEtherwarpDirection
import quoi.utils.skyblock.player.PlayerUtils
import quoi.utils.skyblock.player.PlayerUtils.at
import quoi.utils.skyblock.player.PlayerUtils.rotate
import quoi.utils.skyblock.player.PlayerUtils.useItem

object AutoBloodRush : Module( // inconsis
    "Auto Blood Rush",
    desc = "Automatically blood rushes.",
    area = Island.Dungeon
) {
    private val debug by switch("Debug").hide()

    private val mid = Vec3(-104.0, 0.0, -104.0)

    private var bloodCoords: Vec3? = null
    private var tickerThing: Ticker? = null

    private var tpsReceived = 0
    private var tpsAmount = 0
    private var doneTeleporting = false

    private var firstScan = true
    private var goingMid = false

    private val etherBlock: BlockPos
        get() {
            val room = currentRoom!!

            val relativePos = if (!room.getRealCoords(BlockPos(15, 73, 24)).state.isAir) {
                BlockPos(2, 82, 18)
            } else {
                when (room.rotation.deg) {
                    0, 90 -> BlockPos(2, 81, 15)
                    else -> BlockPos(2, 81, 16)
                }
            }

            return room.getRealCoords(relativePos)
        }

    init {
//        command.sub("br") { stage: Int ->
//            val a = when (stage) {
//                1 -> position()
//                2 -> roof()
//                3 -> br()
//                else -> return@sub
//            }
//            tickerThing = a
//        }.requires { enabled && debug }

        on<TickEvent.End> {
            if (isDead) return@on

            tickerThing?.let {
                if (it.tick()) tickerThing = null
            }
        }

        on<ChatEvent.Packet> {
            if (debug) return@on
            if (currentRoom?.name != "Entrance") return@on
            if (bloodCoords == null || player.y != 99.0) return@on
            when (message.noControlCodes) {
//                "Starting in 4 seconds." -> tickerThing = leaf()
                "[NPC] Mort: Here, I found this map when I first entered the dungeon." -> tickerThing = br()
            }
        }

        on<PacketEvent.Received, ClientboundPlayerPositionPacket> {
            if (goingMid) {
                if (packet.change.position.y in 75.0..77.0) {
                    goingMid = false
                    scheduleTask(3) {
                        tickerThing = ticker {
                            addSteps(position())
                            addSteps(roof())
                            delay(10)
                        }
                    }
                }
            }

            if (tickerThing == null || tpsAmount == 0) return@on
            if (++tpsReceived == tpsAmount) {
                doneTeleporting = true
                tpsReceived = 0
                tpsAmount = 0
            }
        }

        on<DungeonEvent.Room.Scan> {
            if (room.data.type == RoomType.BLOOD) {
                bloodCoords = room.getRealCoords(Vec3(15.0, 70.0, 15.0))
                modMessage("Found blood at $bloodCoords")
            }

            if (debug || !firstScan) return@on

            if (!player.onGround()) return@on

            firstScan = false
            tickerThing = ticker {
                addSteps(position())
                addSteps(roof())
                delay(10)
                action {
                    if (bloodCoords == null) {
                        goingMid = true
                        tickerThing = br()
                    }
                }
            }
        }

        on<WorldEvent.Change> {
            tickerThing = null
            bloodCoords = null

            doneTeleporting = false
            tpsReceived = 0
            tpsAmount = 0

            firstScan = true
            goingMid = false
        }
    }

    private fun position() = ticker {
        val spot = etherBlock
        action {
            if (currentRoom?.name != "Entrance" || !SwapManager.swapById("ASPECT_OF_THE_VOID").success) {
                tickerThing = null
            }

            mc.options.keyShift.isDown = true
        }
        delay(1) // idk
        action {
            val dir = getEtherwarpDirection(spot)
            if (dir == null) {
                tickerThing = null
                return@action
            }
            player.useItem(dir)
        }
        await {
            if (player.at(spot)) {
                mc.options.keyShift.isDown = false
                return@await true
            }
            false
        }
    }

    private fun roof() = ticker {
        action {
            if (!SwapManager.swapByName("pearl").success) {
                tickerThing = null
            } else {
                tpsReceived = 0
                tpsAmount = 4
                doneTeleporting = false
            }
        }
        repeat(4) { // split otherwise it gets fucked
            action { PlayerUtils.interact() }
        }

        await { doneTeleporting() }

        action {
            tpsReceived = 0
            tpsAmount = 4
            doneTeleporting = false
        }

        repeat(4) {
            action { PlayerUtils.interact() }
        }

        await { doneTeleporting() }

        await {
            if (player.blockPosition().above(1).state.isAir) {
                SwapManager.swapById("ASPECT_OF_THE_VOID").success
                return@await true
            }

            if (tpsAmount == 0 || doneTeleporting()) {
//                modMessage("I AM A NI")
                tpsReceived = 0
                tpsAmount = 1
                doneTeleporting = false
                PlayerUtils.interact()
            }

            false
        }
        delay(1) // dk
    }

    private fun br() = ticker {
        action {
            if (player.y < 95 || !SwapManager.swapById("ASPECT_OF_THE_VOID").success) {
                tickerThing = null
                return@action
            }
            mc.options.keyShift.isDown = false
        }

        action(1) {
            val yaw = getFreeDirection(currentRoom!!)
                ?: run { tickerThing = null; return@action }

            val target = bloodCoords ?: mid

            val edgeTimes = 4
            val moved = edgeTimes * 12.0
            val tx = player.x + if (yaw == 90f) -moved else if (yaw == -90f) moved else 0.0
            val tz = player.z + if (yaw == 180f) -moved else if (yaw == 0f) moved else 0.0
            val theoreticalPos = Vec3(tx, player.y, tz)

            val bloodDir = getDirection(theoreticalPos, target)
            val bloodTimes = (theoreticalPos.distanceTo2D(target) / 12).toInt()

            tpsReceived = 0
            tpsAmount = 3
            doneTeleporting = false

            repeat(edgeTimes) { player.useItem(yaw, 0) }
            repeat(8) { player.useItem(0, 90) }
            repeat(bloodTimes) { player.useItem(bloodDir.yaw, 0) }

            if (bloodCoords != null) {
                repeat(7) { player.useItem(0, -90) }
            } else {
                tickerThing = null
            }
        }

        await { doneTeleporting() } // idk

        action {
            SwapManager.swapByName("pearl")
            player.rotate(0, -90)
        }

        repeat(2) {
            action { PlayerUtils.interact() }
        }

        action {
            player.rotate(0, 45)
            PlayerUtils.interact()
        }
    }

    private fun doneTeleporting(): Boolean {
        if (doneTeleporting) {
            doneTeleporting = false
            return true
        }
        return false
    }

    private fun getFreeDirection(entrance: OdonRoom): Float? {
        val directions = mapOf(
            (0 to -32) to 180f,
            (0 to 32) to 0f,
            (-32 to 0) to 90f,
            (32 to 0) to -90f
        )

        val yaw = directions.entries.firstOrNull { (offset, _) ->
            val (dx, dz) = offset

            entrance.roomComponents.none { comp ->

                val vec = Vec2(comp.x + dx, comp.z + dz)

                ScanUtils.scannedRooms.any { room ->
                    room.roomComponents.any { it.vec2 == vec }
                }
            }
        }?.value ?: return null

        return yaw
    }
}