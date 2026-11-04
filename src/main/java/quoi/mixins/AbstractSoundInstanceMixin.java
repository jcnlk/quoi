package quoi.mixins;

import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import quoi.module.impl.misc.catmode.CatMode;

@Mixin(AbstractSoundInstance.class)
public class AbstractSoundInstanceMixin {
    @ModifyArg(
            //? if >=26.4 {
            /*method = "resolveAndSelectSound",
            *///?} else if >=26.3 {
            /*method = "getOrResolve",
            *///?} else {
            method = "resolve",
            //?}
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/sounds/SoundManager;getSoundEvent(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/sounds/WeighedSoundEvents;"
            ),
            index = 0
    )
    private Identifier quoi$meowSound(Identifier sound) {
        return CatMode.replaceSound(sound);
    }
}
