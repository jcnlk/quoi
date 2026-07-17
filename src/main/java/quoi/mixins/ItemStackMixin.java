package quoi.mixins;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import quoi.module.impl.render.RevertMasterStars;

@Mixin(ItemStack.class)
public class ItemStackMixin {

    @Inject(method = "getHoverName", at = @At("RETURN"), cancellable = true)
    private void quoi$onGetHoverName(CallbackInfoReturnable<Component> cir) {
        Component original = cir.getReturnValue();
        Component modified = RevertMasterStars.modifyHoverName(original);
        if (modified != original) cir.setReturnValue(modified);
    }
}
