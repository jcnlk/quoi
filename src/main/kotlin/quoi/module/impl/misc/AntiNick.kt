package quoi.module.impl.misc

import quoi.api.events.WorldEvent
import quoi.api.events.core.on
import quoi.module.Module
import quoi.utils.ChatUtils.modMessage
import quoi.utils.ChatUtils.prefix
import quoi.utils.Scheduler.scheduleTask
import quoi.utils.WorldUtils
import quoi.utils.skyblock.player.PlayerUtils.isNicked
import quoi.utils.skyblock.player.PlayerUtils.realName

object AntiNick : Module(
    "AntiNick",
    desc = "Detects nicked players."
) {
    init {
        on<WorldEvent.Load.End> {
            // The first tab-list entry can arrive before the remaining profiles and
            // their texture properties. Give the list a moment to finish loading.
            scheduleTask(20) {
                WorldUtils.tablist
                    .asSequence()
                    .map { it.profile }
                    .filter { it.id.version() != 2 && it.isNicked }
                    .forEach { profile ->
                        val denicked = profile.realName
                            ?.takeUnless { it.equals(profile.name, ignoreCase = true) }
                            ?.let { "&a[DENICKED] $it" }
                            ?: "&c[CANNOT DENICK]"
                        modMessage("${profile.name} &e->&r $denicked", prefix = prefix("AntiNick"))
                    }
            }
        }
    }
}