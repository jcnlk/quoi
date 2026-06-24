package quoi.api.commands

import quoi.utils.center
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Style
import quoi.QuoiMod.mc
import quoi.QuoiMod.scope
import quoi.api.commands.internal.BaseCommand
import quoi.api.commands.internal.GreedyString
import quoi.api.skyblock.Island
import quoi.api.skyblock.Location
import quoi.api.skyblock.Location.currentArea
import quoi.api.skyblock.Location.currentServer
import quoi.api.skyblock.Location.inSkyblock
import quoi.api.skyblock.Location.subarea
import quoi.api.skyblock.SkyblockPlayer
import quoi.api.skyblock.SkyblockPlayer.InvincibilityType
import quoi.api.skyblock.SkyblockPlayer.Mask
import quoi.api.skyblock.dungeon.Dungeon
import quoi.module.ModuleManager
import quoi.module.impl.misc.ChatReplacements
import quoi.module.impl.misc.chat.Chat
import quoi.module.impl.render.ClickGui.clickGui
import quoi.module.impl.render.PlayerESP
import quoi.utils.ChatUtils.command
import quoi.utils.ChatUtils.literal
import quoi.utils.ChatUtils.modMessage
import quoi.utils.LegacyIdMapper
import quoi.utils.Scheduler.scheduleLoop
import quoi.utils.Scheduler.scheduleTask
import quoi.utils.Scheduler.wait
import quoi.utils.WorldUtils
import quoi.utils.WorldUtils.day
import quoi.utils.skyblock.player.MovementUtils.hold
import quoi.utils.skyblock.player.MovementUtils.isMoving
import quoi.utils.ticker
import quoi.utils.ui.hud.HudManager
import quoi.utils.ui.screens.UIScreen.Companion.open
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import quoi.api.events.TickEvent
import quoi.api.events.core.EventDispatcher
import quoi.api.events.core.EventListener
import quoi.api.events.core.until
import quoi.api.skyblock.dungeon.Dungeon.currentRoom
import quoi.module.impl.misc.chat.impl.CompactChat
import quoi.utils.StringUtils.capitaliseFirst
import quoi.utils.addVec
import quoi.utils.skyblock.PartyUtils
import quoi.utils.skyblock.player.RotationUtils.rotate
import java.net.URI
import kotlin.collections.sortedBy
import kotlinx.coroutines.launch

object QuoiCommand : EventListener {
    val command = BaseCommand("quoi", "requise") {
        open(clickGui)
    }

    val devCommand = BaseCommand("quoidev")

    private fun warpTicker(cmd: String) = ticker {
        action { command("warp $cmd") }
        action(80) { command("warp hub") }
        delay(80)
    }

    private fun antiAfkTicker(delay: Int) = ticker {
        action { mc.options.keyLeft.hold(1) }
        action(delay) { mc.options.keyRight.hold(1) }
        delay(delay)
    }

