package quoi.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.special.PlayerHeadSpecialRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import quoi.mixins.accessors.ItemStackLayerRenderStateAccessor;
import quoi.module.impl.general.Tweaks;

@Mixin(ItemStackRenderState.class)
public class ItemStackRenderStateMixin {
    @Shadow ItemDisplayContext displayContext;
    @Shadow private int activeLayerCount;
    @Shadow private ItemStackRenderState.LayerRenderState[] layers;

    @Inject(method = "submit", at = @At("HEAD"))
    private void quoi$legacySkullSize(PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
                                      int lightCoords, int overlayCoords, int outlineColor, CallbackInfo ci) {
        for (int i = 0; i < activeLayerCount; i++) {
            var renderer = ((ItemStackLayerRenderStateAccessor) layers[i]).quoi$getSpecialRenderer();
            if (!(renderer instanceof PlayerHeadSpecialRenderer)) continue;
            Tweaks.applyLegacySkullSize(poseStack, displayContext);
            return;
        }
    }
}
