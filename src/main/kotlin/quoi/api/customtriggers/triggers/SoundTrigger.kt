package quoi.api.customtriggers.triggers

import quoi.api.abobaui.constraints.impl.size.Copying
import quoi.api.abobaui.dsl.*
import quoi.api.abobaui.elements.ElementScope
import quoi.api.customtriggers.TriggerContext
import quoi.api.customtriggers.optionalSliderField
import quoi.api.customtriggers.settingRow
import quoi.api.customtriggers.textField
import quoi.config.TypeName

@TypeName("sound_played")
class SoundTrigger(
    var soundName: String = "",
    var volume: Float? = null,
    var pitch: Float? = null
) : Trigger {
    override val eventKind get() = TriggerContext.Kind.SOUND

    override fun validationError(): String? = when {
        net.minecraft.resources.Identifier.tryParse(soundName) == null -> "Enter a valid sound ID."
        volume?.let { !it.isFinite() || it < 0f } == true -> "Volume must be non-negative."
        pitch?.let { !it.isFinite() || it < 0f } == true -> "Pitch must be non-negative."
        else -> null
    }

    override fun matches(ctx: TriggerContext): Boolean {
        if (ctx !is TriggerContext.Sound) return false

        if (ctx.name != net.minecraft.resources.Identifier.tryParse(soundName)?.toString()) return false
        if (volume != null && ctx.volume != volume) return false
        if (pitch != null && ctx.pitch != pitch) return false

        return true
    }

    override fun displayString() = buildString {
        append("Sound \"$soundName\"")

        val params = listOfNotNull(
            volume?.let { "volume = $it" },
            pitch?.let { "pitch = $it" }
        )

        if (params.isNotEmpty()) {
            append("(${params.joinToString(", ")})")
        }

        append(" played")
    }

    override fun ElementScope<*>.draw() = column(size(w = Copying), gap = 8.px) {
        textField("Sound ID", soundName) { soundName = it }
        settingRow(
            { optionalSliderField("Volume", ::volume, 10f) },
            { optionalSliderField("Pitch", ::pitch, 2f) }
        )
    }
}
