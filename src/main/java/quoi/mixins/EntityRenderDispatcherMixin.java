package quoi.mixins;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import quoi.module.impl.misc.catmode.impl.CatModel;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {
    @Unique
    private CatModel.Renderer quoi$catRenderer;

    @ModifyArg(
            method = "onResourceManagerReload",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderers;createEntityRenderers(Lnet/minecraft/client/renderer/entity/EntityRendererProvider$Context;)Ljava/util/Map;"),
            index = 0
    )
    private EntityRendererProvider.Context quoi$createCatRenderer(EntityRendererProvider.Context context) {
        quoi$catRenderer = new CatModel.Renderer(context);
        return context;
    }

    @Inject(
            method = "getRenderer(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/client/renderer/entity/EntityRenderer;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void quoi$catEntityRenderer(Entity entity, CallbackInfoReturnable<EntityRenderer<?, ?>> cir) {
        if (entity instanceof AbstractClientPlayer player && CatModel.shouldRender(player)) {
            cir.setReturnValue(quoi$catRenderer);
        }
    }

    @Inject(
            method = "getRenderer(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;)Lnet/minecraft/client/renderer/entity/EntityRenderer;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void quoi$catStateRenderer(EntityRenderState entityRenderState, CallbackInfoReturnable<EntityRenderer<?, ?>> cir) {
        if (entityRenderState instanceof CatModel.RenderState) cir.setReturnValue(quoi$catRenderer);
    }
}
