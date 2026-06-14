package quoi.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import quoi.module.impl.render.RenderOptimiser;
import quoi.module.impl.player.Tweaks;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import static quoi.module.impl.render.RenderOptimiser.should;

@Mixin(FogRenderer.class)
public class FogRendererMixin {

    @ModifyVariable(
            method = "getBuffer",
            at = @At("HEAD"),
            ordinal = 0,
            argsOnly = true
    )
    private FogRenderer.FogMode disableFog(FogRenderer.FogMode value) {
        return should(RenderOptimiser.getDisableFog()) ? FogRenderer.FogMode.NONE : value;
    }

    @WrapOperation(
            method = "computeFogColor",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GameRenderer;getNightVisionScale(Lnet/minecraft/world/entity/LivingEntity;F)F"
            )
    )
    private float fixCrimsonIsleFog(LivingEntity entity, float partialTick, Operation<Float> original) {
        return Tweaks.shouldFixCrimsonIsleFog() ? 0.0F : original.call(entity, partialTick);
    }
}
