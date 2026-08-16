package quoi.module.impl.general

import net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket
import quoi.api.events.ChatEvent
import quoi.api.events.PacketEvent
import quoi.api.events.core.on
import quoi.api.skyblock.SkyblockPlayer.AUTOPET_REGEX
import quoi.api.skyblock.dungeon.Dungeon
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.group.SettingGroup.Companion.childOf
import quoi.utils.skyblock.player.PlayerUtils

object Titles : Module("Titles") {
    private val autoPet by switch("Petrules", desc = "Show title upon petrule chat message")
    private val invincibilityProc by switch("Invincibility", desc = "Show title upon bonzo/spirit/phoenix proc")
    private val shadowAssassin by switch("Shadow Assassin")

    private val titleSettings by text("Settings")
    private val dungeonsOnly by switch("Dungeons only").childOf(::titleSettings)
    private val bossOnly by switch("Boss only").childOf(::titleSettings)
    private val asSubtitle by switch("Use subtitles", true, desc = "Shows the text as a subtitle instead of the main title.").childOf(::titleSettings)
    private val titleDuration by slider("Title duration", 2.0, 0.5, 5.0, 0.1, desc = "How long the title stays on screen.", "s").childOf(::titleSettings)

    private val playSound by switch("Play sound", desc = "Plays a sound when title pops up").childOf(::titleSettings)
    private val soundSettings = sound("Title sound").childOf(::titleSettings) { playSound }

    init {
        on<ChatEvent.Packet> {
            if (dungeonsOnly && !Dungeon.inDungeons) return@on
            if (bossOnly && !Dungeon.inBoss) return@on

            if (autoPet) {
                AUTOPET_REGEX.find(message)?.groupValues?.get(1)?.let { stupid(it.trim()) }
            }

            if (invincibilityProc) {
                when (unformatted) {
                    "Second Wind Activated! Your Spirit Mask saved your life!" ->
                        stupid("§fSpirit")
                    "Your ⚚ Bonzo's Mask saved your life!", "Your Bonzo's Mask saved your life!" ->
                        stupid("§cBonzo")
                    "Your Phoenix Pet saved you from certain death!" ->
                        stupid("§6Phoenix")
                }
            }
        }

        on<PacketEvent.Received, ClientboundInitializeBorderPacket> {
            if (!shadowAssassin) return@on
            if (Dungeon.isFloor(1, 2, 3) && Dungeon.inBoss) return@on
            PlayerUtils.setTitle("", "§aShadow Assassin!", playSound = true, stayAlive = 35, fadeOut = 0)
        }
    }

    private fun stupid(text: String) {
        val (sound, volume, pitch) = soundSettings()

        PlayerUtils.setTitle(
            title = if (!asSubtitle) text else "",
            subtitle = if (asSubtitle) text else "",
            playSound = playSound,
            sound = sound,
            volume = volume,
            pitch = pitch,
            stayAlive = (titleDuration * 20).toInt()
        )
    }
}