package quoi.mixins;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import quoi.utils.render.EntityOverlayState;

@Mixin(LivingEntityRenderState.class)
public class LivingEntityRenderStateMixin implements EntityOverlayState {
    @Unique private int quoi$overlayColour;
    @Unique private boolean quoi$overlayDepth;

    @Override
    public int quoi$getOverlayColour() {
        return quoi$overlayColour;
    }

    @Override
    public boolean quoi$getOverlayDepth() {
        return quoi$overlayDepth;
    }

    @Override
    public void quoi$setOverlay(int colour, boolean depth) {
        quoi$overlayColour = colour;
        quoi$overlayDepth = depth;
    }
}
