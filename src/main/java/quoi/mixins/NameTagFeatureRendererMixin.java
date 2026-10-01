package quoi.mixins;

import net.minecraft.client.renderer.SubmitNodeCollection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import quoi.module.impl.render.NameTags;
//$ nametag_import {
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
//$}

//$ nametag_mixin_target {
@Mixin(NameTagFeatureRenderer.class)
//$}
public class NameTagFeatureRendererMixin {

    //$ nametag_hooks {
    @ModifyArgs(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/network/chat/Component;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)V"
            )
    )
    private void draw(Args args) {
        if (!NameTags.INSTANCE.getEnabled()) return;
        args.set(4, NameTags.getShadow());
        if (NameTags.getCustomBg()) args.set(8, NameTags.getBgColour().getRgb());
    }
    //$}
}
