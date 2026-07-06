package quoi.module.impl.misc

import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.on
import quoi.module.Module
import quoi.utils.ChatUtils.modMessage
import quoi.utils.ChatUtils.prefix
import quoi.utils.WorldUtils
import quoi.utils.skyblock.player.PlayerUtils.isNicked
import quoi.utils.skyblock.player.PlayerUtils.realName
import quoi.utils.skyblock.player.PlayerUtils.usesNickSkin

object AntiNick : Module(
    "AntiNick",
    desc = "Detects nicked players."
) {
    private val scannedProfiles = hashSetOf<String>()
    private var scanTicks = 0

    init {
        on<WorldEvent.Load.Start> {
            scannedProfiles.clear()
            scanTicks = 0
        }

        on<TickEvent.End> {
            if (++scanTicks < 20) return@on
            scanTicks = 0

            WorldUtils.tablist
                .asSequence()
                .map { it.profile }
                .filter { it.id.version() != 2 }
                .filter { "${it.id}:${it.name}" !in scannedProfiles }
                .filter { it.isNicked }
                .forEach { profile ->
                    scannedProfiles += "${profile.id}:${profile.name}"

                    val result = profile.realName
                        ?.takeUnless { it.equals(profile.name, ignoreCase = true) }
                        ?.let { "&a[DENICKED] $it" }
                        ?: if (profile.usesNickSkin) "&e[NICK SKIN]" else "&e[NICKED]"
                    modMessage("${profile.name} &e->&r $result", prefix = prefix("AntiNick"))
                }
        }
    }
}