package quoi.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import quoi.module.impl.misc.catmode.impl.CatModel;

@Mixin(LevelExtractor.class)
public class LevelExtractorMixin {
    @SuppressWarnings("unchecked")
    @WrapOperation(
            method = "extractPlayerState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/extract/LevelExtractor;extractEntity(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;"
            )
    )
    private EntityRenderState quoi$playerViewState(LevelExtractor extractor, Entity entity, float partialTicks, Operation<EntityRenderState> original) {
        if (!(entity instanceof AbstractClientPlayer player) || !CatModel.shouldRender(player)) {
            return original.call(extractor, entity, partialTicks);
        }

        AvatarRenderState state = new AvatarRenderState();
        state.skin = player.getSkin();
        AvatarRenderer<AbstractClientPlayer> renderer = (AvatarRenderer<AbstractClientPlayer>) Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(state);
        return renderer.createRenderState(player, partialTicks);
    }
}
