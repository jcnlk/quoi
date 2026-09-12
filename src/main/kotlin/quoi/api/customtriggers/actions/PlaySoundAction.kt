package quoi.api.customtriggers.actions

import quoi.api.abobaui.elements.ElementScope
import quoi.api.customtriggers.*
import quoi.config.TypeName
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvent
import quoi.utils.SoundUtils

@TypeName("play_sound")
class PlaySoundAction(var sound: String = "minecraft:entity.experience_orb.pickup", var volume: Float = 1f, var pitch: Float = 1f) : TriggerAction {
    override fun validationError() = when {
        Identifier.tryParse(sound) == null -> "Enter a valid sound ID."
        !volume.isFinite() || volume !in 0f..10f -> "Volume must be between 0 and 10."
        !pitch.isFinite() || pitch !in 0.5f..2f -> "Pitch must be between 0.5 and 2."
        else -> null
    }

    override fun execute(ctx: TriggerContext) {
        val id = Identifier.tryParse(sound) ?: return
        SoundUtils.play(SoundEvent.createVariableRangeEvent(id), volume, pitch)
    }
    override fun displayString() = "Play $sound"
    override fun ElementScope<*>.draw() = settingRow(
        { textField("Sound", sound) { sound = it } },
        { sliderField("Volume", ::volume, 0f, 10f) },
        { sliderField("Pitch", ::pitch, 0.5f, 2f) }
    )
}
