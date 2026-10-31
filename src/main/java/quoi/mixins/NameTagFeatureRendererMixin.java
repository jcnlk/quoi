//~ name_tag_rendering

package quoi.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import quoi.module.impl.render.NameTags;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;

@Mixin(NameTagFeatureRenderer.class)
public class NameTagFeatureRendererMixin {

    //? if <26.2 {
    @ModifyArgs(
            method = "renderTranslucent",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/network/chat/Component;FFIZLorg/joml/Matrix4fc;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)V"
            ),
            require = 2,
            expect = 2
    )
    private void draw(Args args) {
        if (!NameTags.INSTANCE.getEnabled()) return;
        args.set(4, NameTags.getShadow());
        if (NameTags.getCustomBg()) args.set(8, NameTags.getBgColour().getRgb());
    }
    //?} else {
    /*@ModifyArg(
            method = "prepareText",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Font;prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;"
            ),
            //~ if >=26.3 'index = 4' -> 'index = 3'
            index = 4
    )
    private static boolean quoi$modifyShadow(boolean shadow) {
        return NameTags.INSTANCE.getEnabled() ? NameTags.getShadow() : shadow;
    }

    @ModifyArg(
            method = "prepareText",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Font;prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;"
            ),
            //~ if >=26.3 'index = 6' -> 'index = 5'
            index = 6
    )
    private static int quoi$modifyBackground(int backgroundColor) {
        return NameTags.INSTANCE.getEnabled() && NameTags.getCustomBg() ? NameTags.getBgColour().getRgb() : backgroundColor;
    }
    *///?}
}
