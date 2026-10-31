package quoi.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.feature.CustomFeatureRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.feature.phase.SimpleFeatureRenderPhase;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import quoi.utils.render.CustomRenderLayer;

@Mixin(SubmitNodeCollection.class)
public class OverlaySubmitNodeCollectionMixin {
    @WrapOperation(method = {"submitModel", "submitCustomGeometry"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/phase/SimpleFeatureRenderPhase;submit(Lnet/minecraft/client/renderer/feature/submit/SubmitNode;)V"))
    private void quoi$submitOverlay(SimpleFeatureRenderPhase phase, SubmitNode submit, Operation<Void> original) {
        boolean overlay = submit instanceof ModelFeatureRenderer.Submit<?> model && CustomRenderLayer.isEntityOverlayEsp(model.renderType())
                || submit instanceof CustomFeatureRenderer.Submit custom && CustomRenderLayer.isEntityOverlayEsp(custom.renderType());
        if (overlay) {
            SubmitNodeCollection collection = (SubmitNodeCollection) (Object) this;
            //~ if >=26.3 'collection.alwaysOnTop.' -> 'collection.alwaysOnTopGizmos.'
            collection.alwaysOnTop.submit(submit);
        } else {
            original.call(phase, submit);
        }
    }
}
