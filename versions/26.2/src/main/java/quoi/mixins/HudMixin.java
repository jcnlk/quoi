package quoi.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import quoi.module.impl.general.PlayerDisplay;
import quoi.module.impl.general.PlayerDisplay.HudType;

@Mixin(Hud.class)
public class HudMixin {

    @Redirect(
            method = "extractPlayerHealth",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;getAbsorptionAmount()F"
            )
    )
    private float hideAbsorption(Player instance) {
        return PlayerDisplay.shouldCancelHud(HudType.ABSORPTION) ? 0.0F : instance.getAbsorptionAmount();
    }

    @Inject(method = "extractArmor", at = @At("HEAD"), cancellable = true)
    private static void cancelArmorBar(GuiGraphicsExtractor context, Player player, int i, int j, int k, int x, CallbackInfo ci) {
        if (PlayerDisplay.shouldCancelHud(HudType.ARMOUR)) ci.cancel();
    }

    @Inject(method = "extractHearts", at = @At("HEAD"), cancellable = true)
    private void cancelHealthBar(GuiGraphicsExtractor context, Player player, int x, int y, int lines, int regeneratingHeartIndex, float maxHealth, int lastHealth, int health, int absorption, boolean blinking, CallbackInfo ci) {
        if (PlayerDisplay.shouldCancelHud(HudType.HEALTH)) ci.cancel();
    }

    @Inject(method = "extractFood", at = @At("HEAD"), cancellable = true)
    private void cancelFoodBar(GuiGraphicsExtractor context, Player player, int top, int right, CallbackInfo ci) {
        if (PlayerDisplay.shouldCancelHud(HudType.FOOD)) ci.cancel();
    }

    @Inject(method = "extractVehicleHealth", at = @At("HEAD"), cancellable = true)
    private void cancelMountHealth(GuiGraphicsExtractor guiGraphics, CallbackInfo ci) {
        if (PlayerDisplay.shouldCancelHud(HudType.MOUNT_HEALTH)) ci.cancel();
    }

    @ModifyExpressionValue(
            method = "extractPlayerHealth",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;hasEffect(Lnet/minecraft/core/Holder;)Z"
            )
    )
    private boolean disableRegenBounce(boolean original) {
        return !PlayerDisplay.shouldCancelHud(HudType.REGEN_BOUNCE) && original;
    }
}
