package quoi.mixins;

import java.util.UUID;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.BossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import quoi.api.events.BossBarEvent;
import quoi.utils.StringUtils;

@Mixin(LerpingBossEvent.class)
public abstract class LerpingBossEventMixin extends BossEvent {
    protected LerpingBossEventMixin(UUID id, Component name, BossBarColor color, BossBarOverlay overlay) {
        super(id, name, color, overlay);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onCreate(CallbackInfo ci) {
        String message = StringUtils.INSTANCE.getFormattedString(name);
        new BossBarEvent.Update(message, StringUtils.INSTANCE.getNoControlCodes(message), name, progress).post();
    }

    @Inject(method = "setProgress", at = @At("TAIL"))
    private void onSetProgress(float progress, CallbackInfo ci) {
        String message = StringUtils.INSTANCE.getFormattedString(name);
        new BossBarEvent.Update(message, StringUtils.INSTANCE.getNoControlCodes(message), name, progress).post();
    }
}
