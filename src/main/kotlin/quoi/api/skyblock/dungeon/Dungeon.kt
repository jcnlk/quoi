package quoi.api.skyblock.dungeon

import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.*
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.monster.zombie.Zombie
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LeverBlock
import net.minecraft.world.level.block.SkullBlock
import net.minecraft.world.level.block.entity.SkullBlockEntity
import net.minecraft.world.level.block.state.BlockState
import quoi.annotations.Init
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.events.DungeonEvent
import quoi.api.events.PacketEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.EventListener
import quoi.api.events.core.on
import quoi.api.skyblock.dungeon.enums.*
import quoi.api.skyblock.dungeon.odonscanning.ScanUtils
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonRoom
import quoi.api.skyblock.dungeon.odonscanning.tiles.RoomType
import quoi.api.skyblock.location.Island
import quoi.api.skyblock.location.Location
import quoi.module.impl.dungeon.LeapMenu
import quoi.module.impl.render.clickgui.ClickGui
import quoi.utils.*
import quoi.utils.EntityUtils.getEntities
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.skyblock.PartyUtils
import java.util.*
import kotlin.math.floor

/**
 * modified OdinFabric (BSD 3-Clause)
 * copyright (c) 2025-2026 odtheking
 * original: https://github.com/odtheking/OdinFabric/blob/main/src/main/kotlin/com/odtheking/odin/utils/skyblock/dungeon/DungeonUtils.kt
 *           https://github.com/odtheking/OdinFabric/blob/main/src/main/kotlin/com/odtheking/odin/utils/skyblock/dungeon/DungeonListener.kt
 */
@Init
@Suppress("unused")
object Dungeon : EventListener, Shortcuts {

    inline val inDungeons: Boolean
        get() = Location.currentArea.isArea(Island.Dungeon)

    inline val inClear: Boolean
        get() = inDungeons && inGame && !inBoss

    var hasStarted = false
        private set

    var floor: Floor? = null
        private set

    inline val inBoss: Boolean
        get() = inGame && getBoss()

    var inTerminal: Boolean = false
        private set

    inline val secretCount: Int
        get() = dungeonStats.secretsFound

    inline val secretPercentage: Float
        get() = dungeonStats.secretsPercent

    inline val deathCount: Int
        get() = dungeonStats.deaths

    inline val cryptCount: Int
        get() = dungeonStats.crypts

    inline val completedRoomCount: Int
        get() = dungeonStats.completedRooms

    inline val percentCleared: Int
        get() = dungeonStats.percentCleared

    inline val totalRooms: Int
        get() = if (completedRoomCount == 0 || percentCleared == 0) 0 else floor((completedRoomCount / (percentCleared * 0.01).toFloat()) + 0.4).toInt()

    var puzzles = ArrayList<Puzzle>()
        private set

    inline val puzzleCount: Int
        get() = dungeonStats.puzzleCount

    var dungeonTeammates: ArrayList<DungeonPlayer> = ArrayList(5)
        private set

    var dungeonTeammatesNoSelf: List<DungeonPlayer> = ArrayList(4)
        private set

    val allTeammates: List<String>
        get() = (dungeonTeammates.map { it.name } + PartyUtils.members).distinct()

    val allTeammatesNoSelf: List<String>
        get() = allTeammates.filter { it != player.name.string }

    var leapTeammates: List<DungeonPlayer> = ArrayList(4)
        private set

    inline val currentDungeonPlayer: DungeonPlayer
        get() = dungeonTeammates.find { it.name == player.name.string } ?: DungeonPlayer(player.name.string, DungeonClass.Unknown, 0, null)

    inline val isDead: Boolean
        get() = currentDungeonPlayer.isDead

    inline val doorOpener: String
        get() = dungeonStats.doorOpener

    inline val mimicKilled: Boolean
        get() = dungeonStats.mimicKilled

    inline val princeKilled: Boolean
        get() = dungeonStats.princeKilled

    inline val batKilled: Boolean
        get() = dungeonStats.batKilled

    val batCount: Int
        get() = batKillers.size

    inline val currentRoom: OdonRoom?
        get() = ScanUtils.currentRoom

    val isPaul: Boolean
        get() = ScoreCalculation.isPaul

    inline val bloodDone: Boolean
        get() = dungeonStats.bloodDone

    inline val bloodOpen: Boolean
        get() = dungeonStats.bloodOpen

    inline val score: Int
        get() = ScoreCalculation.score.total

