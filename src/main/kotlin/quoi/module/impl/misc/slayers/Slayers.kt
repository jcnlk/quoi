package quoi.module.impl.misc.slayers

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import quoi.api.colour.Colour
import quoi.api.colour.alpha
import quoi.api.colour.withAlpha
import quoi.api.events.PacketEvent
import quoi.api.events.RenderEvent
import quoi.api.events.SlayerEvent
import quoi.api.events.TickEvent
import quoi.api.events.core.on
import quoi.api.events.core.trackedBy
import quoi.api.skyblock.location.Island
import quoi.module.Module
import quoi.module.impl.misc.Test
import quoi.module.impl.misc.slayers.blaze.BlazeSlayer
import quoi.module.impl.misc.slayers.enderman.BeaconESP
import quoi.module.impl.misc.slayers.enderman.EndermanSlayer
import quoi.module.settings.UIComponent.Companion.visibleIf
import quoi.module.settings.group.SettingGroup.Companion.childOf
import quoi.utils.ChatUtils.modMessage
import quoi.utils.EntityUtils.getEntities
import quoi.utils.EntityUtils.getEntity
import quoi.utils.EntityUtils.interpolatedBox
import quoi.utils.StringUtils.formatTime
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.romanToInt
import quoi.utils.skyblock.player.PlayerUtils
import quoi.utils.ui.textPair

@Suppress("unnecessary_safe_call")
object Slayers : Module(
    "Slayers",
    desc = "Various slayer features.",
    area = Island.Skyblock
) {
    private val slayers: Set<ISlayer> = setOf(BlazeSlayer, EndermanSlayer)

    internal val beaconHighlight = highlight(colour = Colour.RED, fillColour = Colour.RED, glow = false).childOf(BeaconESP.component)
    internal val beaconTracer = tracer(colour = Colour.RED, distance = null).childOf(BeaconESP.component)

    private val esp by switch("Boss ESP")
    private val highlight = highlight(aabbOffset = true).childOf(::esp)
    private val tracer = tracer(distance = null).childOf(::esp)

    private val spawnAlert by switch("Spawn Alert", desc = "Shows an alert when your Slayer boss spawns.")
    private val spawnTime by switch("Spawn Time", desc = "Shows how long your Slayer boss took to spawn in chat.")
    private val killTime by switch("Kill Time", desc = "Shows how long your Slayer boss took to kill in chat.")

    private var spawnStartedAt = 0L
    private var killStartedAt = 0L

    @Suppress("unused")
    private val debug by textHud("Debug") {
        column {
            debugStrings.forEach { (name, str) ->
                textPair(
                    string = "$name:",
                    supplier = str,
                    labelColour = colour,
                    shadow = shadow,
                    font = font
                )
            }
        }
    }.setting().visibleIf { Test.enabled }

    init {
        on<SlayerEvent.State> {
            val now = System.currentTimeMillis()

            when (new) {
                QuestState.SPAWNING -> {
                    spawnStartedAt = now
                    killStartedAt = 0L
                }

                QuestState.KILLING -> {
                    if (old == QuestState.SPAWNING && spawnStartedAt != 0L && spawnTime) {
                        modMessage("Spawn Time: §a${formatTime(now - spawnStartedAt)}")
                    }
                    killStartedAt = now

                    if (spawnAlert) PlayerUtils.setTitle(
                        title = "§c§lBOSS SPAWNED!",
                        playSound = true,
                        sound = SoundEvents.EXPERIENCE_ORB_PICKUP,
                        pitch = 0f,
                        stayAlive = 30,
                        fadeOut = 10,
                    )
                }

                QuestState.SLAIN -> {
                    if (old == QuestState.KILLING && killStartedAt != 0L && killTime) {
                        modMessage("Kill Time: §a${formatTime(now - killStartedAt)}")
                    }
                    killStartedAt = 0L
                }

                QuestState.FAILED, QuestState.NONE -> {
                    spawnStartedAt = 0L
                    killStartedAt = 0L
                }
            }
        }

        on<RenderEvent.World> {
            if (!esp || questState != QuestState.KILLING) return@on

            ctx.drawSlayer(currentBoss)

            slayers.forEach { slayer ->
                slayer.entitiesForRender.forEach { (it, col) ->
                    ctx.drawSlayer(it, col, false)
                }
            }
        }
    }

    override fun onDisable() {
        slayers.forEach { s -> s.features.forEach { it.onDisable() } }
        super.onDisable()
    }

    var questTier = 0
        private set

    val questState by trackedBy<PacketEvent.Received, ClientboundSetPlayerTeamPacket, QuestState>(QuestState.NONE) {
        val params = packet.parameters.orElse(null) ?: return@trackedBy it

        val text = (params.playerPrefix.string + params.playerSuffix.string).noControlCodes.trim()
        val new = when {
            text.contains("Combat") || text.contains("Kills") -> QuestState.SPAWNING
            text == "Slay the boss!" -> QuestState.KILLING
            text == "Boss slain!" -> QuestState.SLAIN
            else -> it // can get stuck when boss is slain, and you collect the reward but doesn't really matter since I won't be using it prob.
        }

        if (new != it) SlayerEvent.State(it, new).post()

        new
    }

    val currentBoss by trackedBy<TickEvent.End, LivingEntity?>(null) { boss ->
        if (questState != QuestState.KILLING) {
            questTier = 0
            return@trackedBy null
        }

        boss?.let {
            if (!it.isDeadOrDying && !it.isRemoved) return@trackedBy it
            return@trackedBy null
        }

        val spawnedBy = getEntities<ArmorStand>().firstOrNull { stand ->
            val name = stand.displayName?.string ?: return@trackedBy null
            name.contains("Spawned by: ${player.name.string}", ignoreCase = true)
        } ?: return@trackedBy null

        val bossStand = (getEntity(spawnedBy.id - 2) as? ArmorStand)?.displayName?.string
            ?: return@trackedBy null

        questTier = tierRegex.find(bossStand)?.destructured
            ?.let { (tier) -> romanToInt(tier) }
            ?: return@trackedBy null

        getEntity(spawnedBy.id - 3) as? LivingEntity
    }

    private fun LevelRenderContext.drawSlayer(entity: LivingEntity?, colour: Colour? = null, main: Boolean = true) {
        if (entity == null || entity.isInvisible || entity.isDeadOrDying) return
        if (!main && colour == null) return
        highlight.draw(
            this,
            entity.interpolatedBox,
            colour = colour ?: Colour.WHITE,
            fillColour = colour?.withAlpha(highlight.fill.alpha) ?: Colour.WHITE.withAlpha(67)
        )

        tracer.draw(this, entity, colour = colour ?: Colour.WHITE)
    }

    private val debugStrings = buildList {
        add("Boss" to { currentBoss?.displayName?.string })
        add("Tier" to { questTier })
        addAll(slayers.map {
            (it::class.simpleName ?: "Slayer") to { it.debugString }
        })
    }

    private val tierRegex = Regex(".* (I{1,3}|IV|V) \\d+.*❤$")
}
