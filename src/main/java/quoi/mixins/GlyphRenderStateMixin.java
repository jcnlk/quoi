package quoi.mixins;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GlyphRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import quoi.utils.render.CustomRenderPipelines;

@Mixin(GlyphRenderState.class)
public class GlyphRenderStateMixin {
    @Inject(method = "pipeline", at = @At("HEAD"), cancellable = true)
    private void useFoglessGuiTextPipeline(CallbackInfoReturnable<RenderPipeline> cir) {
        GlyphRenderState state = (GlyphRenderState) (Object) this;

        if (state.renderable().guiPipeline() == RenderPipelines.GUI_TEXT) {
            cir.setReturnValue(CustomRenderPipelines.INSTANCE.getGUI_TEXT_NO_FOG());
        }
    }
}
