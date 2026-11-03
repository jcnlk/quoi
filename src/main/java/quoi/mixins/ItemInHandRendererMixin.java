package quoi.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
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
import quoi.module.impl.render.ItemAnimations;
import quoi.module.impl.render.VisualBlocking;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class ItemInHandRendererMixin {

    @ModifyArg(
            method = "submitHandsWithItems",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;rotateDegrees(Lcom/mojang/math/Axis;F)V"),
            index = 1,
            require = 2
    )
    private float quoi$itemAnimationsHandMovement(float angle) {
        return ItemAnimations.disableHandMovement() ? 0f : angle;
    }

    @ModifyExpressionValue(
            method = "submitHandsWithItems",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;swingAnimation:F")
    )
    private float quoi$itemAnimationsSwing(float original, float frameInterp, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state) {
        if (!ItemAnimations.INSTANCE.getEnabled()) return original;
        if (ItemAnimations.isMap(state.attackHand == InteractionHand.MAIN_HAND ? state.mainHandItem : state.offHandItem)) return original;

        return ItemAnimations.getSwingAnimation(frameInterp);
    }

    @ModifyExpressionValue(
            method = "submitHandsWithItems",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/state/level/FirstPersonHandsAndItemsRenderState;attackHand:Lnet/minecraft/world/InteractionHand;")
    )
    private InteractionHand quoi$itemAnimationsSwingHand(InteractionHand original, float frameInterp, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state) {
        if (ItemAnimations.isMap(original == InteractionHand.MAIN_HAND ? state.mainHandItem : state.offHandItem)) return original;
        LivingEntity.SwingDescription swing = ItemAnimations.getSwingDescription(null);
        return swing != null ? swing.hand() : original;
    }

    @ModifyExpressionValue(
            method = "submitArmWithItem",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;currentSwing:Lnet/minecraft/world/entity/LivingEntity$SwingDescription;")
    )
    private LivingEntity.SwingDescription quoi$itemAnimationsSwingDescription(LivingEntity.SwingDescription original, PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state) {
        if (original != null && ItemAnimations.isMap(original.hand() == InteractionHand.MAIN_HAND ? state.mainHandItem : state.offHandItem)) return original;
        return ItemAnimations.getSwingDescription(original);
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
            method = "submitArmWithItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V",
                    shift = At.Shift.AFTER
            )
    )
    private void quoi$itemAnimationsMainTransform(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state, float partialTicks, float xRot, net.minecraft.world.InteractionHand hand, float attack, ItemStack itemStack, float inverseArmHeight, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, CallbackInfo ci) {
        if (hand != net.minecraft.world.InteractionHand.MAIN_HAND || ItemAnimations.isMap(itemStack)) return;
        ItemAnimations.applyTransformations(poseStack);
    }

    @Inject(
            method = "submitArmWithItem",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V")
    )
    private void quoi$itemAnimationsItemScale(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state, float partialTicks, float xRot, net.minecraft.world.InteractionHand hand, float attack, ItemStack itemStack, float inverseArmHeight, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, CallbackInfo ci) {
        AvatarRenderState avatar = playerState.avatarRenderState;
        HumanoidArm arm = hand == net.minecraft.world.InteractionHand.MAIN_HAND ? avatar.mainArm : avatar.mainArm.getOpposite();
        ItemDisplayContext displayContext = arm == HumanoidArm.RIGHT ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        if (hand == InteractionHand.MAIN_HAND) VisualBlocking.applyFirstPerson(poseStack, itemStack, displayContext);
        ItemAnimations.applyScale(poseStack, itemStack, displayContext);
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
