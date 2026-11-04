package quoi.mixins.accessors;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
//? if >=26.4
//import net.minecraft.client.gui.components.inventory.SlotWidget;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
    @Invoker("getHoveredSlot")
    //~ if >=26.4 'Slot quoi$getSlotAtPos(double mouseX, double mouseY)' -> 'SlotWidget quoi$getSlotAtPos()'
    Slot quoi$getSlotAtPos(double mouseX, double mouseY);
}
