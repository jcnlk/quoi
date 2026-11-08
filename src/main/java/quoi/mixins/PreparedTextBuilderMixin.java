package quoi.mixins;

import net.minecraft.client.gui.Font;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import quoi.module.impl.render.RenderOptimiser;

import static quoi.module.impl.render.RenderOptimiser.should;

@Mixin(Font.PreparedTextBuilder.class)
public class PreparedTextBuilderMixin {
    @Inject(
            method = "getShadowColor",
            at = @At("HEAD"),
            cancellable = true
    )
    private void devonian$disableTextShadow(Style style, int i, CallbackInfoReturnable<Integer> cir) {
        if (should(RenderOptimiser.getContainerTextShadow()) && Minecraft.getInstance().gui.screen() instanceof AbstractContainerScreen) return;
        if (should(RenderOptimiser.getDisableTextShadow())) cir.setReturnValue(0);
    }
}