    inline val warpCooldown: Long
        get() = (enterTime - System.currentTimeMillis()).coerceAtLeast(0L)

    var enterTime = 0L
        private set

    var dungeonStats = DungeonStats()
        private set

    var deathTick = -1
        private set

    private var expectingBloodUpdate = false
    private var chatDeaths = 0
    private val batKillers = mutableSetOf<String>()

    init {
        ScanUtils.init()

        on<DungeonEvent.Start> {
            hasStarted = true
            ScoreCalculation.updateMayor()
        }

        on<DungeonEvent.Enter> { ScoreCalculation.updateMayor() }

        on<WorldEvent.Change> {
            hasStarted = false
            Blessing.entries.forEach { it.reset() }
            dungeonTeammatesNoSelf = emptyList()
            dungeonStats = DungeonStats()
            deathTick = -1
            expectingBloodUpdate = false
            chatDeaths = 0
            batKillers.clear()
            leapTeammates = emptyList()
            dungeonTeammates.clear()
            Puzzle.entries.forEach { it.status = null }
            puzzles.clear()
            floor = if (ClickGui.forceDungeons) ClickGui.dungeonFloor.selected
            else if (Location.onZapto) Floor.F7
            else null
        }

        on<TickEvent.Server> {
            if (!inClear || deathTick < 0) return@on
            if (deathTick == 0) deathTick = 40
            deathTick--
        }

        on<PacketEvent.ReceivedPost, ClientboundPlayerInfoUpdatePacket> {
            if (!inDungeons) return@on
            if (packet.actions().none {
                    it.equalsOneOf(
                        ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME,
                        ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER
                    )
                }) return@on
            val tabListEntries = packet.entries()
                .mapNotNull { it.displayName }
                .ifEmpty { return@on }

            val stringEntries = tabListEntries.map { it.string.noControlCodes }
            val colouredEntries = tabListEntries.map { it.string.noControlCodes to Colour.RGB(it.siblings.lastOrNull()?.style?.color?.value ?: Colour.WHITE.rgb).withAlpha(1.0f) }

            updateDungeonTeammates(colouredEntries)
            updateDungeonStats(stringEntries)
            getDungeonPuzzles(stringEntries)
        }

        on<PacketEvent.ReceivedPost> {
            with(packet) {
                when (this) {
                    is ClientboundSetPlayerTeamPacket -> {
                        val team = parameters.orElse(null) ?: return@on

                        val text = team.playerPrefix.string.noControlCodes + team.playerSuffix.string.noControlCodes

                        floorRegex.find(text)?.groupValues?.get(1)?.let {
                            val detectedFloor = Floor.valueOf(it)
                            if (floor != detectedFloor) {
                                floor = detectedFloor
                                DungeonEvent.Enter(detectedFloor).post()
                            }
                        }

                        clearedRegex.find(text)?.groupValues?.get(1)?.toIntOrNull()?.let {
                            if (dungeonStats.percentCleared != it && expectingBloodUpdate) dungeonStats.bloodDone = true
                            dungeonStats.percentCleared = it
                        }
                        if (inDungeons) updateElapsedTime(text)
                        dungeonTeammates.find { it.name == player.name.string }?.apply {
                            isDead = player.inventory.getItem(0).displayName.string.contains("Haunt")
                        }
                    }

                    is ClientboundTabListPacket -> {
                        Blessing.entries.forEach { blessing ->
                            blessing.regex.find(footer.string)
                                ?.let { romanToInt(it.groupValues[1]) }
                                ?.let { blessing.current = it }
                        }
                    }

                    is ClientboundSystemChatPacket -> {
                        if (overlay()) return@on
                        val message = content.string.noControlCodes
                        if (expectingBloodRegex.matches(message)) expectingBloodUpdate = true
                        if (enterRegex.matches(message) && warpCooldown == 0L) enterTime = System.currentTimeMillis() + 30_000L
                        doorOpenRegex.matchEntire(message)?.let { match ->
                            val opener = match.groupValues[1]
                            dungeonStats.doorOpener = opener
                            DungeonEvent.DoorOpen(opener).post()
                        }
                        deathRegex.matchEntire(message)?.let { match ->
                            if (!inDungeons || "reconnected" in message) return@let
                            chatDeaths++
                            dungeonStats.deaths = maxOf(dungeonStats.deaths, chatDeaths)
                            dungeonTeammates.find { teammate ->
                                teammate.name == (match.groupValues[1].takeUnless { it.isEmpty() || it == "You" } ?: player.name.string)
                            }?.let { it.deaths++ }
                        }

                        if (inDungeons) updateBonusScore(message)

                        when (message) {
                            "[NPC] Mort: Here, I found this map when I first entered the dungeon." -> if (!hasStarted) DungeonEvent.Start().post()
                            "The BLOOD DOOR has been opened!" -> dungeonStats.bloodOpen = true
                        }

                        when (partyMessageRegex.matchEntire(message)?.groups?.get("message")?.value?.lowercase()) {
                            "blaze done!", "blaze done", "blaze puzzle solved!" ->
                                puzzles.find { it == Puzzle.BLAZE }.let { it?.status = PuzzleStatus.Completed }
                        }
                    }

                    is ClientboundEntityEventPacket -> {
                        if (inClear && (floor?.floorNumber ?: 0) >= 6 && eventId.toInt() == 3 &&
                            (getEntity(level) as? Zombie)?.isBaby == true
                        ) dungeonStats.mimicKilled = true
                    }

                    is ClientboundSetTimePacket -> {
                        if (inClear) deathTick = 40 - (gameTime % 40).toInt()
                    }

                }
            }
        }

        on<PacketEvent.Received> {
            when (val packet = packet) {
                is ClientboundOpenScreenPacket -> inTerminal = terminalTitles.any { packet.title.string.contains(it) }
                is ClientboundContainerClosePacket -> inTerminal = false
            }
        }

        on<PacketEvent.Sent> {
            if (packet is ServerboundContainerClosePacket) inTerminal = false
        }
    }

