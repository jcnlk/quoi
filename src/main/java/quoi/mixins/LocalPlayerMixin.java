package quoi.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import quoi.api.world.Direction;
import quoi.module.impl.misc.ItemAnimations;
import quoi.utils.skyblock.player.RotationUtils;

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
}
