package quoi.mixins;

import quoi.module.impl.render.RenderOptimiser;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

import static quoi.module.impl.render.RenderOptimiser.should;

@Mixin(LightmapRenderStateExtractor.class)
public class LightTextureMixin {

    @ModifyExpressionValue(
            method = "extract",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/ARGB;vector3fFromRGB24(I)Lorg/joml/Vector3f;"
            ),
            slice = @Slice(
                    from = @At(
                            value = "FIELD",
                            target = "Lnet/minecraft/world/attribute/EnvironmentAttributes;AMBIENT_LIGHT_COLOR:Lnet/minecraft/world/attribute/EnvironmentAttribute;"
                    )
            )
    )
    private Vector3f getAmbientLight(Vector3f original) {
        return should(RenderOptimiser.getFullBright()) ? new Vector3f(15.0f) : original;
    }
}