    /**
     * Checks if the current dungeon floor number matches any of the specified options.
     *
     * @param options The floor number options to compare with the current dungeon floor.
     * @return `true` if the current dungeon floor matches any of the specified options, otherwise `false`.
     */
    fun isFloor(vararg options: Int): Boolean {
        return floor?.floorNumber?.let { it in options } ?: false
    }

    fun getMageCooldownMultiplier(): Double {
        return if (currentDungeonPlayer.clazz != DungeonClass.Mage) 1.0
        else 1 - 0.25 - (floor(currentDungeonPlayer.clazzLvl / 2.0) / 100) * if (dungeonTeammates.count { it.clazz == DungeonClass.Mage } == 1) 2 else 1
    }

    private val tablistRegex = Regex("^\\[(\\d+)] (?:\\[\\w+] )*(\\w+) .*?\\((\\w+)(?: (\\w+))*\\)$")

    private fun getDungeonTeammates(previousTeammates: ArrayList<DungeonPlayer>, tabList: List<Pair<String, Colour>>): ArrayList<DungeonPlayer> {
        for ((line, colour) in tabList) {
            val (_, name, clazz, clazzLevel) = tablistRegex.find(line)?.destructured ?: continue

            previousTeammates.find { it.name == name }?.let { player -> player.isDead = clazz == "DEAD" }
                ?: run {
                    val player = connection.getPlayerInfo(name) ?: continue
                    previousTeammates.add(
                        DungeonPlayer(
                            name = name,
                            clazz = DungeonClass.entries.find { it.name == clazz } ?: continue,
                            clazzLvl = romanToInt(clazzLevel) ?: continue,
                            playerSkin = player.skin,
                            colour = colour
                        )
                    )
                }
        }
        return previousTeammates
    }

    private val WITHER_ESSENCE_IDS = UUID.fromString("2865274b-3097-394e-8149-ec629c72d850")
    private val REDSTONE_KEY = UUID.fromString("fed95410-aba1-39df-9b95-1d4f361eb66e")

    fun isWitherEssence(id: UUID?): Boolean = id == WITHER_ESSENCE_IDS
    fun isRedstoneKey(id: UUID?): Boolean = id == REDSTONE_KEY

    /**
     * Determines whether a given block state and position represent a secret location.
     *
     * This function checks if the specified block state and position correspond to a secret location based on certain criteria.
     * It considers blocks such as chests, trapped chests, and levers as well as player skulls with a specific player profile ID.
     *
     * @param state The block state to be evaluated for secrecy.
     * @param pos The position (BlockPos) of the block in the world.
     * @return `true` if the specified block state and position indicate a secret location, otherwise `false`.
     */
    fun isSecret(state: BlockState, pos: BlockPos): Boolean {
        return when {
            state.block.equalsOneOf(Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.LEVER) -> true
            state.block is SkullBlock ->
                (level.getBlockEntity(pos) as? SkullBlockEntity)?.ownerProfile?.partialProfile()?.id
                    ?.let { isWitherEssence(it) || isRedstoneKey(it) } ?: false

            else -> false
        }
    }

