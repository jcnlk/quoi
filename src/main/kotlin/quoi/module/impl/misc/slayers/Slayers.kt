package quoi.module.impl.misc.slayers

import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.events.PacketEvent
import quoi.api.events.RenderEvent
import quoi.api.events.SlayerEvent
import quoi.api.events.TickEvent
import quoi.api.events.core.on
import quoi.api.events.core.trackedBy
import quoi.api.skyblock.location.Island
import quoi.module.Module
import quoi.module.impl.misc.slayers.blaze.BlazeSlayer
import quoi.module.impl.misc.slayers.blaze.Attunement
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.UIComponent.Companion.visibleIf
import quoi.utils.EntityUtils.getEntities
import quoi.utils.EntityUtils.getEntity
import quoi.utils.EntityUtils.interpolatedBox
import quoi.utils.EntityUtils.renderPos
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.romanToInt
import quoi.utils.render.drawStyledBox
import quoi.utils.render.drawTracer

@Suppress("unnecessary_safe_call")
object Slayers : Module(
    "Slayers",
    desc = "Utilities for Slayer quests, including boss ESP.",
    area = Island.Skyblock
) {

    private val bossEsp by switch("Boss ESP", desc = "Highlights your active Slayer boss through walls.")
    private val espStyle by selector("Style", "Filled box", arrayListOf("Box", "Filled box", "Filled"), desc = "Render style for Slayer bosses.").childOf(::bossEsp)
    private val outlineColour by colourPicker("Outline colour", Colour.CYAN, allowAlpha = true, desc = "Outline colour for non-Blaze Slayer bosses.").childOf(::bossEsp).visibleIf { espStyle.selected != "Filled" }
    private val fillColour by colourPicker("Fill colour", Colour.CYAN.withAlpha(0.33f), allowAlpha = true, desc = "Fill colour for non-Blaze Slayer bosses.").childOf(::bossEsp).visibleIf { espStyle.selected != "Box" }
    private val tracer by switch("Tracer", desc = "Draws a line to your active Slayer boss.").childOf(::bossEsp)
    private val depth by switch("Depth check", desc = "Hides the ESP behind blocks.").childOf(::bossEsp)

    private val attunementColours = mapOf(
        Attunement.ASHEN to Colour.BLACK,
        Attunement.SPIRIT to Colour.WHITE,
        Attunement.AURIC to Colour.YELLOW,
        Attunement.CRYSTAL to Colour.CYAN
    )

    private val slayers = setOf(
        BlazeSlayer
    ).flatMap { it.features }

    override fun onDisable() {
        slayers.forEach { it.onDisable() }
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
            else -> it // can get stuck when boss is slain and you collect the reward but doesn't really matter since I won't be using it prob.
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

        val a = getEntity(spawnedBy.id - 3) as? LivingEntity
        a
    }

    init {
        on<RenderEvent.World> {
            if (!bossEsp || questState != QuestState.KILLING) return@on

            val targets = BlazeSlayer.espTargets()
            if (targets.isNotEmpty()) {
                targets.forEach { (entity, attunement) ->
                    val colour = attunementColours[attunement] ?: Colour.CYAN
                    ctx.drawStyledBox(espStyle.selected, entity.interpolatedBox, colour, colour.withAlpha(0.33f), depth = depth)
                    if (tracer) ctx.drawTracer(entity.renderPos.add(0.0, entity.bbHeight / 2.0, 0.0), colour, depth = depth)
                }
                return@on
            }

            val boss = currentBoss?.takeUnless { it.isDeadOrDying } ?: return@on
            ctx.drawStyledBox(espStyle.selected, boss.interpolatedBox, outlineColour, fillColour, depth = depth)
            if (tracer) ctx.drawTracer(boss.renderPos.add(0.0, boss.bbHeight / 2.0, 0.0), outlineColour, depth = depth)
        }
    }

    private val tierRegex = Regex(".* (I{1,3}|IV|V) \\d+.*❤$")
}