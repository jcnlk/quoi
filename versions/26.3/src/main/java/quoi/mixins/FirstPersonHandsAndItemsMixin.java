package quoi.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import quoi.module.impl.render.ItemAnimations;

@Mixin(FirstPersonHandsAndItems.class)
public abstract class FirstPersonHandsAndItemsMixin {
    @Inject(
            method = "shouldInstantlyReplaceVisibleItem",
            at = @At("HEAD"),
            cancellable = true
    )
    private void quoi$itemAnimationsReequip(ItemStack current, ItemStack expected, LocalPlayer player, CallbackInfoReturnable<Boolean> cir) {
        if (ItemAnimations.disableReequip()) cir.setReturnValue(true);
    }

    @WrapOperation(
            method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getItemSwapScale(F)F")
    )
    private float quoi$itemAnimationsBob(LocalPlayer instance, float partialTick, Operation<Float> original) {
        if (ItemAnimations.disableReequip() || ItemAnimations.disableSwingBob()) return 1f;
        return original.call(instance, partialTick);
    }
}
