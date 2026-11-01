//~ gui_extraction
//~ screen_extraction

package quoi.mixins;

import quoi.api.events.GuiEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import quoi.module.impl.render.RenderOptimiser;

@Mixin(Screen.class)
public class ScreenMixin {

    @Inject(
            method = "init*",
            at = @At("HEAD"),
            cancellable = true
    )
    protected void quoi$onInitPre(CallbackInfo ci) {
        if (new GuiEvent.Open((Screen) (Object) this).post()) ci.cancel();
    }

    @Inject(
            method = "init*",
            at = @At("TAIL"),
            cancellable = true
    )
    protected void quoi$onInitPost(CallbackInfo ci) {
        if (new GuiEvent.Open.Post((Screen) (Object) this).post()) ci.cancel();
    }

    @Inject(
            method = "onClose",
            at = @At("HEAD"),
            cancellable = true
    )
    protected void quoi$onClose(CallbackInfo ci) {
        if (new GuiEvent.Close((Screen) (Object) this).post()) ci.cancel();
    }

    @Inject(
            method = "extractRenderStateWithTooltipAndSubtitles",
            at = @At("HEAD"),
            cancellable = true
    )
    protected void quoi$onRender(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        if (new GuiEvent.Draw((Screen) (Object) this, context, mouseX, mouseY).post()) ci.cancel();
    }

    @Inject(
            method = "extractRenderStateWithTooltipAndSubtitles",
            at = @At("TAIL")
    )
    protected void quoi$onRenderPost(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        new GuiEvent.DrawPost((Screen) (Object) this, context, mouseX, mouseY).post();
    }

    @Inject(
            method = "extractBackground",
            at = @At("HEAD"),
            cancellable = true
    )
    protected void quoi$onRenderBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        if (new GuiEvent.DrawBackground((Screen) (Object) this, context, mouseX, mouseY).post()) ci.cancel();
    }

    @Inject(
            method = "extractBackground",
            at = @At("TAIL")
    )
    protected void quoi$onRenderBackgroundPost(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        new GuiEvent.DrawBackground.Post((Screen) (Object) this, context).post();
    }

    @ModifyArgs(
            method = "extractTransparentBackground",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fillGradient(IIIIII)V"
            ),
            require = 1
    )
    private void quoi$containerBackgroundOpacity(Args args) {
        if (!((Object) this instanceof AbstractContainerScreen<?>) || !RenderOptimiser.INSTANCE.getEnabled()) return;
        float opacity = RenderOptimiser.getContainerBackgroundOpacity() / 100f;
        for (int i = 4; i <= 5; i++) {
            int colour = args.get(i);
            int alpha = Math.round((colour >>> 24) * opacity);
            args.set(i, (alpha << 24) | (colour & 0xFFFFFF));
        }
    }
}
