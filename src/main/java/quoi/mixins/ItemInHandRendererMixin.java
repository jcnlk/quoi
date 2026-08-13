package quoi.mixins;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionfc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import quoi.module.impl.render.ItemAnimations;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class ItemInHandRendererMixin {

    @ModifyExpressionValue(
            method = "submitHandsWithItems",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;swingAnimation:F")
    )
    private float quoi$itemAnimationsSwing(float original, float frameInterp, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state) {
        if (!ItemAnimations.INSTANCE.getEnabled()) return original;

        if (state.mainHandItem.isEmpty() && !ItemAnimations.affectHand()) {
            return original;
        }
        if (state.mainHandItem.has(DataComponents.MAP_ID) && !ItemAnimations.affectMap()) {
            return original;
        }

        return ItemAnimations.getSwingAnimation(frameInterp);
    }

    @Inject(
            method = "applyItemArmTransform",
            at = @At("HEAD"),
            cancellable = true
    )
    private void quoi$applyEquipOffset(PoseStack poseStack, HumanoidArm humanoidArm, float f, CallbackInfo ci) {
        if (ItemAnimations.disableReequip()) {
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
        if (hand != net.minecraft.world.InteractionHand.MAIN_HAND) return;
        if (itemStack.isEmpty() && !ItemAnimations.affectHand()) return;
        if (itemStack.has(DataComponents.MAP_ID) && !ItemAnimations.affectMap()) return;
        ItemAnimations.applyTransformations(poseStack, itemStack);
    }

    @Inject(
            method = "renderPlayerArm",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/FirstPersonHandsAndItemsRenderer;renderPlayerHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/world/entity/HumanoidArm;Lnet/minecraft/client/renderer/state/level/PlayerRenderState;)V")
    )
    private void quoi$itemAnimationsArmScale(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, float f, float g, HumanoidArm humanoidArm, PlayerRenderState playerState, CallbackInfo ci) {
        if (!ItemAnimations.affectHand()) return;
        ItemAnimations.applyScale(poseStack);
    }

    @Inject(
            method = "submitArmWithItem",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V")
    )
    private void quoi$itemAnimationsItemScale(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state, float partialTicks, float xRot, net.minecraft.world.InteractionHand hand, float attack, ItemStack itemStack, float inverseArmHeight, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, CallbackInfo ci) {
        AvatarRenderState avatar = playerState.avatarRenderState;
        HumanoidArm arm = hand == net.minecraft.world.InteractionHand.MAIN_HAND ? avatar.mainArm : avatar.mainArm.getOpposite();
        ItemDisplayContext displayContext = arm == HumanoidArm.RIGHT ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        ItemAnimations.applyScale(poseStack, itemStack, displayContext);
    }

    @WrapWithCondition(
            method = "submitHandsWithItems",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionfc;)V")
    )
    private boolean quoi$itemAnimationsSway(PoseStack instance, Quaternionfc quaternionfc) {
        return !ItemAnimations.disableHandSway();
    }

    @ModifyVariable(
            method = "renderPlayerArm",
            at = @At(value = "STORE"),
            ordinal = 4
    )
    private float quoi$disableSwingTrans1(float f) {
        return ItemAnimations.disableSwingTranslation() ? 0f : f;
    }

    @ModifyVariable(
            method = "renderPlayerArm",
            at = @At(value = "STORE"),
            ordinal = 5
    )
    private float quoi$disableSwingTrans2(float f) {
        return ItemAnimations.disableSwingTranslation() ? 0f : f;
    }

    @ModifyVariable(
            method = "renderPlayerArm",
            at = @At(value = "STORE"),
            ordinal = 6
    )
    private float quoi$disableSwingTrans3(float f) {
        return ItemAnimations.disableSwingTranslation() ? 0f : f;
    }

    @WrapOperation(
            method = "swingArm",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V")
    )
    private void quoi$disableSwingTransGeneral(PoseStack instance, float f, float g, float h, Operation<Void> original) {
        if (ItemAnimations.disableSwingTranslation()) return;
        original.call(instance, f, g, h);
    }

    @Inject(
            method = "applyEatTransform",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/lang/Math;pow(DD)D",
                    shift = At.Shift.BEFORE
            ),
            cancellable = true
    )
    private void quoi$cancelEatTransform(PoseStack poseStack, float partialTicks, HumanoidArm humanoidArm, float useItemRemainingTicks, int useDuration, CallbackInfo ci) {
        if (ItemAnimations.disableEat()){
            ci.cancel();
        }
    }

    @Inject(
            method = "renderMapHand",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/FirstPersonHandsAndItemsRenderer;renderPlayerHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/world/entity/HumanoidArm;Lnet/minecraft/client/renderer/state/level/PlayerRenderState;)V")
    )
    private void quoi$mapScale1(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, HumanoidArm humanoidArm, PlayerRenderState playerState, CallbackInfo ci) {
        if (!ItemAnimations.affectMap()) return;
        ItemAnimations.applyScale(poseStack);
    }

    @Inject(
            method = "renderMap",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitCustomGeometry(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;Lnet/minecraft/client/renderer/SubmitNodeCollector$CustomGeometryRenderer;)V")
    )
    private void quoi$mapScale2(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, ItemStack itemStack, boolean mainHand, FirstPersonHandsAndItemsRenderState state, CallbackInfo ci) {
        if (!ItemAnimations.INSTANCE.getEnabled()) return;
        if (!ItemAnimations.affectMap()) return;
        poseStack.translate(64f, 64f, 0f);
        ItemAnimations.applyScale(poseStack);
        poseStack.translate(-64f, -64f, 0f);
    }
}