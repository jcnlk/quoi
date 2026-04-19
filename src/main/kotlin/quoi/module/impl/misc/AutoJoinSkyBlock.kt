package quoi.module.impl.misc

import quoi.api.events.ServerEvent
import quoi.api.events.TickEvent
import quoi.api.skyblock.Location
import quoi.module.Module
import quoi.utils.ChatUtils

object AutoJoinSkyBlock : Module(
    "Auto Join SkyBlock",
    desc = "Automatically joins SkyBlock after connecting to Hypixel."
) {
    private var armed = false
    private var pendingJoin = false
    private var joinTicks = 0
    private var lastJoinAt = 0L

    init {
        on<ServerEvent.Connect> {
            if (System.currentTimeMillis() - lastJoinAt < 30_000L) return@on

            armed = true
            pendingJoin = false
            joinTicks = 0
            lastJoinAt = System.currentTimeMillis()
        }

        on<ServerEvent.Disconnect> {
            reset()
        }

        on<TickEvent.End> {
            if (!armed) return@on

            if (!pendingJoin) {
                if (!Location.onHypixel) return@on
                pendingJoin = true
                joinTicks = 0
            }

            if (Location.inSkyblock) return@on.also { reset() }

            if (++joinTicks < 20) return@on
            if (mc.player?.connection == null) return@on

            reset()
            ChatUtils.command("skyblock")
        }
    }

    override fun onDisable() {
        reset()
    }

    private fun reset() {
        armed = false
        pendingJoin = false
        joinTicks = 0
    }
}
