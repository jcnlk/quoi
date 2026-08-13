package quoi.mixins;

import quoi.module.impl.render.NameTags;
import net.minecraft.client.renderer.SubmitNodeCollection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(SubmitNodeCollection.class)
public class NameTagFeatureRendererMixin {

    @ModifyArg(
            method = "nameTag",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/feature/TextFeatureRenderer$Content$Text;<init>(FFLnet/minecraft/util/FormattedCharSequence;ZIII)V"
            ),
            index = 3
    )
    private static boolean quoi$modifyShadow(boolean shadow) {
        return NameTags.INSTANCE.getEnabled() ? NameTags.getShadow() : shadow;
    }

    @ModifyArg(
            method = "nameTag",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/feature/TextFeatureRenderer$Content$Text;<init>(FFLnet/minecraft/util/FormattedCharSequence;ZIII)V"
            ),
            index = 5
    )
    private static int quoi$modifyBackground(int backgroundColor) {
        return NameTags.INSTANCE.getEnabled() && NameTags.getCustomBg() ? NameTags.getBgColour().getRgb() : backgroundColor;
    }
}
