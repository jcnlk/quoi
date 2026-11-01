//~ item_in_hand

package quoi.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import quoi.module.impl.render.ItemAnimations;

@Mixin(ItemInHandRenderer.class)
public abstract class LegacyItemInHandRendererMixin {

    @ModifyArg(
            method = "renderHandsWithItems",
            at = @At(value = "INVOKE", target = "Lcom/mojang/math/Axis;rotationDegrees(F)Lorg/joml/Quaternionf;"),
            index = 0,
            require = 2
    )
    private float quoi$itemAnimationsHandMovement(float angle) {
        return ItemAnimations.disableHandMovement() ? 0f : angle;
    }

    @WrapOperation(
            method = "renderHandsWithItems",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getAttackAnim(F)F"),
            require = 1
    )
    private float quoi$itemAnimationsSwing(LocalPlayer instance, float v, Operation<Float> original) {
        if (!ItemAnimations.INSTANCE.getEnabled()) return original.call(instance, v);
        InteractionHand hand = instance.swingingArm != null ? instance.swingingArm : InteractionHand.MAIN_HAND;
        if (ItemAnimations.isMap(instance.getItemInHand(hand))) return original.call(instance, v);

        return ItemAnimations.getSwingAnimation(v);
    }

    @Inject(
            method = "shouldInstantlyReplaceVisibleItem",
            at = @At("HEAD"),
            cancellable = true
    )
    private void quoi$itemAnimationsReequip(ItemStack itemStack, ItemStack itemStack2, CallbackInfoReturnable<Boolean> cir) {
        if (ItemAnimations.isMap(itemStack) || ItemAnimations.isMap(itemStack2)) return;
        if (ItemAnimations.disableReequip()) cir.setReturnValue(true);
    }

    @WrapOperation(
            method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getItemSwapScale(F)F"),
            require = 1
    )
    private float quoi$itemAnimationsBob(LocalPlayer instance, float v, Operation<Float> original) {
        if (ItemAnimations.isMap(instance.getMainHandItem())) return original.call(instance, v);
        if (ItemAnimations.disableReequip() || ItemAnimations.disableSwingBob()) return 1f;
        return original.call(instance, v);
    }

    @Inject(
            method = "applyItemArmTransform",
            at = @At("HEAD"),
            cancellable = true
    )
    private void quoi$applyEquipOffset(PoseStack poseStack, HumanoidArm humanoidArm, float f, CallbackInfo ci) {
        if (ItemAnimations.disableReequip(humanoidArm)) {
            int i = humanoidArm == HumanoidArm.RIGHT ? 1 : -1;
            poseStack.translate((float)i * 0.56f, -0.52f, -0.72f);
            ci.cancel();
        }
    }

    @Inject(
            method = "renderArmWithItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V",
                    shift = At.Shift.AFTER
            ),
            require = 1
    )
    private void quoi$itemAnimationsMainTransform(CallbackInfo ci, @Local(argsOnly = true) ItemStack itemStack, @Local(argsOnly = true) PoseStack poseStack) {
        if (ItemAnimations.isMap(itemStack)) return;
        ItemAnimations.applyTransformations(poseStack);
    }

    @Inject(
            method = "renderItem",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V")
    )
    private void quoi$itemAnimationsItemScale(LivingEntity livingEntity, ItemStack itemStack, ItemDisplayContext itemDisplayContext, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, CallbackInfo ci) {
        ItemAnimations.applyScale(poseStack, itemStack, itemDisplayContext);
    }

    @ModifyVariable(
            method = "renderPlayerArm",
            at = @At(value = "STORE"),
            ordinal = 4
    )
    private float quoi$disableSwingTrans1(float f, @Local(argsOnly = true) HumanoidArm arm) {
        return ItemAnimations.disableSwingTranslation(arm) ? 0f : f;
    }

    @ModifyVariable(
            method = "renderPlayerArm",
            at = @At(value = "STORE"),
            ordinal = 5
    )
    private float quoi$disableSwingTrans2(float f, @Local(argsOnly = true) HumanoidArm arm) {
        return ItemAnimations.disableSwingTranslation(arm) ? 0f : f;
    }

    @ModifyVariable(
            method = "renderPlayerArm",
            at = @At(value = "STORE"),
            ordinal = 6
    )
    private float quoi$disableSwingTrans3(float f, @Local(argsOnly = true) HumanoidArm arm) {
        return ItemAnimations.disableSwingTranslation(arm) ? 0f : f;
    }

    @WrapOperation(
            method = "swingArm",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V")
    )
    private void quoi$disableSwingTransGeneral(PoseStack instance, float f, float g, float h, Operation<Void> original, @Local(argsOnly = true) HumanoidArm arm) {
        if (ItemAnimations.disableSwingTranslation(arm)) return;
        original.call(instance, f, g, h);
    }
}
