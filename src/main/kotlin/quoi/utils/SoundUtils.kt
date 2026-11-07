package quoi.utils

import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import quoi.QuoiMod.mc

object SoundUtils {

    fun play(sound: SoundEvent, volume: Float = 1.0f, pitch: Float = 1.0f) = mc.execute {
        player.playSound(sound, volume, pitch)
    }

    fun play(soundSettings: () -> Triple<SoundEvent, Float, Float>) {
        val (sound, volume, pitch) = soundSettings()
        play(sound, volume, pitch)
    }

    enum class SoundSetting(val sound: SoundEvent) {
        BlazeHurt(SoundEvents.BLAZE_HURT),
        Pling(SoundEvents.NOTE_BLOCK_PLING.value()),
        OrbPickup(SoundEvents.EXPERIENCE_ORB_PICKUP),
        LevelUp(SoundEvents.PLAYER_LEVELUP),
        AnvilLand(SoundEvents.ANVIL_LAND),
        Custom(SoundEvents.BLAZE_HURT)
    }
}