    fun isBossLever(pos: BlockPos, state: BlockState): Boolean {
        if (state.block != Blocks.LEVER) return false
        val p3Lever = pos in bossLevers && getEntities<ArmorStand>(pos.above().vec3.aabb(1.5)) { it.displayName.string == "Not Activated" }.isNotEmpty()
        val deviceLever = pos in deviceLevers && state.hasProperty(LeverBlock.POWERED) && !state.getValue(LeverBlock.POWERED)
        val extraDeviceLever = pos == EXTRA_DEVICE_LEVER && !deviceLever && getEntities<ArmorStand>(pos.vec3.aabb(2.0)) { it.displayName.string == "Inactive" }.isNotEmpty()
        return p3Lever || deviceLever || extraDeviceLever
    }

    fun isBossLever(pos: BlockPos): Boolean = pos in bossLevers || pos in deviceLevers || pos == EXTRA_DEVICE_LEVER

    fun isProtectedBlock(pos: BlockPos, allowInteractions: Boolean = false): Boolean {
        val state = level.getBlockState(pos)
        val blockEntity = level.getBlockEntity(pos)

        if (allowInteractions) {
            if (inBoss && state.block.equalsOneOf(Blocks.LEVER, Blocks.STONE_BUTTON)) return false
            if (blockEntity is SkullBlockEntity && isRedstoneKey(blockEntity.ownerProfile?.partialProfile()?.id)) return false
        }

        return state.block in blacklistedDBBlocks
    }

    fun canInstaMine(pos: BlockPos): Boolean {
        if (currentRoom?.data?.type.equalsOneOf(RoomType.PUZZLE, RoomType.FAIRY)) return false
        val block = level.getBlockState(pos).block
        return block !in blacklistedDBBlocks && block != Blocks.OBSIDIAN
    }

    fun getBoss(): Boolean = with(player) {
        when (floor?.floorNumber) {
            1 -> x > -71 && z > -39
            in 2..4 -> x > -39 && z > -39
            in 5..6 -> x > -39 && z > -7
            7 -> x > -7 && z > -7
            else -> false
        }
    }

    fun setFloor(floor: Floor) {
        this.floor = floor
    }

    private fun getDungeonPuzzles(tabList: List<String>) {
        for (entry in tabList) {
            val (name, status) = puzzleRegex.find(entry)?.destructured ?: continue
            val puzzle = Puzzle.entries.find { it.displayName == name }?.takeIf { it != Puzzle.UNKNOWN } ?: continue
            if (puzzle !in puzzles) puzzles.add(puzzle)

            puzzle.status = when (status) {
                "✖" -> PuzzleStatus.Failed
                "✔" -> PuzzleStatus.Completed
                "✦" -> PuzzleStatus.Incomplete
                else -> continue
            }
        }
    }

    private fun updateDungeonStats(text: List<String>) {
        for (entry in text) {
            with(dungeonStats) {
                secretsPercent = secretPercentRegex.find(entry)?.groupValues?.get(1)?.toFloatOrNull() ?: secretsPercent
                completedRooms = completedRoomsRegex.find(entry)?.groupValues?.get(1)?.toIntOrNull() ?: completedRooms
                secretsFound = secretCountRegex.find(entry)?.groupValues?.get(1)?.toIntOrNull() ?: secretsFound
                openedRooms = openedRoomsRegex.find(entry)?.groupValues?.get(1)?.toIntOrNull() ?: openedRooms
                puzzleCount = puzzleCountRegex.find(entry)?.groupValues?.get(1)?.toIntOrNull() ?: puzzleCount
                deathsRegex.find(entry)?.groupValues?.get(1)?.toIntOrNull()?.let { deaths = maxOf(deaths, it, chatDeaths) }
                crypts = cryptRegex.find(entry)?.groupValues?.get(1)?.toIntOrNull() ?: crypts
                updateElapsedTime(entry)
            }
        }
    }

    private fun updateElapsedTime(text: String) {
        val match = timeRegex.matchEntire(text.trim()) ?: return
        val (hours, minutes, seconds) = match.destructured
        dungeonStats.secondsElapsed = (hours.toIntOrNull() ?: 0) * 3600 + (minutes.toIntOrNull() ?: 0) * 60 + (seconds.toIntOrNull() ?: 0)
        dungeonStats.elapsedTime = text.substringAfter(":").trim()
        if (!hasStarted && dungeonStats.secondsElapsed > 0) DungeonEvent.Start().post()
    }

