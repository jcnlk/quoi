package quoi.module.impl.misc.catmode.impl

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.model.animal.feline.AbstractFelineModel
import net.minecraft.client.model.animal.feline.AdultCatModel
import net.minecraft.client.model.animal.feline.BabyCatModel
import net.minecraft.client.model.geom.ModelLayers
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.LivingEntityRenderer
import net.minecraft.client.renderer.entity.state.CatRenderState
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.resources.Identifier
import quoi.module.impl.misc.catmode.CatMode
import quoi.module.settings.group.ToggleableGroup

object CatModel : ToggleableGroup(
    CatMode,
    "Kitty kitty"
) {
    private val catModel by selector("Model", CatType.Tabby)
    private val self by switch("I'm a cat")
    private val others by switch("He's a cat")
    private val baby by switch("Baby cat")

    @JvmStatic
    fun shouldRender(player: AbstractClientPlayer): Boolean =
        running && if (player == mc.player) self else others

    class RenderState : CatRenderState()

    class Renderer(context: EntityRendererProvider.Context) :
        LivingEntityRenderer<AbstractClientPlayer, RenderState, AbstractFelineModel<CatRenderState>>(
            context, AdultCatModel(context.bakeLayer(ModelLayers.CAT)), 0.4f
        ) {
        private val adultModel = model
        private val babyModel = BabyCatModel(context.bakeLayer(ModelLayers.CAT_BABY))

        override fun createRenderState() = RenderState()

        override fun getTextureLocation(state: RenderState): Identifier = state.texture

        override fun extractRenderState(player: AbstractClientPlayer, state: RenderState, partialTicks: Float) {
            super.extractRenderState(player, state, partialTicks)
            state.isBaby = baby
            state.ageScale = if (baby) 0.5f else 1f
            state.texture = if (baby) catModel.selected.babyPath else catModel.selected.path
            state.isCrouching = player.isCrouching
            state.isSprinting = player.isSprinting
            state.isSitting = player.isPassenger
        }

        override fun getShadowRadius(state: RenderState): Float = super.getShadowRadius(state) * state.ageScale

        override fun submit(
            state: RenderState,
            pose: PoseStack,
            collector: SubmitNodeCollector,
            camera: CameraRenderState,
        ) {
            model = if (state.isBaby) babyModel else adultModel
            super.submit(state, pose, collector, camera)
        }
    }

    @Suppress("unused")
    private enum class CatType(p: String) {
        AllBlack("all_black"),
        Black("black"),
        BritishShorthair("british_shorthair"),
        Calico("calico"),
        Jellie("jellie"),
        Persian("persian"),
        Ragdoll("ragdoll"),
        Red("red"),
        Siamese("siamese"),
        Tabby("tabby"),
        White("white");

        val path = Identifier.withDefaultNamespace("textures/entity/cat/cat_$p.png")
        val babyPath = Identifier.withDefaultNamespace("textures/entity/cat/cat_${p}_baby.png")
    }
}