    init {
        with(devCommand) {
            "copy" { string: GreedyString ->
                mc.keyboardHandler.clipboard = string.string
                modMessage("Copied text to clipboard.")
            }

            "simulate" { message: GreedyString ->
                EventDispatcher.onPacketReceived(ClientboundSystemChatPacket(literal(message.string), false))
                modMessage("simulated: ${message.string}")
            }

            "resetinvincibility" {
                InvincibilityType.entries.forEach { it.reset() }
                modMessage("&aReset all invincibility cooldowns.")
            }.description("Resets every invincibility cooldown.")

            "deathticks" { count: Int ->
                if (count <= 0) return@invoke modMessage("&cProvide a positive number of death ticks.")

                modMessage("&eStarting $count death ticks.")
                scope.launch {
                    repeat(count) {
                        wait(60)

                        val proccingTypes = buildList {
                            when (SkyblockPlayer.currentMask) {
                                Mask.BONZO -> add(InvincibilityType.BONZO)
                                Mask.SPIRIT -> add(InvincibilityType.SPIRIT)
                                Mask.NONE -> Unit
                            }
                            if (SkyblockPlayer.currentPet.contains("phoenix", ignoreCase = true)) {
                                add(InvincibilityType.PHOENIX)
                            }
                        }.filter { it.currentCooldown <= 0 }

                        if (proccingTypes.isEmpty()) {
                            modMessage("&cYou died!")
                            return@launch
                        }

                        proccingTypes.forEach { type ->
                            val message = when (type) {
                                InvincibilityType.BONZO -> "Your Bonzo's Mask saved your life!"
                                InvincibilityType.SPIRIT -> "Second Wind Activated! Your Spirit Mask saved your life!"
                                InvincibilityType.PHOENIX -> "Your Phoenix Pet saved you from certain death!"
                            }
                            EventDispatcher.onPacketReceived(ClientboundSystemChatPacket(literal(message), false))
                        }
                    }
                }
            }.description("Simulates death ticks.")

            "currentroom" {
                currentRoom?.let { room ->
                    val player = mc.player!!
                    val currentComp = room.tiles.minByOrNull { comp ->
                        val dx = player.x - comp.x
                        val dz = player.z - comp.z
                        dx * dx + dz * dz
                    }

                    val componentsString = room.tiles.mapIndexed { index, comp ->
                        val curr = if (comp == currentComp) "&a->&f" else "   "
                        "$curr &7$index: ${comp.vec2} &7| &f${comp.core}"
                    }.joinToString("\n")


                    val msg = listOf(
                        "&e${room.data.name} &7(${room.data.type})",
                        "&7|&fState: &7${room.data.state}",
                        "&7|&fCorner: &7${room.clayPos.x}, ${room.clayPos.y}, ${room.clayPos.z}",
                        "&7|&fRotation: &7${room.rotation} (${room.rotation.deg})",
                        "&7|&fComponents:",
                        componentsString
                    ).joinToString("\n")

                    modMessage(msg, prefix = "")
                }
            }

            "relative" {
                mc.hitResult?.let {
                    if (it !is BlockHitResult) return@let
                    currentRoom?.getRelativeCoords(it.blockPos)?.let { vec2 ->
                        modMessage("Relative coords: ${vec2.x}, ${vec2.z}")
                    }
                    currentRoom?.getRelativeCoords(Vec3(it.blockPos))?.let { vec2 ->
                        modMessage("Relative coords: ${vec2.x}, ${vec2.z}")
                    }
                }
            }

//            "rooms" {
//                modMessage("Rooms: ${uniqueRooms.joinToString(", ") { it.name }}")
//            }

            "area" {
                modMessage("Area: $currentArea, Sub: $subarea, Server: $currentServer, Floor: ${Dungeon.floor?.name}")
            }

            "featurelist" { md: Boolean? ->
                val featureList = StringBuilder()

                for ((category, modulesInCategory) in ModuleManager.modules.groupBy { it.category }.entries) {
                    val categoryName = category.name.capitaliseFirst()

                    if (md == true) {
                        featureList.appendLine("<details>")
                        featureList.appendLine("<summary><b>$categoryName</b></summary>")
                        featureList.appendLine()
                    } else {
                        featureList.appendLine("# $categoryName")
                    }

                    for (module in modulesInCategory.sortedBy { it.name }) {
                        featureList.appendLine("- **${module.name}**")
                        if (module.desc.isNotEmpty()) featureList.appendLine("  - ${module.desc}")
                    }

                    if (md == true) {
                        featureList.appendLine()
                        featureList.appendLine("</details>")
                    }

                    featureList.appendLine()
                }

                mc.keyboardHandler.clipboard = featureList.toString()
                modMessage("Copied feature list to clipboard.")
            }

            "centre" {
                with(mc.player) {
                    this?.setPos(this.blockPosition().center.addVec(y = -0.5))
                }
            }

            "rotate" { yaw: Float, pitch: Float ->
                mc.player?.rotate(yaw, pitch)
            }

            "id" {
                val hit = mc.hitResult as? BlockHitResult
                    ?: return@invoke modMessage("&cYou are not looking at a block.")
                val state = mc.level?.getBlockState(hit.blockPos)
                    ?: return@invoke modMessage("&cNo world is loaded.")

                modMessage(LegacyIdMapper.getId(state), prefix = "")
            }

            "blockinfo" {
                val hit = mc.hitResult as? BlockHitResult
                    ?: return@invoke modMessage("&cYou are not looking at a block.")
                val state = mc.level?.getBlockState(hit.blockPos)
                    ?: return@invoke modMessage("&cNo world is loaded.")
                val name = BuiltInRegistries.BLOCK.getKey(state.block)
                val stateInfo = state.toString().substringAfter('[', "").let {
                    if (it.isEmpty()) "" else " [${it.removeSuffix("]")}]"
                }

                modMessage("$name (${LegacyIdMapper.getId(state)})$stateInfo", prefix = "")
            }
        }

