package quoi.module.impl.dungeon

import kotlinx.coroutines.launch
import net.minecraft.world.phys.Vec3
import quoi.QuoiMod.scope
import quoi.api.abobaui.elements.impl.Text.Companion.shadow
import quoi.api.abobaui.elements.impl.Text.Companion.textSupplied
import quoi.api.colour.Colour
import quoi.api.events.ChatEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.skyblock.Island
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.invoke
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.utils.ChatUtils.modMessage
import quoi.utils.Scheduler.wait
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.StringUtils.toFixed
import quoi.utils.ThemeManager.theme
import quoi.utils.skyblock.player.MovementUtils.cancelMovementTask
import quoi.utils.skyblock.player.MovementUtils.moveTo
import quoi.utils.skyblock.player.MovementUtils.resetInput
import quoi.utils.skyblock.player.PlayerUtils.rightClick
import quoi.utils.skyblock.player.SwapManager

object FireFreeze : Module(
    "Fire Freeze",
    desc = "Shows the F3/M3 Fire Freeze timer and can automate the use.",
    area = Island.Dungeon(3, inBoss = true)
) {
    private val autoUse by switch("Auto use")
    private val autoReposition by switch("Auto reposition").childOf(::autoUse)

    private val hud by textHud("Fire freeze", Colour.WHITE) {
        visibleIf { this@FireFreeze.enabled && (preview || startedAt >= 0) }
        textSupplied(
            supplier = { "§bFF§f: ${formatTime(if (preview) 100 else remainingTicks)}" },
            size = theme.textSize,
            font = font,
            colour = colour
        ).shadow = shadow
    }.setting()

    private var startedAt = -1
    private var remainingTicks = -1
    private var autoTriggered = false
    private var repositioning = false
    private var repositionTarget: Vec3? = null

    private val autoUsePosition = Vec3(1.5, 72.0, 1.5)
    private val repositionPositions = listOf(
        Vec3(1.5, 71.0, 4.5),
        Vec3(1.5, 71.0, -1.5),
        Vec3(4.5, 71.0, 1.5),
        Vec3(-1.5, 71.0, 1.5),
    )

    override fun onDisable() {
        if (repositioning) {
            cancelMovementTask()
            mc.player?.resetInput()
        }
        resetState()
        super.onDisable()
    }

    init {
        on<WorldEvent.Change> {
            resetState()
        }

        on<ChatEvent.Packet> {
            if (message.noControlCodes != "[BOSS] The Professor: Oh? You found my Guardians' one weakness?") return@on

            startedAt = 110
            remainingTicks = startedAt
            autoTriggered = false

            if (autoReposition) {
                repositionTarget = getBestRepositionTarget()
                repositioning = true
            }
        }

        on<TickEvent.Start> {
            if (!repositioning) return@on
            val target = repositionTarget ?: run {
                repositioning = false
                return@on
            }

            if (!Dungeon.inBoss || !Dungeon.isFloor(3)) {
                repositioning = false
                cancelMovementTask()
                player.resetInput()
                return@on
            }

            if (player.position().distanceToSqr(target) <= 0.09) {
                repositioning = false
                cancelMovementTask()
                player.resetInput()
                return@on
            }

            player.moveTo(target)
        }

        on<TickEvent.Server> {
            if (startedAt < 0) return@on

            remainingTicks = startedAt--

            if (!autoTriggered && remainingTicks <= 0) {
                if (autoUse && player.position().distanceToSqr(autoUsePosition) <= 25.0) {
                    autoTriggered = true
                    triggerAutoUse()
                }
                resetTimer()
            }
        }
    }

    private fun triggerAutoUse() {
        scope.launch {
            val swapped = SwapManager.swapByName("Fire Freeze Staff")
            if (!swapped.success) {
                modMessage("§cCould not find Fire Freeze Staff in your hotbar.")
                return@launch
            }

            if (!swapped.already) wait(1)
            player.rightClick()
        }
    }

    private fun getBestRepositionTarget(): Vec3? {
        val position = mc.player?.position() ?: return null
        return repositionPositions.minByOrNull { position.distanceToSqr(it) }
    }

    private fun formatTime(ticks: Int): String {
        val time = (ticks.coerceAtLeast(0) / 20f).toFixed()
        val colour = when {
            ticks >= 74 -> "§a"
            ticks >= 37 -> "§6"
            else -> "§c"
        }
        return "$colour$time§fs"
    }

    private fun resetTimer() {
        startedAt = -1
        remainingTicks = -1
        autoTriggered = false
    }

    private fun resetState() {
        if (repositioning) {
            cancelMovementTask()
            mc.player?.resetInput()
        }
        repositioning = false
        repositionTarget = null
        resetTimer()
    }
}
