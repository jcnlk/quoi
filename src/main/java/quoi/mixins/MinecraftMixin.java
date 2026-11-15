package quoi.mixins;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.SwingAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import quoi.api.events.EntityEvent;
import quoi.api.events.PlayerEvent;

@Mixin(Minecraft.class)
public class MinecraftMixin {

    @Unique
    private static boolean cancelSwing = false;

    // based on https://github.com/Noamm9/NoammAddons/blob/3fe8feff94494d48a8d2fa76ee8b6eff1f57e70c/src/main/java/com/github/noamm9/mixin/MixinMinecraft.java
    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void quoi$onRightClick(CallbackInfo ci) {
        if (quoi$postInteract(false)) ci.cancel();
    }

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void quoi$onLeftClick(CallbackInfoReturnable<Boolean> cir) {
        if (quoi$postInteract(true)) cir.setReturnValue(false);
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void quoi$onContinueAttack(boolean pressed, CallbackInfo ci) {
        if (pressed && quoi$postInteract(true)) ci.cancel();
    }

    @Unique
    private boolean quoi$postInteract(boolean leftClick) {
        Minecraft client = (Minecraft) (Object) this;
        if (client.player == null || client.level == null) return false;
        PlayerEvent.Interact event = leftClick
                ? new PlayerEvent.Interact.LeftClick(client.player.getMainHandItem(), client.hitResult)
                : new PlayerEvent.Interact.RightClick(client.player.getMainHandItem(), InteractionHand.MAIN_HAND, client.hitResult);
        return event.post();
    }

    @Redirect(
            method = "startAttack()Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;attack(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;)V"
            )
    )
    private void redirectAttack(MultiPlayerGameMode gameMode, Player player, Entity entity) {
        if (new EntityEvent.Attack(entity).post()) {
            cancelSwing = true;
        } else {
            gameMode.attack(player, entity);
        }
    }

    @Redirect(
            method = "startAttack()Z",
            at = @At(
                    value = "INVOKE",
                    //~ if >=26.3 'InteractionHand;)V' -> 'InteractionHand;Lnet/minecraft/world/item/component/SwingAnimation;Z)Z'
                    target = "Lnet/minecraft/client/player/LocalPlayer;swing(Lnet/minecraft/world/InteractionHand;)V"
            )
    )
    //~ if >=26.3 'void redirectSwing' -> 'boolean redirectSwing'
    //~ if >=26.3 'InteractionHand hand)' -> 'InteractionHand hand, SwingAnimation animation, boolean sendToSwingingEntity)'
    private void redirectSwing(LocalPlayer instance, InteractionHand hand) {
        if (cancelSwing) {
            cancelSwing = false;
            //? if >=26.3
            //return false;
        } else {
            //? if <26.3 {
            instance.swing(hand);
            //?} else {
            /*return instance.swing(hand, animation, sendToSwingingEntity);
            *///?}
        }
    }
}
