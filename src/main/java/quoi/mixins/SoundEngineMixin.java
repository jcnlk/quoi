package quoi.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import quoi.module.impl.player.Tweaks;

@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {

    @ModifyExpressionValue(
        method = "calculateVolume(FLnet/minecraft/sounds/SoundSource;)F",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/Options;getFinalSoundSourceVolume(Lnet/minecraft/sounds/SoundSource;)F"
        )
    )
    private float quoi$muteSoundsWhileUnfocused(float original) {
        Minecraft minecraft = Minecraft.getInstance();
        return Tweaks.should(Tweaks.getMuteSounds()) && !minecraft.isWindowActive() ? 0.0F : original;
    }
}
