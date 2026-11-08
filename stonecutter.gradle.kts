plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "26.2" /* [SC] DO NOT EDIT */

tasks.register<Delete>("clean") {
    group = "build"
    description = "Deletes collected artifacts and every version's build output."
    delete(layout.buildDirectory)
    dependsOn(stonecutter.tasks.named("clean"))
}

tasks.register("buildAll") {
    group = "build"
    description = "Builds every Minecraft version and collects its JAR in build/libs."
    dependsOn(stonecutter.tasks.named("build"))
    dependsOn(stonecutter.tasks.named("collectJar"))
}

tasks.register("buildActive") {
    group = "build"
    description = "Builds only the active Minecraft version."
    dependsOn(stonecutter.tasks.named("build") { metadata.isActive })
}

tasks.register("runActive") {
    group = "fabric"
    description = "Runs the client for the active Minecraft version."
    dependsOn(stonecutter.tasks.named("runClient") { metadata.isActive })
}

stonecutter parameters {
    val sdl = current.parsed >= "26.3"
    val v26_4 = current.parsed >= "26.4"
    val pipelineInBlaze3d = !sdl || v26_4

    // Minecraft 26.4 rendering, GUI, text, sound and fluid changes.
    replacements.string(v26_4, "texture_resources") {
        replace(
            "import net.minecraft.client.renderer.texture.DynamicTexture",
            "import net.minecraft.client.renderer.texture.TextureResources",
        )
        replace("DynamicTexture({", "TextureResources.from2dImage({")
        replace("DynamicTexture?", "TextureResources?")
        replace("DynamicTexture,", "TextureResources,")
        replace("it.textureView", "it.textureView()")
    }

    replacements.string(v26_4, "gui_pipeline") {
        replace("import net.minecraft.client.renderer.RenderPipelines", "import net.minecraft.client.gui.GuiPipeline")
        replace("RenderPipelines.GUI_TEXTURED", "GuiPipeline.TEXTURED")
        replace("RenderPipelines.GUI", "GuiPipeline.DEFAULT")
    }
    replacements.regex(v26_4, "gui_pipeline") {
        // A type colon must not match the Elvis expression ?: GuiPipeline.DEFAULT.
        replace("(?<!\\?): RenderPipeline\\b", ": GuiPipeline", "(?<!\\?): GuiPipeline\\b", ": RenderPipeline")
    }
    replacements.string(v26_4, "styled_text") {
        replace("}, Style.EMPTY)", "}, Unit, Style.EMPTY)")
    }

    replacements.string(v26_4) {
        replace("mc.mouseHandler.xpos()", "mc.mouseHandler.xpos")
        replace("mc.mouseHandler.ypos()", "mc.mouseHandler.ypos")
        replace("isHoveredOrFocused", "(isHovered || isFocused)")
        replace("SoundEvents.CAT_AMBIENT_BABY", "SoundEvents.BABY_CAT_SOUND_SET.ambientSound().get()")
        replace("import net.minecraft.tags.FluidTags", "import net.minecraft.world.level.material.Fluids")
        replace("FluidTags.WATER", "Fluids.WATER")
        replace("FluidTags.LAVA", "Fluids.LAVA")
        replace("net.minecraft.client.renderer.RenderPipelines", "net.minecraft.client.renderer.pipeline.RenderPipelines")
        replace("RenderSetup.builder(pipeline)", "RenderSetup.nonOitBuilder(pipeline)")
        replace("RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)", "RenderPipeline.builder(filledSnippet)")
    }

    replacements.string(v26_4, "gui_extraction") {
        listOf("extractRenderState", "extractBackground", "extractWidgetRenderState", "extractContents").forEach { method ->
            listOf("ctx", "guiGraphics").forEach { context ->
                replace(
                    "override fun $method($context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, deltaTicks: Float)",
                    "override fun $method($context: GuiGraphicsExtractor)",
                )
                replace("$method($context, mouseX, mouseY, deltaTicks)", "$method($context)")
            }
        }
        replace("this, context, mouseX, mouseY", "this, context, context.mouseX(), context.mouseY()")
        replace("{ ctx, a ->", "{ ctx ->")
        replace("RenderEvent.Overlay(ctx, a)", "RenderEvent.Overlay(ctx, ctx.deltaTracker())")
    }
    replacements.string(v26_4, "screen_extraction") {
        replace("GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks, CallbackInfo", "GuiGraphicsExtractor context, CallbackInfo")
    }
    replacements.string(v26_4, "container_extraction") {
        replace("GuiGraphicsExtractor context, int mouseX, int mouseY, CallbackInfo", "GuiGraphicsExtractor context, CallbackInfo")
        replace("GuiGraphicsExtractor context, Slot slot, int x, int y, CallbackInfo", "GuiGraphicsExtractor context, Slot slot, CallbackInfo")
    }

    // SDL input, swing and movement packet changes in Minecraft 26.3.
    replacements.string(sdl) {
        replace("RedStoneWireBlock", "RedstoneWireBlock")
        replace("InputConstants.Type.KEYSYM", "InputConstants.Type.KEYBOARD")
        replace("InputConstants.KEY_LSUPER", "InputConstants.KEY_LGUI")
        replace("InputConstants.KEY_RSUPER", "InputConstants.KEY_RGUI")
        replace("input.scancode()", "input.keycode()")
        replace("com.mojang.blaze3d.pipeline.BlendFunction", "com.mojang.renderpearl.api.pipeline.BlendFunction")
        replace("com.mojang.blaze3d.pipeline.ColorTargetState", "com.mojang.renderpearl.api.pipeline.ColorTargetState")
        replace("com.mojang.blaze3d.pipeline.DepthStencilState", "com.mojang.renderpearl.api.pipeline.DepthStencilState")
        replace("com.mojang.blaze3d.platform.CompareOp", "com.mojang.renderpearl.api.pipeline.CompareOp")
        replace("com.mojang.blaze3d.PrimitiveTopology", "com.mojang.renderpearl.api.pipeline.PrimitiveTopology")
        replace("com.mojang.blaze3d.textures.FilterMode", "com.mojang.renderpearl.api.textures.FilterMode")
        replace("player.swing(InteractionHand.MAIN_HAND)", "player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false)")
        replace("player.swing(hand)", "player.swing(hand, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false)")
        replace("level.addBreakingBlockEffect(pos, direction)", "level.addBreakingBlockEffects(pos, direction, false)")
        replace("packet.values.position", "packet.position.endPosition()")
        replace("player.drop(false)", "gameMode.dropItem(player, false)")
        replace("click.button()", "quoi.api.input.Mouse.normalizeButton(click.button())")
        replace("mouseButtonEvent.button()", "quoi.api.input.Mouse.normalizeButton(mouseButtonEvent.button())")
        replace("input.button()", "quoi.api.input.Mouse.normalizeButton(input.button())")
    }
    replacements.string(sdl, "desktop_platform") {
        replace("import net.minecraft.util.Util", "import com.mojang.blaze3d.Blaze3D")
        replace("Util.getPlatform().openUri(", "Blaze3D.openUri(")
        replace("Util.getPlatform().openPath(", "Blaze3D.openPath(")
    }
    replacements.string(sdl, "system_cursor") {
        replace("import org.lwjgl.glfw.GLFW", "import org.lwjgl.sdl.SDLMouse")
    }
    replacements.string(sdl, "name_tag_rendering") {
        replace("import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;", "import net.minecraft.client.renderer.SubmitNodeCollection;")
        replace("@Mixin(NameTagFeatureRenderer.class)", "@Mixin(SubmitNodeCollection.class)")
        replace("method = \"prepareText\"", "method = \"nameTag\"")
        replace(
            "Lnet/minecraft/client/gui/Font;prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font\$PreparedText;",
            "Lnet/minecraft/client/renderer/feature/TextFeatureRenderer\$Content\$Text;<init>(FFLnet/minecraft/util/FormattedCharSequence;ZIII)V",
        )
    }
    replacements.string(sdl, "entity_overlay_shader") {
        replace("#moj_import", "#include")
    }
    replacements.regex(sdl, "enderman_type") {
        replace("\\bEnderMan\\b", "Enderman", "\\bEnderman\\b", "EnderMan")
    }
    replacements.regex(sdl, "swing_packet") {
        replace("\\bServerboundSwingPacket\\b", "ServerboundPunchPacket", "\\bServerboundPunchPacket\\b", "ServerboundSwingPacket")
    }
    replacements.string(!pipelineInBlaze3d) {
        replace("com.mojang.blaze3d.pipeline.RenderPipeline", "com.mojang.renderpearl.api.pipeline.RenderPipeline")
    }

    swaps["system_cursor"] = when {
        !sdl -> "val \$1 by lazy { GLFW.glfwCreateStandardCursor(GLFW.GLFW_\$2_CURSOR) }"
        else -> "val \$1 by lazy { SDLMouse.SDL_CreateSystemCursor(SDLMouse.SDL_SYSTEM_CURSOR_\$3) }"
    }
    swaps["input_key"] = when {
        !sdl -> "val \$1: Int = InputConstants.getKey(\"\$2\").value"
        else -> "const val \$1: Int = SDLScancode.SDL_SCANCODE_\$3"
    }
    swaps["pose_rotation"] = when {
        !sdl -> "pose.mulPose(Axis.\$1.rotationDegrees(\$2))"
        else -> "pose.rotateDegrees(Axis.\$1, \$2)"
    }
    swaps["primitive_type_import"] = when {
        !sdl -> "import com.mojang.blaze3d.PrimitiveTopology"
        else -> "import com.mojang.renderpearl.api.pipeline.PrimitiveTopology"
    }
    swaps["chat_render_method"] = when {
        !v26_4 -> "method = \"extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent\$DisplayMode;Z)V\","
        else -> "method = \"extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;ILnet/minecraft/client/gui/components/ChatComponent\$DisplayMode;Z)V\","
    }
    swaps["pipeline_start"] = when {
        !v26_4 -> "val \$1: RenderPipeline = RenderPipelines.register("
        else -> "val \$1: RenderPipeline ="
    }
    swaps["pipeline_end"] = when {
        !v26_4 -> ")"
        else -> "// Empty."
    }
}
