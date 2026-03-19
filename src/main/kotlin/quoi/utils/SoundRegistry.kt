package quoi.utils

import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent
import quoi.QuoiMod

object SoundRegistry {

    val SWEET = register("sweet")

    private fun register(name: String): SoundEvent {
        val id = ResourceLocation.fromNamespaceAndPath(QuoiMod.MOD_ID, name)
        return Registry.register(
            BuiltInRegistries.SOUND_EVENT,
            id,
            SoundEvent.createVariableRangeEvent(id)
        )
    }

    fun initialize() {
        QuoiMod.logger.info("Registered custom sounds for ${QuoiMod.MOD_ID}")
    }
}