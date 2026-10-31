//~ gui_extraction
//~ container_extraction

package quoi.mixins;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import quoi.api.events.GuiEvent;

@Mixin(AbstractContainerScreen.class)
public class AbstractContainerScreenMixin {

    @Inject(
            method = "extractSlot",
            at = @At("HEAD"),
            //~ if >=26.2 'cancellable = true' -> 'cancellable = true, require = 1'
            cancellable = true
    )
    private void quoi$onDrawSlot(GuiGraphicsExtractor context, Slot slot, int x, int y, CallbackInfo ci) {
        if (new GuiEvent.Slot.Draw((Screen) (Object) this, context, slot).post()) ci.cancel();
    }

    @Inject(
            //~ if >=26.3 '"slotClicked"' -> '"slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ContainerInput;)V"'
            method = "slotClicked",
            at = @At("HEAD"),
            cancellable = true
    )
    public void quoi$onMouseClickedSlot(Slot slot, int slotId, int button, ContainerInput actionType, CallbackInfo ci) {
        if (slot == null) return;
        if (new GuiEvent.Slot.Click((Screen) (Object) this, slot, slotId, button, actionType).post()) ci.cancel();
    }

    @Inject(
            method = "extractTooltip",
            at = @At("HEAD"),
            cancellable = true
    )
    public void quoi$onDrawMouseoverTooltip(GuiGraphicsExtractor context, int mouseX, int mouseY, CallbackInfo ci) {
        if (new GuiEvent.DrawTooltip((Screen) (Object) this, context, mouseX, mouseY).post()) {
            ci.cancel();
        }
    }
}
