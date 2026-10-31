package quoi.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import quoi.api.world.Direction;
import quoi.module.impl.render.NameTags;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import quoi.utils.skyblock.player.RotationUtils;
import quoi.utils.render.CustomRenderLayer;
import quoi.utils.render.EntityOverlayState;
import quoi.utils.render.EntityOverlayRenderer;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity, S extends LivingEntityRenderState, M extends EntityModel<? super S>> {

    @Shadow public abstract Identifier getTextureLocation(S state);

    @WrapMethod(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V")
    private void quoi$overlayLayers(S state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, Operation<Void> original) {
        EntityOverlayState previous = EntityOverlayRenderer.setState((EntityOverlayState) state);
        try {
            original.call(state, poseStack, collector, camera);
        } finally {
            EntityOverlayRenderer.setState(previous);
        }
    }

    @ModifyExpressionValue(
            method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;getRenderType(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;ZZZ)Lnet/minecraft/client/renderer/rendertype/RenderType;")
    )
    private RenderType quoi$overlayRenderType(RenderType original, S state) {
        EntityOverlayState overlay = (EntityOverlayState) state;
        if ((overlay.quoi$getOverlayColour() >>> 24) == 0) return original;
        return CustomRenderLayer.entityOverlay(getTextureLocation(state), overlay.quoi$getOverlayDepth());
    }

    @Inject(
            method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z",
            at = @At(value = "HEAD"),
            cancellable = true
    )
    private void shouldShowName(T livingEntity, double d, CallbackInfoReturnable<Boolean> cir) {
        if (NameTags.getShouldCancelTag()) cir.cancel();
    }

    @ModifyExpressionValue(
            method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/Mth;rotLerp(FFF)F"
            )
    )
    private float headYaw(float original, LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
        if (entity != Minecraft.getInstance().player) return original;

        Direction dir = RotationUtils.getServerDirection();
        return dir != null ? dir.getYaw() : original;
    }

    @ModifyExpressionValue(
            method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;getXRot(F)F"
            )
    )
    private float headPitch(float original, LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
        if (entity != Minecraft.getInstance().player) return original;

        Direction dir = RotationUtils.getServerDirection();
        return dir != null ? dir.getPitch() : original;
    }
}
