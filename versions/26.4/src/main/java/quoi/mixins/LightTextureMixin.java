package quoi.mixins;

import quoi.module.impl.render.RenderOptimiser;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import static quoi.module.impl.render.RenderOptimiser.should;

@Mixin(LightmapRenderStateExtractor.class)
public class LightTextureMixin {

    @WrapOperation(
            method = "extract",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/renderer/state/LightmapRenderState;ambientColor:Lorg/joml/Vector3fc;",
                    opcode = Opcodes.PUTFIELD
            ),
            require = 1
    )
    private void setAmbientLight(LightmapRenderState state, Vector3fc color, Operation<Void> original) {
        original.call(state, should(RenderOptimiser.getFullBright()) ? new Vector3f(15.0f) : color);
    }
}
