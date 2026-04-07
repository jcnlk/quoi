package quoi.mixins;

import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import quoi.module.impl.misc.ItemAnimations;

@Mixin(AvatarRenderer.class)
public class AvatarRendererMixin {

    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("RETURN")
    )
    private void quoi$itemAnimationsThirdPersonSwing(Avatar avatar, AvatarRenderState avatarRenderState, float tickProgress, CallbackInfo ci) {
        avatarRenderState.attackTime = ItemAnimations.getThirdPersonSwingAnimation(avatarRenderState.attackTime, avatarRenderState.getMainHandItemStack(), avatarRenderState.id);
    }
}
