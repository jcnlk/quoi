package quoi.mixins;

import quoi.module.impl.render.NameTags;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(NameTagFeatureRenderer.class)
public class NameTagFeatureRendererMixin {

    @ModifyArg(
            method = "prepareText",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Font;prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;"
            ),
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
            index = 6
    )
    private static int quoi$modifyBackground(int backgroundColor) {
        return NameTags.INSTANCE.getEnabled() && NameTags.getCustomBg() ? NameTags.getBgColour().getRgb() : backgroundColor;
    }
}
