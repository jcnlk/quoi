package quoi.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.PlayerItemInHandLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import quoi.module.impl.misc.ItemAnimations;

@Mixin(PlayerItemInHandLayer.class)
public class PlayerItemInHandLayerMixin {

    @Inject(
            method = "renderItemHeldToEye",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V")
    )
    private void quoi$itemAnimationsThirdPersonEyeItem(AvatarRenderState avatarRenderState, HumanoidArm humanoidArm, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, CallbackInfo ci) {
        ItemAnimations.applyThirdPersonPlayerTransformations(poseStack, avatarRenderState.getUseItemStackForArm(humanoidArm), avatarRenderState.id);
    }
}
