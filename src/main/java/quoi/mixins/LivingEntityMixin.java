package quoi.mixins;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import quoi.module.impl.render.RenderOptimiser;

import static quoi.module.impl.render.RenderOptimiser.should;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {

    @Redirect(
            method = "tickEffects",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V")
    )
    private void cancelPotionEffectParticles(Level level, ParticleOptions particle, double x, double y, double z, double vx, double vy, double vz) {
        if (!should(RenderOptimiser.getHidePotionBubbles())) {
            level.addParticle(particle, x, y, z, vx, vy, vz);
        }
    }
}