        with(command) {
            "toggle" { moduleName: GreedyString ->
                val module = ModuleManager.getModuleByName(moduleName.string)
                module?.apply {
                    toggle()
                    toggleMessage()
                } ?: modMessage("Unknown module name: ${moduleName.string}")
            }.suggests { ModuleManager.modules.map { it.name } }.description("Toggles specified module.")

            "hud" { open(HudManager.editor()) }.description("Opens Hud editor.")
        }

        command.sub("findlobby") { area: String, criteria: String, value: String ->
            val island = Island.entries
                .firstOrNull { it.command != null && it.displayName.equals(area.replace("_", " "), true) }
                ?: return@sub modMessage("&cIncorrect area!")

            if (criteria !in setOf("day", "server", "player")) return@sub modMessage("&cInvalid criteria!")

            val intValue = if (criteria == "day") value.toIntOrNull()
                ?: return@sub modMessage("&cInvalid day number!") else null

            fun isMet(): Boolean = when (criteria) {
                "day" -> mc.level!!.day <= intValue!!
                "server" -> Location.currentServer.equals(value, true)
                "player" -> WorldUtils.players.any { it.profile.name.equals(value, true) }
                else -> false
            }

            var ticker = warpTicker(island.command!!)

            modMessage("Starting to look for $criteria $value")

            scheduleLoop {
                if (mc.player!!.isMoving) {
                    modMessage("Cancelling, you moved!")
                    it.cancel()
                    return@scheduleLoop
                }

                if (isMet() && currentArea.isArea(island)) {
                    modMessage("Found")
                    it.cancel()
                    return@scheduleLoop
                }

                if (ticker.tick()) ticker = warpTicker(island.command)
            }
        }.description("Finds lobby with specified criteria.")
        .requires("&cYou are not in skyblock!") { inSkyblock }
        .suggests("area") { Island.entries.filter { it.command != null }.map { it.displayName.replace(" ", "_") } }
        .suggests("criteria", "day", "server", "player")

        command.sub("antiafk") { delay: Int ->
            if (delay < 20) return@sub modMessage("&cThe delay is too low!")
            val headRot = mc.player!!.yHeadRot
            modMessage("Starting. Move your camera to cancel")

            var ticker = antiAfkTicker(delay)
            until<TickEvent.End> {
                if (mc.player!!.yHeadRot != headRot) {
                    modMessage("Cancelling, you moved your camera!")
                    true
                } else {
                    if (ticker.tick()) ticker = antiAfkTicker(delay)
                    false
                }
            }
        }.description("Prevents afk kick.").suggests("delay", "40")
    }

    fun init() {
        command.register()
        devCommand.register()

        BaseCommand("clearchat") {
            mc.gui.hud.chat.clearMessages(false)
            CompactChat.chatList.clear()
            modMessage("Cleared chat.")
        }.register()

        BaseCommand("lsb") {
            command("l")
            scheduleTask(10) { command("play sb") }
        }.register()

        BaseCommand("ld") {
            command("l")
            scheduleTask(10) { command("play sb") }
            scheduleTask(30) { command("warp dungeon_hub") }
        }.register()

        BaseCommand("ptr") {
            val target = PartyUtils.membersNoSelf.randomOrNull()
                ?: return@BaseCommand modMessage("&cParty empty!")
            command("p transfer $target")
        }.register()

        Floors.entries.forEach { floor ->
            BaseCommand(floor.name.lowercase()) {
                command("joininstance ${floor.instance()}")
            }.requires("&cYou are not in skyblock!") { inSkyblock }.register()
        }
    }

    private enum class Floors {
        F0,
        F1, F2, F3, F4, F5, F6, F7,
        M1, M2, M3, M4, M5, M6, M7;

        private val floors = listOf("one", "two", "three", "four", "five", "six", "seven")

        fun instance(): String {
            if (this == F0) return "catacombs_entrance"

            val adj = ordinal - 1

            return "${if (adj > 6) "master_" else ""}catacombs_floor_${floors[adj % 7]}"
        }
    }
}
