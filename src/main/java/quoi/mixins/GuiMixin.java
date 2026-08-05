package quoi.mixins;

import quoi.module.impl.general.PlayerDisplay;
import quoi.module.impl.general.PlayerDisplay.HudType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class GuiMixin {

    @Redirect(
            method = "extractPlayerHealth",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;getAbsorptionAmount()F"
            )
    )
    private float hideAbsorption(Player instance) {
        if (PlayerDisplay.shouldCancelHud(HudType.ABSORPTION)) {
            return 0.0F;
        }
        return instance.getAbsorptionAmount();
    }

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Gui;renderSleepOverlay(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V"
            )
    )
    private void render(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
//        new RenderEvent.Overlay(guiGraphics, deltaTracker).post();
    }

    @Inject(
            method = "extractArmor",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void cancelArmorBar(GuiGraphicsExtractor context, Player player, int i, int j, int k, int x, CallbackInfo ci) {
        if (PlayerDisplay.shouldCancelHud(HudType.ARMOUR)) ci.cancel();
    }

    @Inject(
            method = "extractHearts",
            at = @At("HEAD"),
            cancellable = true
    )
    private void cancelHealthBar(GuiGraphicsExtractor context, Player player, int x, int y, int lines, int regeneratingHeartIndex, float maxHealth, int lastHealth, int health, int absorption, boolean blinking, CallbackInfo ci) {
        if (PlayerDisplay.shouldCancelHud(HudType.HEALTH)) ci.cancel();
    }

    @Inject(
            method = "extractFood",
            at = @At("HEAD"),
            cancellable = true
    )
    private void cancelFoodBar(GuiGraphicsExtractor context, Player player, int top, int right, CallbackInfo ci) {
        if (PlayerDisplay.shouldCancelHud(HudType.FOOD)) ci.cancel();
    }

    @Inject(
            method = "extractVehicleHealth",
            at = @At("HEAD"),
            cancellable = true
    )
    private void cancelMountHealth(GuiGraphicsExtractor guiGraphics, CallbackInfo ci) {
        if (PlayerDisplay.shouldCancelHud(HudType.MOUNT_HEALTH)) ci.cancel();
    }

    @ModifyArg(
            method = "extractPlayerHealth",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Gui;extractHearts(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/entity/player/Player;IIIIFIIIZ)V"
            ),
            index = 5
    )
    private int disableRegenBounce(int heartOffsetIndex) {
        return PlayerDisplay.shouldCancelHud(HudType.REGEN_BOUNCE) ? -1 : heartOffsetIndex;
    }

}
