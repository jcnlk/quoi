package quoi.module.impl.misc

import java.util.Optional
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import quoi.api.events.ChatEvent
import quoi.api.events.core.on
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.location.Island
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.UIComponent.Companion.visibleIf
import quoi.utils.ChatUtils
import quoi.utils.Scheduler.scheduleTask
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.skyblock.player.PlayerUtils

object GreatSpook : Module(
    "Great Spook",
    area = Island.Skyblock,
    desc = "Automatically solves Primal Fear challenges and shows a title when a Primal Fear spawns.",
) {
    private val autoMath by switch("Auto math", desc = "Automatically answers Quick Maths in all chat.")
    private val autoSpeaking by switch("Auto Public Speaking", desc = "Automatically answers Public Speaking prompts addressed to you in all chat.")
    private val autoCommitment by switch("Auto Commitment Phobia", desc = "Automatically signs the document linked in chat.")
    private val responseDelay by slider("Answer delay", 0, 0, 10, 1, unit = " ticks", desc = "Client ticks to wait before answering. Zero answers immediately.").visibleIf { autoMath || autoSpeaking || autoCommitment }
    private val spawnTitle by switch("Spawn title", desc = "Shows a title when the spawn message for a Primal Fear arrives.")
    private val showInDungeon by switch("Show in dungeons", desc = "Show the spawn title while in a dungeon.").childOf(::spawnTitle)

    private val speakingPattern = Regex("\\[FEAR] Public Speaking Demon: (?:Speak|Say something interesting) (\\w{1,16})!")
    private val speakingAnswers = listOf(
        "The Great Spook is here!",
        "Time to face another Primal Fear.",
        "Spooky monsters are back in SkyBlock.",
    )
    private var speakingIndex = 0

    init {
        on<ChatEvent.Packet> {
            when {
                spawnTitle && unformatted == "FEAR. A Primal Fear has been summoned!" && (showInDungeon || !Dungeon.inDungeons) ->
                    PlayerUtils.setTitle("§dPrimal Fear spawned!", stayAlive = 40, fadeOut = 10)

                autoMath && unformatted.startsWith("QUICK MATHS! Solve: ") ->
                    calcMath(unformatted.removePrefix("QUICK MATHS! Solve: "))?.let { answer("ac $it") }

                autoSpeaking && speakingPattern.matchEntire(unformatted)?.groupValues?.get(1).equals(player.gameProfile.name) -> {
                    answer("ac ${speakingAnswers[speakingIndex]}")
                    speakingIndex = (speakingIndex + 1) % speakingAnswers.size
                }

                autoCommitment -> getCommand(text)?.let { answer(it) }
            }
        }
    }

    private fun answer(command: String) {
        if (responseDelay == 0) ChatUtils.command(command)
        else scheduleTask(responseDelay) { ChatUtils.command(command) }
    }

    private fun getCommand(text: Component): String? {
        val msg = text.string.noControlCodes.trim().removePrefix("[FEAR] Commitment Phobia: Please sign this paper!\n")
        if (!msg.startsWith("Click HERE to sign the ") || !msg.endsWith('.')) return null
        //? if >=26.4 {
        /*val click = text.visit<ClickEvent>({ style, content ->
            if ("HERE" in content.noControlCodes) Optional.ofNullable(style.clickEvent) else Optional.empty()
        }, ClickEvent.RunCommand(""), Style.EMPTY).orElse(null)
        *///?} else {
        val click = text.toFlatList().firstOrNull { "HERE" in it.string.noControlCodes }?.style?.clickEvent
        //?}
        return (click as? ClickEvent.RunCommand)?.command?.takeIf { it.isNotBlank() }
    }

    private fun calcMath(expression: String): String? {
        if (expression.length > 128) return null
        val math = expression.trim().replace('x', '*')
        if (Regex("""\d\s*\(|\)\s*[\d(]""").containsMatchIn(math)) return null

        Regex("""\(([^()]+)\)""").find(math)?.let { brackets ->
            val result = calcMath(brackets.groupValues[1]) ?: return null
            return calcMath(math.replaceRange(brackets.range, result))
        }
        for (operators in listOf("[*/]", "[+-]")) {
            val match = Regex("""(?<!\d)([+-]?\d+)\s*($operators)\s*([+-]?\d+)""").find(math) ?: continue
            val (first, operator, second) = match.destructured
            val left = first.toIntOrNull() ?: return null
            val right = second.toIntOrNull() ?: return null
            val result = when (operator) {
                "*" -> left * right
                "/" -> if (right == 0 || left % right != 0) return null else left / right
                "+" -> left + right
                else -> left - right
            }
            return calcMath(math.replaceRange(match.range, result.toString()))
        }
        return math.trim().toIntOrNull()?.toString()
    }
}
