package quoi.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;
import quoi.module.impl.render.RenderOptimiser;
import static quoi.module.impl.render.RenderOptimiser.should;

@Mixin(LightmapRenderStateExtractor.class)
public class LightTextureMixin {

    //$ ambient_light_hook {
    @ModifyExpressionValue(
            method = "extract",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/lang/Math;max(FF)F"
            )
    )
    private float getAmbientLight(float original) {
        return should(RenderOptimiser.getFullBright()) ? 15.0f : original;
    }
    //$}
}
