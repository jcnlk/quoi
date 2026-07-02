package quoi.mixins;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import quoi.api.events.EntityEvent;
import quoi.api.skyblock.location.Island;
import quoi.api.skyblock.location.Location;
import quoi.api.skyblock.dungeon.Dungeon;
import quoi.api.skyblock.dungeon.M7Phases;
import quoi.module.impl.render.RenderOptimiser;

@Mixin(ClientLevel.class)
public class ClientLevelMixin {

    @Inject(
            method = "removeEntity",
            at = @At("HEAD"),
            cancellable = true
    )
    private void quoi$onRemoveEntity(int entityId, Entity.RemovalReason removalReason, CallbackInfo ci) {
        Entity entity = ((ClientLevel) (Object) this).getEntity(entityId);
        if (entity != null && new EntityEvent.Leave(entity, removalReason).post()) ci.cancel();
    }

    @Inject(
            method = "addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void quoi$hideParticle(ParticleOptions particle, double x, double y, double z, double vx, double vy, double vz, CallbackInfo ci) {
        if (RenderOptimiser.should(RenderOptimiser.getHideParticles()) &&
                !Location.INSTANCE.getCurrentArea().isArea(Island.Garden) &&
                Dungeon.INSTANCE.getF7Phase() != M7Phases.P5 ||
                RenderOptimiser.should(RenderOptimiser.getHidePotionBubbles()) &&
                particle.getType() == ParticleTypes.ENTITY_EFFECT) ci.cancel();
    }

    @Inject(
            method = "addParticle(Lnet/minecraft/core/particles/ParticleOptions;ZZDDDDDD)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void quoi$hideParticle(ParticleOptions particle, boolean overrideLimiter, boolean alwaysShow, double x, double y, double z, double vx, double vy, double vz, CallbackInfo ci) {
        if (RenderOptimiser.should(RenderOptimiser.getHideParticles()) &&
                !Location.INSTANCE.getCurrentArea().isArea(Island.Garden) &&
                Dungeon.INSTANCE.getF7Phase() != M7Phases.P5 ||
                RenderOptimiser.should(RenderOptimiser.getHidePotionBubbles()) &&
                particle.getType() == ParticleTypes.ENTITY_EFFECT) ci.cancel();
    }

    @Inject(
            method = "addAlwaysVisibleParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void quoi$hideAlwaysVisibleParticle(ParticleOptions particle, double x, double y, double z, double vx, double vy, double vz, CallbackInfo ci) {
        if (RenderOptimiser.should(RenderOptimiser.getHideParticles()) &&
                !Location.INSTANCE.getCurrentArea().isArea(Island.Garden) &&
                Dungeon.INSTANCE.getF7Phase() != M7Phases.P5 ||
                RenderOptimiser.should(RenderOptimiser.getHidePotionBubbles()) &&
                particle.getType() == ParticleTypes.ENTITY_EFFECT) ci.cancel();
    }

    @Inject(
            method = "addAlwaysVisibleParticle(Lnet/minecraft/core/particles/ParticleOptions;ZDDDDDD)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void quoi$hideAlwaysVisibleParticle(ParticleOptions particle, boolean overrideLimiter, double x, double y, double z, double vx, double vy, double vz, CallbackInfo ci) {
        if (RenderOptimiser.should(RenderOptimiser.getHideParticles()) &&
                !Location.INSTANCE.getCurrentArea().isArea(Island.Garden) &&
                Dungeon.INSTANCE.getF7Phase() != M7Phases.P5 ||
                RenderOptimiser.should(RenderOptimiser.getHidePotionBubbles()) &&
                particle.getType() == ParticleTypes.ENTITY_EFFECT) ci.cancel();
    }
}
