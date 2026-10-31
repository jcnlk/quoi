package quoi.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
//? if >=26.3
//import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.world.item.ItemDisplayContext;
//? if <26.2
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import quoi.utils.render.EntityOverlayRenderer;

import java.util.List;

@Mixin(SubmitNodeCollection.class)
public class EntityOverlaySubmitMixin {
    @Inject(method = "submitItem", at = @At("HEAD"), cancellable = true)
    private void quoi$overlayItem(PoseStack poseStack, ItemDisplayContext context, int light, int overlay, int outline, int[] tints,
                                 //? if >=26.3 {
                                 //ItemQuads quads,
                                 //?} else
                                 List<BakedQuad> quads,
                                 ItemStackRenderState.FoilType foil, CallbackInfo ci) {
        //? if >=26.3 {
        //var geometry = quads.all();
        //?} else
        var geometry = quads;
        if (EntityOverlayRenderer.submitItem((SubmitNodeCollection) (Object) this, poseStack, light, tints, geometry)) ci.cancel();
    }

    @ModifyVariable(method = "submitModel", at = @At("HEAD"), argsOnly = true)
    private RenderType quoi$overlayModelType(RenderType original) {
        return EntityOverlayRenderer.renderType(original);
    }

    @ModifyVariable(method = "submitModel", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private int quoi$overlayModelColour(int original, Model<?> model, Object state, PoseStack poseStack, RenderType layer) {
        return EntityOverlayRenderer.overlayCoords(original, layer);
    }

    //? if <26.2 {
    @ModifyVariable(method = "submitModelPart", at = @At("HEAD"), argsOnly = true)
    private RenderType quoi$overlayPartType(RenderType original) {
        return EntityOverlayRenderer.renderType(original);
    }

    @ModifyVariable(method = "submitModelPart", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private int quoi$overlayPartColour(int original, ModelPart model, PoseStack poseStack, RenderType layer) {
        return EntityOverlayRenderer.overlayCoords(original, layer);
    }
    //?}
}
