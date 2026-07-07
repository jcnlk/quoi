package quoi.module.impl.mining

import net.minecraft.sounds.SoundEvents
import quoi.api.events.ChatEvent
import quoi.api.events.core.on
import quoi.api.skyblock.location.Island
import quoi.module.Module
import quoi.utils.StringUtils.formattedString
import quoi.utils.skyblock.player.PlayerUtils

object AbilityAlert : Module(
    "Ability Alert",
    area = Island.Mining,
    desc = "Shows an alert when your mining ability is available again."
) {
    init {
        on<ChatEvent.Packet> {
            val formatted = text.formattedString
            if (!formatted.startsWith("§a") ||
                "§6" !in formatted ||
                !formatted.endsWith("§ais now available!")
            ) return@on

            val ability = AVAILABLE_PATTERN.matchEntire(unformatted)
                ?.groupValues
                ?.get(1)
                ?: return@on

            PlayerUtils.setTitle(
                title = "§6${ability.uppercase()}!",
                playSound = true,
                sound = SoundEvents.EXPERIENCE_ORB_PICKUP,
                pitch = 0f,
                stayAlive = 50,
                fadeOut = 10,
            )
        }
    }

    private val AVAILABLE_PATTERN = Regex("^(.+) is now available!$")
}
