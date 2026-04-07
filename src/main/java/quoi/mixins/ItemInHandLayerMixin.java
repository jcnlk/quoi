package quoi.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import quoi.module.impl.misc.ItemAnimations;

@Mixin(ItemInHandLayer.class)
public class ItemInHandLayerMixin {

    @Inject(
            method = "submitArmWithItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/model/ArmedModel;translateToHand(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
                    shift = Shift.AFTER
            )
    )
    private void quoi$itemAnimationsThirdPerson(ArmedEntityRenderState armedEntityRenderState, ItemStackRenderState itemStackRenderState, ItemStack itemStack, HumanoidArm humanoidArm, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, CallbackInfo ci) {
        if (!(armedEntityRenderState instanceof AvatarRenderState avatarRenderState)) return;
        ItemAnimations.applyThirdPersonPlayerTransformations(poseStack, itemStack, avatarRenderState.id);
    }
}
