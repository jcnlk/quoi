package quoi.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import quoi.module.impl.render.RenderOptimiser;
//$ fire_render_import {
import net.minecraft.client.renderer.MultiBufferSource;
//$}

@Mixin(ScreenEffectRenderer.class)
public class ScreenEffectRendererMixin {

    @Inject(
            //$ fire_render_target {
            method = "renderFire",
            //$}
            at = @At("HEAD"),
            cancellable = true
    )
    //$ fire_render_handler {
    private static void onRenderFire(PoseStack poseStack, MultiBufferSource multiBufferSource, TextureAtlasSprite textureAtlasSprite, CallbackInfo ci) {
    //$}
        if (RenderOptimiser.should(RenderOptimiser.getHideFire())) ci.cancel();
    }
}
