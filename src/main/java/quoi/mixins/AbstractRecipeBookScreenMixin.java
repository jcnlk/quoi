package quoi.mixins;

import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import quoi.mixins.accessors.RecipeBookComponentAccessor;
import quoi.module.impl.render.RenderOptimiser;

@Mixin(AbstractRecipeBookScreen.class)
public abstract class AbstractRecipeBookScreenMixin {
    @Shadow @Final private RecipeBookComponent<?> recipeBookComponent;

    @Inject(method = "init", at = @At("TAIL"))
    private void quoi$hideRecipeBookAfterInit(CallbackInfo ci) {
        if (RenderOptimiser.shouldHideRecipeBook()) {
            ((RecipeBookComponentAccessor) this.recipeBookComponent).quoi$setVisible(false);
        }
    }

    @Inject(method = "initButton", at = @At("HEAD"), cancellable = true)
    private void quoi$cancelRecipeBookButton(CallbackInfo ci) {
        if (RenderOptimiser.shouldHideRecipeBook()) ci.cancel();
    }
}
