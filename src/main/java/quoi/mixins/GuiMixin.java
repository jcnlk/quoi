package quoi.mixins;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import quoi.utils.skyblock.player.container.task.ContainerManager;

@Mixin(Gui.class)
public class GuiMixin {
    @Inject(
            method = "setScreen",
            at = @At("HEAD"),
            cancellable = true
    )
    private void quoi$onSetScreen(Screen screen, CallbackInfo ci) {
        if (ContainerManager.onSetScreen(screen)) ci.cancel();
    }
}
