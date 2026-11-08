package quoi.module.impl.general

import com.mojang.authlib.properties.Property
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.on
import quoi.api.skyblock.location.Island
import quoi.api.skyblock.location.Location.onP3Sim
import quoi.module.Module
import quoi.utils.ChatUtils.modMessage
import quoi.utils.skyblock.player.PlayerUtils.NickResult
import quoi.utils.skyblock.player.PlayerUtils.nickResult
import quoi.utils.skyblock.player.PlayerUtils.textureProperty
import java.util.*

object AntiNick : Module(
    "AntiNick",
    desc = "Detects nicked players.",
    area = Island.Skyblock
) {
    private data class ProfileKey(val id: UUID, val name: String)
    private data class ProfileState(val textureProperty: Property?, val result: NickResult)

    private val profiles = hashMapOf<ProfileKey, ProfileState>()
    private var scanTicks = 0

    init {
        on<WorldEvent.Load.Start> { reset() }

        on<TickEvent.End> {
            if (onP3Sim) return@on
            if (++scanTicks < 20) return@on
            scanTicks = 0

            val onlinePlayers = connection.listedOnlinePlayers
            val activeProfiles = HashSet<ProfileKey>(onlinePlayers.size)
            for (playerInfo in onlinePlayers) {
                val profile = playerInfo.profile
                if (profile.id == player.uuid || profile.id.version() == 2) continue

                val key = ProfileKey(profile.id, profile.name)
                activeProfiles += key

                val textureProperty = profile.textureProperty
                val previous = profiles[key]
                if (previous != null && previous.textureProperty == textureProperty) continue

                val result = profile.nickResult(textureProperty)
                profiles[key] = ProfileState(textureProperty, result)
                if (result == previous?.result) continue

                val message = when (result) {
                    is NickResult.Denicked -> "&e${profile.name} &7is actually &a${result.name}&7."
                    NickResult.Nicked -> "&e${profile.name} &7is nicked."
                    NickResult.NotNicked -> continue
                }
                modMessage(message)
            }

            profiles.keys.retainAll(activeProfiles)
        }
    }

    override fun onEnable() = reset()

    override fun onDisable() = reset()

    private fun reset() {
        profiles.clear()
        scanTicks = 0
    }
}
