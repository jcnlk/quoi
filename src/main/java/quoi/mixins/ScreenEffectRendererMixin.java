package quoi.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import quoi.module.impl.render.RenderOptimiser;
//~ if >=26.2 'renderer.MultiBufferSource' -> 'renderer.SubmitNodeCollector'
import net.minecraft.client.renderer.MultiBufferSource;

@Mixin(ScreenEffectRenderer.class)
public class ScreenEffectRendererMixin {

    @Inject(
            //~ if >=26.2 '"renderFire"' -> '"submitFire"'
            method = "renderFire",
            at = @At("HEAD"),
            cancellable = true
    )
    //~ if >=26.2 'MultiBufferSource multiBufferSource' -> 'SubmitNodeCollector submitNodeCollector'
    private static void onRenderFire(PoseStack poseStack, MultiBufferSource multiBufferSource, TextureAtlasSprite textureAtlasSprite, CallbackInfo ci) {
        if (RenderOptimiser.should(RenderOptimiser.getHideFire())) ci.cancel();
    }
}
