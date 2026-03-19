package quoi.module.impl.misc

import net.minecraft.client.resources.sounds.SimpleSoundInstance
import quoi.api.events.ChatEvent
import quoi.module.Module
import quoi.utils.SoundRegistry
import quoi.utils.StringUtils.noControlCodes

object Sweet : Module("Sweet") {
    private val soundVolume by slider("Volume", 100, 0, 500, 1, "Volume of the sound.", unit = "%")
    private val soundPitch by slider("Pitch", 100, 100, 200, 1, "Pitch of the sound.", unit = "%")

    init {
        on<ChatEvent.Packet> {
            if (message.noControlCodes.contains("sweet", ignoreCase = true)) {
                sweet()
            }
        }
    }

    private fun sweet() {
        val v = soundVolume / 100.0f
        val p = soundPitch / 100.0f
        mc.execute { mc.soundManager.play(SimpleSoundInstance.forUI(SoundRegistry.SWEET, p, v)) }
    }
}