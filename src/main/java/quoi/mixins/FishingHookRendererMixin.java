package quoi.mixins;

import net.minecraft.client.renderer.entity.FishingHookRenderer;
import net.minecraft.client.renderer.entity.state.FishingHookRenderState;
import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import quoi.module.impl.misc.ItemAnimations;

@Mixin(FishingHookRenderer.class)
public class FishingHookRendererMixin {

    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/projectile/FishingHook;Lnet/minecraft/client/renderer/entity/state/FishingHookRenderState;F)V",
            at = @At("RETURN")
    )
    private void quoi$itemAnimationsRodLine(FishingHook fishingHook, FishingHookRenderState fishingHookRenderState, float f, CallbackInfo ci) {
        ItemAnimations.applyFishingRodLineTransformations(fishingHook, fishingHookRenderState);
    }
}
