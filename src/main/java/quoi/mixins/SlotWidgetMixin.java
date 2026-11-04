//? if >=26.4 {
/*package quoi.mixins;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.inventory.SlotWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import quoi.api.events.GuiEvent;

@Mixin(SlotWidget.class)
public class SlotWidgetMixin {
    @Inject(method = "extractWidgetRenderState", at = @At("HEAD"), cancellable = true)
    private void quoi$onDrawSlot(GuiGraphicsExtractor context, CallbackInfo ci) {
        Screen screen = Minecraft.getInstance().screen;
        if (!(screen instanceof AbstractContainerScreen<?>)) return;
        SlotWidget widget = (SlotWidget) (Object) this;
        Slot slot = widget.slot();

        // Slot draw listeners use container-relative coordinates.
        context.pose().pushMatrix();
        context.pose().translate(widget.getX() - slot.x, widget.getY() - slot.y);
        boolean cancelled = new GuiEvent.Slot.Draw(screen, context, slot).post();
        context.pose().popMatrix();
        if (cancelled) ci.cancel();
    }
}
*///?}