    // based on: https://github.com/Noamm9/NoammAddons/blob/1bca286c77939638c13029b1b6d7f387cf92b3f4/src/main/kotlin/com/github/noamm9/utils/dungeons/map/handlers/ScoreCalculation.kt
    private fun updateBonusScore(message: String) {
        val text = message.lowercase().trim()
        if (text == "a prince falls. +1 bonus score") dungeonStats.princeKilled = true
        if (text == "a bat has been slain. +1 bonus score") addBatKill(player.name.string)
        if (inClear && (floor?.floorNumber ?: 0) >= 6 && mimicCharmRegex.matches(text)) dungeonStats.mimicKilled = true

        val party = partyMessageRegex.matchEntire(message) ?: return
        val partyText = party.groups["message"]!!.value.lowercase().trim().trimEnd('!', '.')
        if (partyText in mimicMessages && (floor?.floorNumber ?: 0) >= 6) dungeonStats.mimicKilled = true
        if (partyText in princeMessages) dungeonStats.princeKilled = true
        if (partyText in batMessages) addBatKill(party.groups["sender"]!!.value)
    }

    private fun addBatKill(name: String) {
        if (batKillers.size < 5) batKillers.add(name.lowercase())
        dungeonStats.batKilled = batKillers.isNotEmpty()
    }

    private fun updateDungeonTeammates(tabList: List<Pair<String, Colour>>) {
        dungeonTeammates = getDungeonTeammates(dungeonTeammates, tabList)
        dungeonTeammatesNoSelf = dungeonTeammates.filter { it.name != player.name.string }

        leapTeammates =
            when (LeapMenu.sorting.selected) {
                "Class" -> dungeonTeammatesNoSelf.sortedWith(compareBy({ it.clazz.ordinal }, { it.name }))
                "Name" -> dungeonTeammatesNoSelf.sortedBy { it.name }
                "Custom" -> {
                    if (LeapMenu.fillEmpty) {
                        val remaining = dungeonTeammatesNoSelf.toMutableList()
                        LeapMenu.customOrder.map { entry ->
                            if (entry == "_") DungeonPlayer.EMPTY
                            else remaining.firstOrNull { it.name.lowercase().equals(entry, true) }?.also { remaining.remove(it) } ?: DungeonPlayer.EMPTY
                        }.map { if (it == DungeonPlayer.EMPTY && remaining.isNotEmpty()) remaining.removeAt(0) else it }
                    } else {
                        LeapMenu.customOrder.map { entry ->
                            if (entry == "_") DungeonPlayer.EMPTY
                            else dungeonTeammatesNoSelf.firstOrNull { it.name.lowercase().equals(entry, true) } ?: DungeonPlayer.EMPTY
                        }
                    }
                }
                else -> dungeonTeammatesNoSelf
            }
    }

    val dungeonItemDrops = setOf(
        "Health Potion VIII Splash Potion", "Healing Potion 8 Splash Potion", "Healing Potion VIII Splash Potion", "Healing VIII Splash Potion", "Healing 8 Splash Potion",
        "Decoy", "Inflatable Jerry", "Spirit Leap", "Trap", "Training Weights", "Defuse Kit", "Dungeon Chest Key", "Treasure Talisman", "Revive Stone", "Architect's First Draft",
        "Secret Dye", "Candycomb"
    )

    private val bossLevers = setOf(
        BlockPos(94, 124, 113),
        BlockPos(106, 124, 113),
        BlockPos(27, 124, 127),
        BlockPos(23, 132, 138),
        BlockPos(14, 122, 55),
        BlockPos(2, 122, 55),
        BlockPos(86, 128, 46),
        BlockPos(84, 121, 34)
    )

    private val deviceLevers = setOf(
        BlockPos(62, 136, 142),
        BlockPos(62, 133, 142),
        BlockPos(60, 134, 142),
        BlockPos(60, 135, 142),
        BlockPos(58, 133, 142),
        BlockPos(58, 136, 142)
    )

    private val EXTRA_DEVICE_LEVER = BlockPos(59, 133, 142)

