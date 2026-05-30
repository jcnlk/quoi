package quoi.mixins;

import quoi.module.impl.render.RenderOptimiser;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import static quoi.module.impl.render.RenderOptimiser.should;

@Mixin(LightmapRenderStateExtractor.class)
public class LightTextureMixin {

    @ModifyExpressionValue(
            method = "extract",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/lang/Math;max(FF)F"
            )
    )
    private float getAmbientLight(float original) {
        return should(RenderOptimiser.getFullBright()) ? 1.0f : original;
    }
}
