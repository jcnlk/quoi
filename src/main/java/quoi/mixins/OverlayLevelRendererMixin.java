package quoi.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelRenderer.class)
public class OverlayLevelRendererMixin {
    @Unique
    private boolean quoi$hasOverlayGeometry;

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;prepareFrame(Lnet/minecraft/client/renderer/SubmitNodeStorage;)Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;"))
    private FeatureRenderDispatcher.PreparedFrame quoi$captureOverlayPass(FeatureRenderDispatcher dispatcher, SubmitNodeStorage storage, Operation<FeatureRenderDispatcher.PreparedFrame> original) {
        // Preparing drains the phases. Remember their content before that happens.
        quoi$hasOverlayGeometry = storage.getSubmitsPerOrder().values().stream()
                .anyMatch(collection -> !collection.alwaysOnTopGizmos.isEmpty());
        return original.call(dispatcher, storage);
    }

    @Inject(method = "frameHasAlwaysOnTopGizmos", at = @At("RETURN"), cancellable = true)
    private void quoi$includeOverlayGeometry(CallbackInfoReturnable<Boolean> cir) {
        if (quoi$hasOverlayGeometry) cir.setReturnValue(true);
    }
}
