package quoi.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import quoi.api.events.EntityEvent;
import quoi.api.world.Direction;
import quoi.module.impl.misc.ItemAnimations;
import quoi.utils.skyblock.player.RotationUtils;

import java.util.function.Predicate;

@Mixin(LocalPlayer.class)
public class LocalPlayerMixin extends AbstractClientPlayer {

    public LocalPlayerMixin(ClientLevel world, GameProfile profile) {
        super(world, profile);
    }

    @Inject(
            method = "swing",
            at = @At("HEAD")
    )
    private void quoi$onSwing(InteractionHand hand, CallbackInfo ci) {
        ItemAnimations.onSwing();
    }

    @ModifyExpressionValue(
            method = {"sendPosition", "tick"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;getYRot()F"
            )
    )
    private float silentRotationYaw(float original) {
        Direction dir = RotationUtils.getServerDirection();
        if (dir == null) return original;
        return dir.getYaw();
    }

    @ModifyExpressionValue(
            method = {"sendPosition", "tick"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;getXRot()F"
            )
    )
    private float silentRotationPitch(float original) {
        Direction dir = RotationUtils.getServerDirection();
        if (dir == null) return original;
        return dir.getPitch();
    }

    @ModifyExpressionValue(
            method = "raycastHitResult",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/entity/EntitySelector;CAN_BE_PICKED:Ljava/util/function/Predicate;"
            )
    )
    private Predicate<Entity> filterAttackRangePick(Predicate<Entity> original) {
        return filterPickableEntities(original);
    }

    @ModifyExpressionValue(
            method = "pick",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/entity/EntitySelector;CAN_BE_PICKED:Ljava/util/function/Predicate;"
            )
    )
    private static Predicate<Entity> filterStandardPick(Predicate<Entity> original) {
        return filterPickableEntities(original);
    }

    @Unique
    private static Predicate<Entity> filterPickableEntities(Predicate<Entity> original) {
        return entity -> original.test(entity) && !new EntityEvent.Pick(entity).post();
    }
}
