package quoi.module.impl.floor7

import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket
import net.minecraft.world.entity.monster.Creeper
import quoi.api.abobaui.constraints.impl.positions.Centre
import quoi.api.abobaui.dsl.at
import quoi.api.abobaui.dsl.px
import quoi.api.abobaui.elements.impl.Text.Companion.shadow
import quoi.api.abobaui.elements.impl.Text.Companion.textSupplied
import quoi.api.colour.Colour
import quoi.api.events.ChatEvent
import quoi.api.events.PacketEvent
import quoi.api.events.RenderEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.on
import quoi.api.skyblock.dungeon.Dungeon.getMageCooldownMultiplier
import quoi.api.skyblock.dungeon.Floor7Utils.inF7Boss
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.utils.ChatUtils.modMessage
import quoi.utils.Scheduler.scheduleTask
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.skyblock.player.PlayerUtils
import quoi.utils.skyblock.player.SwapManager
import quoi.utils.ui.hud.impl.TextHud
import kotlin.math.roundToLong

object WitherCloak : Module(
    "Wither Cloak",
    desc = "Tracks Creeper Veil and optionally hides nearby cloak creepers."
) {
    private val title by textInput("Title", "Wither Cloak", length = 24, desc = "Title to show while Wither Cloak is active.")
    private val timer by switch("Timer", desc = "Shows the cooldown timer.")
    private val hideCloak by switch("Hide cloak", desc = "Hides creepers around the player.")
    private val autoBossCloak by switch("Auto cloak", desc = "Automatically uses Wither Cloak on the F7 boss countdown at 2.")
    private val autoDelay by slider("Delay", 3, 1, 10, 1, unit = "t").childOf(::autoBossCloak)

    @Suppress("unused")
    private val hud by textHud("Wither cloak", Colour.CYAN, toggleable = false) {
        visibleIf { this@WitherCloak.enabled && (preview || inCloak || (timer && remainingCloakMillis() != null)) }
        group {
            textSupplied(
                supplier = ::displayTitle,
                colour = Colour.TRANSPARENT,
                font = font,
                size = 18.px
            )

            textSupplied(
                supplier = { currentDisplayText(preview).orEmpty() },
                colour = colour,
                font = font,
                pos = at(x = Centre),
                size = 18.px
            ).shadow = shadow
        }
    }.setting()

    private var inCloak = false
    private var cloakCooldownEnd = 0L
    private var swapBackSlot = -1

    override fun onDisable() { reset() }

    init {
        on<ChatEvent.Packet> {
            when (unformatted) {
                "Creeper Veil Activated!" -> inCloak = true
                "Creeper Veil De-activated!" -> disableCloak(5_000L)
                "Creeper Veil De-activated! (Expired)",
                "Not enough mana! Creeper Veil De-activated!" -> disableCloak(10_000L)
            }
        }

        on<PacketEvent.Received, ClientboundSetTitleTextPacket> {
            if (!inF7Boss || !autoBossCloak || inCloak || swapBackSlot != -1 || packet.text.string.noControlCodes != "2") return@on

            val originalSlot = player.inventory.selectedSlot
            if (!SwapManager.swapById("WITHER_CLOAK").success) return@on

            swapBackSlot = originalSlot

            scheduleTask(autoDelay) {
                if (!enabled || swapBackSlot == -1) return@scheduleTask

                modMessage("&aCloaking!")
                PlayerUtils.interact()
            }

            scheduleTask(autoDelay * 2) {
                val slot = swapBackSlot
                swapBackSlot = -1

                if (slot !in 0..8 || !enabled) return@scheduleTask
                SwapManager.swapToSlot(slot)
            }
        }

        on<RenderEvent.Entity> {
            if (!hideCloak) return@on
            val creeper = entity as? Creeper ?: return@on
            if (creeper.health != 20f || !creeper.isInvisible || !creeper.isPowered || creeper.distanceTo(player) > 10f) return@on
            cancel()
        }

        on<WorldEvent.Change> { reset() }
    }

    private fun currentDisplayText(preview: Boolean): String? = when {
        preview || inCloak -> displayTitle()
        !timer -> null
        else -> remainingCloakMillis()?.let {
            "%.2f".format(it / 1000.0)
        }
    }

    private fun displayTitle() = title.ifBlank { "Wither Cloak" }

    private fun remainingCloakMillis(): Long? =
        (cloakCooldownEnd - System.currentTimeMillis()).takeIf { it > 0 }

    private fun disableCloak(baseCooldown: Long) {
        inCloak = false
        cloakCooldownEnd = System.currentTimeMillis() + (baseCooldown * getMageCooldownMultiplier()).roundToLong()
    }

    private fun reset() {
        inCloak = false
        cloakCooldownEnd = 0L
        swapBackSlot = -1
    }
}