    val blacklistedDBBlocks = setOf(
        Blocks.BARRIER, Blocks.BEDROCK, Blocks.COMMAND_BLOCK, Blocks.SKELETON_SKULL, Blocks.SKELETON_WALL_SKULL,
        Blocks.WITHER_SKELETON_SKULL, Blocks.WITHER_SKELETON_WALL_SKULL, Blocks.PLAYER_HEAD,
        Blocks.PLAYER_WALL_HEAD, Blocks.TNT,
        Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.END_PORTAL_FRAME, Blocks.END_PORTAL,
        Blocks.PISTON, Blocks.PISTON_HEAD, Blocks.STICKY_PISTON, Blocks.MOVING_PISTON,
        Blocks.LEVER, Blocks.STONE_BUTTON, Blocks.DISPENSER, Blocks.HOPPER, Blocks.DROPPER
    )

    val terminalTitles = setOf("Correct all the panes!", "Change all to same color!", "Click in order!", "What starts with:", "Select all the", "Click the button on time!")

    val BLOOD_START_REGEX = Regex("^\\[BOSS] The Watcher: (Congratulations, you made it through the Entrance\\.|Ah, you've finally arrived\\.|Ah, we meet again\\.\\.\\.|So you made it this far\\.\\.\\. interesting\\.|You've managed to scratch and claw your way here, eh\\?|I'm starting to get tired of seeing you around here\\.\\.\\.|Oh\\.\\. hello\\?|Things feel a little more roomy now, eh\\?)$")

    private val enterRegex = Regex("^-*\\n\\[[^]]+] (\\w+) entered (?:MM )?\\w+ Catacombs, Floor (\\w+)!\\n-*$")
    private val puzzleRegex = Regex("^ (\\w+(?: \\w+)*|\\?\\?\\?): \\[([✖✔✦])] ?(?:\\((\\w+)\\))?$")
    private val expectingBloodRegex = Regex("^\\[BOSS] The Watcher: You have proven yourself. You may pass.")
    private val doorOpenRegex = Regex("^(?:\\[\\w+] )?(\\w+) opened a (?:WITHER|Blood) door!")
    private val secretPercentRegex = Regex("^ Secrets Found: ([\\d.]+)%$")
    private val deathRegex = Regex("^\\s*☠ (?:You were|(\\w{1,16})) .+?(?: and became a ghost)?\\.$")
    private val timeRegex = Regex("^(?:Time(?: Elapsed)?|Elapsed): (?:(\\d+)h ?)?(?:(\\d+)m ?)?(?:(\\d+)s)?$")
    private val completedRoomsRegex = Regex("^ Completed Rooms: (\\d+)$")
    private val clearedRegex = Regex("^Cleared: (\\d+)% \\(\\d+\\)$")
    private val secretCountRegex = Regex("^ Secrets Found: (\\d+)$")
    private val openedRoomsRegex = Regex("^ Opened Rooms: (\\d+)$")
    private val floorRegex = Regex("The Catacombs \\((\\w+)\\)$")
    private val partyMessageRegex = Regex("^Party > (?:\\[[^]]+] )*(?<sender>\\w+): (?<message>.+)$")
    private val puzzleCountRegex = Regex("^\\s*Puzzles: \\((\\d+)\\)$")
    private val deathsRegex = Regex("^\\s*Team Deaths: (\\d+)$")
    private val cryptRegex = Regex("^ Crypts: (\\d+)$")
    private val mimicCharmRegex = Regex("charm you charmed a mimic and captured (?:\\d+ shards from it|its shard)\\.")
    private val mimicMessages = setOf("mimic dead", "mimic killed", "mimic slain", "\$skytils-dungeon-score-mimic$", "child destroyed", "mimic obliterated", "mimic exorcised", "mimic destroyed", "mimic annhilated", "breefing killed", "breefing dead")
    private val princeMessages = setOf("prince dead", "prince killed", "prince slain", "\$skytils-dungeon-score-prince$")
    private val batMessages = setOf("bat dead", "bat killed", "bat slain", "\$skytils-dungeon-score-bat$")

    data class DungeonStats(
        var secretsFound: Int = 0,
        var secretsPercent: Float = 0f,
        var crypts: Int = 0,
        var openedRooms: Int = 0,
        var completedRooms: Int = 0,
        var deaths: Int = 0,
        var percentCleared: Int = 0,
        var elapsedTime: String = "0s",
        var secondsElapsed: Int = 0,
        var mimicKilled: Boolean = false,
        var princeKilled: Boolean = false,
        var batKilled: Boolean = false,
        var doorOpener: String = "Unknown",
        var bloodDone: Boolean = false,
        var bloodOpen: Boolean = false,
        var puzzleCount: Int = 0,
    )
}