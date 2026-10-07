plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "26.1.x"

stonecutter parameters {
    val gui = current.parsed >= "26.2"
    val sdl = current.parsed >= "26.3"
    val pipelineInBlaze3d = current.parsed < "26.3" || current.parsed >= "26.4-snapshot-1"

    // Minecraft 26.2 moves screens and HUD state into Gui and Hud.
    replacements.string(gui) {
        replace("NVGRenderer", "UIRenderer")
        replace("mc.screen", "mc.gui.screen()")
        replace("minecraft.screen", "minecraft.gui.screen()")
        replace("Minecraft.getInstance().screen", "Minecraft.getInstance().gui.screen()")
        replace("mc.setScreen(", "mc.gui.setScreen(")
        replace("minecraft.setScreen(", "minecraft.gui.setScreen(")
        replace("mc.gui.chat", "mc.gui.hud.chat")
        replace("mc.gui.getChat()", "mc.gui.hud.getChat()")
        replace("mc.gameRenderer.mainCamera", "mc.gameRenderer.mainCamera()")
        replace("mc.isSingleplayer", "mc.hasSingleplayerServer()")
        replace("mc.options.hideGui", "mc.gui.hud.isHidden")
        replace("LevelRenderEvents.END_MAIN", "LevelRenderEvents.COLLECT_SUBMITS")
        replace("GameRenderer;getNightVisionScale", "GameRenderer;nightVisionScale")
        replace("mc.gui.setTimes(", "mc.gui.hud.setTimes(")
        replace("mc.gui.setTitle(", "mc.gui.hud.setTitle(")
        replace("mc.gui.setSubtitle(", "mc.gui.hud.setSubtitle(")
    }
    replacements.string(gui, "ui_frame") {
        replace("import quoi.utils.ui.rendering.NVGSpecialRenderer", "import quoi.utils.ui.rendering.UIRenderer")
        replace("NVGSpecialRenderer.draw(ctx, 0, 0, ctx.guiWidth(), ctx.guiHeight())", "UIRenderer.frame(ctx)")
    }

    replacements.string(current.parsed >= "26.4-snapshot-3") {
        replace(
            "import net.minecraft.client.renderer.texture.DynamicTexture",
            "import net.minecraft.client.renderer.texture.TextureResources",
        )
        replace("DynamicTexture({", "TextureResources.from2dImage({")
        replace("DynamicTexture?", "TextureResources?")
        replace("DynamicTexture,", "TextureResources,")
        replace("it.textureView", "it.textureView()")
    }

    // Registry families replace the individual colored and weathered constants.
    replacements.string(gui) {
        val colors = mapOf(
            "WHITE" to "white", "ORANGE" to "orange", "MAGENTA" to "magenta", "LIGHT_BLUE" to "lightBlue",
            "YELLOW" to "yellow", "LIME" to "lime", "PINK" to "pink", "GRAY" to "gray",
            "LIGHT_GRAY" to "lightGray", "CYAN" to "cyan", "PURPLE" to "purple", "BLUE" to "blue",
            "BROWN" to "brown", "GREEN" to "green", "RED" to "red", "BLACK" to "black",
        )
        mapOf(
            "WOOL" to "WOOL", "CARPET" to "CARPET", "TERRACOTTA" to "DYED_TERRACOTTA",
            "SHULKER_BOX" to "DYED_SHULKER_BOX", "CANDLE" to "DYED_CANDLE",
            "STAINED_GLASS" to "STAINED_GLASS", "STAINED_GLASS_PANE" to "STAINED_GLASS_PANE",
        ).forEach { (old, family) ->
            colors.forEach { (color, method) -> replace("Blocks.${color}_$old", "Blocks.$family.$method()") }
        }
        listOf("DOOR", "TRAPDOOR", "BULB").forEach { family ->
            listOf("EXPOSED", "WEATHERED", "OXIDIZED").forEach { state ->
                replace("Blocks.${state}_COPPER_$family", "Blocks.COPPER_$family.weathering().${state.lowercase()}()")
                replace("Blocks.WAXED_${state}_COPPER_$family", "Blocks.COPPER_$family.waxed().${state.lowercase()}()")
            }
            replace("Blocks.WAXED_COPPER_$family", "Blocks.COPPER_$family.waxed().unaffected()")
        }
    }

    // SDL input, swing and movement packet changes in Minecraft 26.3.
    replacements.string(sdl) {
        replace("RedStoneWireBlock", "RedstoneWireBlock")
        replace("InputConstants.KEY_LSUPER", "InputConstants.KEY_LGUI")
        replace("InputConstants.KEY_RSUPER", "InputConstants.KEY_RGUI")
        replace("input.scancode()", "input.keycode()")
        replace("Util.getPlatform().openUri(", "Blaze3D.openUri(")
        replace("Util.getPlatform().openPath(", "Blaze3D.openPath(")
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
        replace("click.button()", "Mouse.normalizeButton(click.button())")
        replace("mouseButtonEvent.button()", "Mouse.normalizeButton(mouseButtonEvent.button())")
        replace("input.button()", "Mouse.normalizeButton(input.button())")
    }
    replacements.regex(sdl, "enderman_type") {
        replace("\\bEnderMan\\b", "Enderman", "\\bEnderman\\b", "EnderMan")
    }
    replacements.string(!pipelineInBlaze3d) {
        replace("com.mojang.blaze3d.pipeline.RenderPipeline", "com.mojang.renderpearl.api.pipeline.RenderPipeline")
    }

    swaps["system_cursor"] = when {
        !sdl -> "val \$1 by lazy { GLFW.glfwCreateStandardCursor(GLFW.GLFW_\$2_CURSOR) }"
        else -> "val \$1 by lazy { SDLMouse.SDL_CreateSystemCursor(SDLMouse.SDL_SYSTEM_CURSOR_\$3) }"
    }
    swaps["atlas_upload"] = when {
        current.parsed >= "26.4-snapshot-3" -> """
            val encoder = RenderSystem.getDevice().createCommandEncoder()
            page.image.writeToGpuTexture(encoder, page.texture.texture)
            encoder.submit()
        """.trimIndent()
        else -> "page.texture.upload()"
    }
    swaps["pipeline_start"] = when {
        current.parsed < "26.4-snapshot-1" -> "val \$1: RenderPipeline = RenderPipelines.register("
        else -> "val \$1: RenderPipeline ="
    }
    swaps["pipeline_end"] = when {
        current.parsed < "26.4-snapshot-1" -> ")"
        else -> "// Empty."
    }
    swaps["pipeline_vertices"] = when {
        !gui -> ".withVertexFormat(DefaultVertexFormat.\$1, VertexFormat.Mode.\$2)"
        else -> """
            .withVertexBinding(0, DefaultVertexFormat.${'$'}1)
            .withPrimitiveTopology(PrimitiveTopology.${'$'}2)
        """.trimIndent()
    }
    swaps["slot_click_target"] = when {
        !sdl -> "method = \"slotClicked\","
        else -> "method = \"slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ContainerInput;)V\","
    }
    swaps["slot_draw_options"] = when {
        !gui -> "cancellable = true"
        else -> "cancellable = true,\nrequire = 1"
    }
    swaps["fog_target_end"] = when {
        !gui -> ")"
        else -> "),\nrequire = 1"
    }
    swaps["avatar_swing_state"] = when {
        !sdl -> "avatarRenderState.attackTime = ItemAnimations.getThirdPersonSwingAnimation(avatarRenderState.attackTime, avatarRenderState.getMainHandItemStack(), avatarRenderState.id);"
        else -> """
            avatarRenderState.swingAnimation = ItemAnimations.getThirdPersonSwingAnimation(avatarRenderState.swingAnimation, avatarRenderState.getMainHandItemStack(), avatarRenderState.id, avatarRenderState.currentSwing);
            avatarRenderState.currentSwing = ItemAnimations.getThirdPersonSwingDescription(avatarRenderState.currentSwing, avatarRenderState.getMainHandItemStack(), avatarRenderState.id);
        """.trimIndent()
    }
    swaps["chat_extract_target"] = when {
        !gui -> "method = \"extractRenderState*\","
        else -> "method = \"extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent\$DisplayMode;Z)V\","
    }
    swaps["chat_focus_args"] = when {
        !gui -> "argsOnly = true"
        else -> """
            argsOnly = true,
            ordinal = 0
        """.trimIndent()
    }
    swaps["chat_focus_handler"] = when {
        !gui -> """
            private ChatComponent.DisplayMode renderFocused(ChatComponent.DisplayMode mode) {
                return ChatPeek.displayMode(mode);
        """.trimIndent()
        else -> """
            private boolean renderFocused(boolean focused) {
                return focused || ChatPeek.isDown();
        """.trimIndent()
    }
    swaps["chat_search_header"] = when {
        !gui -> "messages.addFirst(new GuiMessage(mc.gui.getGuiTicks(), Component.literal(\"§e§lSEARCH ON\"), null, GuiMessageSource.SYSTEM_CLIENT, null));"
        else -> "messages.addFirst(new GuiMessage(0, Component.literal(\"§e§lSEARCH ON\"), null, GuiMessageSource.SYSTEM_CLIENT, null));"
    }
    swaps["entity_visibility_handler"] = when {
        !sdl -> "private void onRender(T entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {"
        else -> "private void onRender(T entity, Frustum frustum, double x, double y, double z, float partialTick, CallbackInfoReturnable<Boolean> cir) {"
    }
    swaps["living_swing_hook"] = when {
        !sdl -> "// Empty."
        else -> """
            @Inject(
                    method = "swing(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/component/SwingAnimation;Z)Z",
                    at = @At("HEAD"),
                    require = 1
            )
            private void quoi${'$'}onSwing(InteractionHand hand, SwingAnimation animation, boolean sendToSwingingEntity, CallbackInfoReturnable<Boolean> cir) {
                if ((Object) this == Minecraft.getInstance().player) ItemAnimations.onSwing(hand, animation);
            }
        """.trimIndent()
    }
    swaps["local_swing_hook"] = when {
        !sdl -> """
            @Inject(
                    method = "swing",
                    at = @At("HEAD")
            )
            private void quoi${'$'}onSwing(InteractionHand hand, CallbackInfo ci) {
                ItemAnimations.onSwing();
            }
        """.trimIndent()
        else -> "// Empty."
    }
    swaps["attack_swing_target"] = when {
        !sdl -> "target = \"Lnet/minecraft/client/player/LocalPlayer;swing(Lnet/minecraft/world/InteractionHand;)V\""
        else -> "target = \"Lnet/minecraft/client/player/LocalPlayer;swing(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/component/SwingAnimation;Z)Z\""
    }
    swaps["attack_swing_handler"] = when {
        !sdl -> "private void redirectSwing(LocalPlayer instance, InteractionHand hand) {"
        else -> "private boolean redirectSwing(LocalPlayer instance, InteractionHand hand, SwingAnimation animation, boolean sendToSwingingEntity) {"
    }
    swaps["cancel_attack_swing"] = when {
        !sdl -> "// Empty."
        else -> "return false;"
    }
    swaps["send_attack_swing"] = when {
        !sdl -> "instance.swing(hand);"
        else -> "return instance.swing(hand, animation, sendToSwingingEntity);"
    }
    swaps["minecraft_screen_hook"] = when {
        !gui -> """
            @Inject(
                    method = "setScreen",
                    at = @At("HEAD"),
                    cancellable = true
            )
            private void quoi${'$'}onSetScreen(Screen screen, CallbackInfo ci) {
                if (ContainerManager.onSetScreen(screen)) ci.cancel();

            }
        """.trimIndent()
        else -> "// Empty."
    }
    swaps["mouse_move_handler"] = when {
        !sdl -> "private void onMouseMove(long window, double mx, double my, CallbackInfo ci) {"
        else -> "private void onMouseMove(long window, double mx, double my, double dx, double dy, CallbackInfo ci) {"
    }
    swaps["release_mouse"] = when {
        !sdl -> "InputConstants.grabOrReleaseMouse(Minecraft.getInstance().getWindow(), InputConstants.CURSOR_NORMAL, this.beforeX, this.beforeY);"
        else -> "InputConstants.releaseMouse(Minecraft.getInstance().getWindow(), this.beforeX, this.beforeY);"
    }
    swaps["nametag_import"] = when {
        !sdl -> "import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;"
        else -> "// Empty."
    }
    swaps["nametag_mixin_target"] = when {
        !sdl -> "@Mixin(NameTagFeatureRenderer.class)"
        else -> "@Mixin(SubmitNodeCollection.class)"
    }
    swaps["fire_render_import"] = when {
        !gui -> "import net.minecraft.client.renderer.MultiBufferSource;"
        else -> "// Empty."
    }
    swaps["fire_render_target"] = when {
        !gui -> "method = \"renderFire\","
        else -> "method = \"submitFire\","
    }
    swaps["fire_render_handler"] = when {
        !gui -> "private static void onRenderFire(PoseStack poseStack, MultiBufferSource multiBufferSource, TextureAtlasSprite textureAtlasSprite, CallbackInfo ci) {"
        else -> "private static void onRenderFire(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, TextureAtlasSprite textureAtlasSprite, CallbackInfo ci) {"
    }
    swaps["register_nanovg_renderer"] = when {
        !gui -> """
            PictureInPictureRendererRegistry.register { context ->
                NVGSpecialRenderer(context.bufferSource())
            }
        """.trimIndent()
        else -> "// Empty."
    }
    swaps["nanovg_renderer_import"] = when {
        !gui -> "import quoi.utils.ui.rendering.NVGSpecialRenderer"
        else -> "// Empty."
    }
    swaps["keyboard_backend_import"] = when {
        !sdl -> "import org.lwjgl.glfw.GLFW"
        else -> "// Empty."
    }
    swaps["key_name"] = when {
        !sdl -> """
            val scancode = GLFW.glfwGetKeyScancode(key)
            if (scancode == -1) return null

            return GLFW.glfwGetKeyName(key, scancode) ?: when (key) {
        """.trimIndent()
        else -> "return InputConstants.Type.KEYBOARD.getOrCreate(key).displayName.string.ifBlank { null } ?: when (key) {"
    }
    swaps["function_key_names"] = when {
        !sdl -> "in Keybinds.KEY_F1..GLFW.GLFW_KEY_F25 -> \"F\${key - Keybinds.KEY_F1 + 1}\""
        else -> """
            in Keybinds.KEY_F1..Keybinds.KEY_F12 ->
                "F${'$'}{key - Keybinds.KEY_F1 + 1}"
            in InputConstants.KEY_F13..InputConstants.KEY_F24 ->
                "F${'$'}{key - InputConstants.KEY_F13 + 13}"
        """.trimIndent()
    }
    swaps["key_state"] = when {
        !sdl -> """
            val state = GLFW.glfwGetKey(mc.window.handle(), key)
            return state == GLFW.GLFW_PRESS || state == GLFW.GLFW_REPEAT
        """.trimIndent()
        else -> "return InputConstants.isKeyDown(key)"
    }
    swaps["mouse_backend_import"] = when {
        !sdl -> "import org.lwjgl.glfw.GLFW"
        else -> "import org.lwjgl.sdl.SDLMouse"
    }
    swaps["sdl_mouse_buttons"] = when {
        !sdl -> "// Empty."
        else -> """
            /**
             * Converts SDL's 1-based button order (left, middle, right) to the
             * legacy order used throughout quoi (left, right, middle, ...).
             */
            @JvmStatic
            fun normalizeButton(code: Int): Int = when (code) {
                SDLMouse.SDL_BUTTON_LEFT -> 0
                SDLMouse.SDL_BUTTON_RIGHT -> 1
                SDLMouse.SDL_BUTTON_MIDDLE -> 2
                else -> code - 1
            }

            fun toSDLButton(code: Int): Int = when (code) {
                0 -> SDLMouse.SDL_BUTTON_LEFT
                1 -> SDLMouse.SDL_BUTTON_RIGHT
                2 -> SDLMouse.SDL_BUTTON_MIDDLE
                else -> code + 1
            }
        """.trimIndent()
    }
    swaps["mouse_button_state"] = when {
        !sdl -> """
            fun isButtonDown(code: Int): Boolean {
                val state = GLFW.glfwGetMouseButton(mc.window.handle(), code)
                return state == GLFW.GLFW_PRESS || state == GLFW.GLFW_REPEAT
            }
        """.trimIndent()
        else -> """
            fun isButtonDown(code: Int): Boolean {
                if (code !in 0..7) return false
                return isSDLButtonDown(toSDLButton(code))
            }

            fun isSDLButtonDown(code: Int): Boolean {
                if (code !in 1..Int.SIZE_BITS) return false
                val state = SDLMouse.SDL_GetMouseState(null, null)
                return state and (1 shl (code - 1)) != 0
            }
        """.trimIndent()
    }
    swaps["set_cursor"] = when {
        !sdl -> "GLFW.glfwSetCursor(mc.window.handle(), cursor)"
        else -> "SDLMouse.SDL_SetCursor(if (cursor == 0L) SDLMouse.SDL_GetDefaultCursor() else cursor)"
    }
    swaps["scancode_import"] = when {
        !sdl -> "// Empty."
        else -> "import org.lwjgl.sdl.SDLScancode"
    }
    swaps["keypad_decimal_divide"] = when {
        !sdl -> """
            const val KEY_NUMPAD_DECIMAL: Int = InputConstants.KEY_NUMPADCOMMA
            val KEY_NUMPAD_DIVIDE: Int = InputConstants.getKey("key.keyboard.keypad.divide").value
        """.trimIndent()
        else -> """
            const val KEY_NUMPAD_DECIMAL: Int = SDLScancode.SDL_SCANCODE_KP_PERIOD
            const val KEY_NUMPAD_DIVIDE: Int = SDLScancode.SDL_SCANCODE_KP_DIVIDE
        """.trimIndent()
    }
    swaps["keypad_subtract"] = when {
        !sdl -> "val KEY_NUMPAD_SUBTRACT: Int = InputConstants.getKey(\"key.keyboard.keypad.subtract\").value"
        else -> "const val KEY_NUMPAD_SUBTRACT: Int = SDLScancode.SDL_SCANCODE_KP_MINUS"
    }
    swaps["menu_key"] = when {
        !sdl -> "val KEY_MENU: Int = InputConstants.getKey(\"key.keyboard.menu\").value"
        else -> "const val KEY_MENU: Int = SDLScancode.SDL_SCANCODE_APPLICATION"
    }
    swaps["map_player_face"] = when {
        !gui -> "PlayerFaceExtractor.extractRenderState(ctx, it, w, h, w)"
        else -> "ctx.drawImage(WHITE_MARKER, 0, 0, w, h)"
    }
    swaps["movement_zero_delta"] = when {
        !sdl -> "if (packet.xa.toInt() == 0 && packet.ya.toInt() == 0 && packet.za.toInt() == 0) return@on"
        else -> "// Empty."
    }
    swaps["decode_movement_delta"] = when {
        !sdl -> "// Empty."
        else -> """
            if (!packet.hasPosition()) return@on
            val positionCodec = entity.positionCodec
            val nextPosition = packet.positionDelta.decode(positionCodec).endPosition()
            val delta = nextPosition.subtract(positionCodec.base)
            if (delta == Vec3.ZERO) return@on
        """.trimIndent()
    }
    swaps["legacy_movement_delta"] = when {
        !sdl -> """
            val dx = packet.xa / 4096.0
            val dy = packet.ya / 4096.0
            val dz = packet.za / 4096.0
        """.trimIndent()
        else -> "// Empty."
    }
    swaps["store_movement_delta"] = when {
        !sdl -> "bloodEntity.vecs.add(Vec3(dx, dy, dz))"
        else -> "bloodEntity.vecs.add(delta)"
    }
    swaps["chat_display_mode"] = when {
        !gui -> """
            fun displayMode(mode: ChatComponent.DisplayMode): ChatComponent.DisplayMode {
                return if (isDown()) ChatComponent.DisplayMode.FOREGROUND else mode
            }

            @JvmStatic
        """.trimIndent()
        else -> "// Empty."
    }
    swaps["wolf_entity_type"] = when {
        !gui -> "type == EntityType.WOLF -> MineshaftMob.GLACITE_MUTT"
        else -> "EntityType.getKey(type).path == \"wolf\" -> MineshaftMob.GLACITE_MUTT"
    }
    swaps["slime_import"] = when {
        !gui -> "import net.minecraft.world.entity.monster.Slime"
        else -> "// Empty."
    }
    swaps["craft_room_entity_colors"] = when {
        !gui -> """
            val colour = when (entity) {
                is Zombie -> Colour.BROWN
                is Slime -> Colour.GREEN
                is CaveSpider -> Colour.WHITE
        """.trimIndent()
        else -> """
            val colour = when {
                entity is Zombie -> Colour.BROWN
                net.minecraft.world.entity.EntityType.getKey(entity.type).path == "slime" -> Colour.GREEN
                entity is CaveSpider -> Colour.WHITE
        """.trimIndent()
    }
    swaps["punch_packet_import"] = when {
        !sdl -> "import net.minecraft.network.protocol.game.ServerboundSwingPacket"
        else -> "import net.minecraft.network.protocol.game.ServerboundPunchPacket"
    }
    swaps["punch_packet_event"] = when {
        !sdl -> """
            on<PacketEvent.Sent, ServerboundSwingPacket> {
                if (packet.hand != InteractionHand.MAIN_HAND) return@on
        """.trimIndent()
        else -> "on<PacketEvent.Sent, ServerboundPunchPacket> {"
    }
    swaps["swing_description_field"] = when {
        !sdl -> "// Empty."
        else -> "private var swingDescription: LivingEntity.SwingDescription? = null"
    }
    swaps["third_person_swing_data"] = when {
        !sdl -> "private data class ThirdPersonSwing(val startTime: Double, var previousVanilla: Float)"
        else -> """
            private data class ThirdPersonSwing(
                val startTime: Double,
                var previousVanilla: Float,
                val description: LivingEntity.SwingDescription?
            )
        """.trimIndent()
    }
    swaps["clear_swing_description"] = when {
        !sdl -> "// Empty."
        else -> "swingDescription = null"
    }
    swaps["item_rotation"] = when {
        !sdl -> """
            pose.mulPose(Axis.XP.rotationDegrees(pitch))
            pose.mulPose(Axis.YP.rotationDegrees(yaw))
            pose.mulPose(Axis.ZP.rotationDegrees(roll))
        """.trimIndent()
        else -> """
            pose.rotateDegrees(Axis.XP, pitch)
            pose.rotateDegrees(Axis.YP, yaw)
            pose.rotateDegrees(Axis.ZP, roll)
        """.trimIndent()
    }
    swaps["third_person_swing_signature"] = when {
        !sdl -> "fun getThirdPersonSwingAnimation(current: Float, stack: ItemStack, playerId: Int): Float {"
        else -> "fun getThirdPersonSwingAnimation(current: Float, stack: ItemStack, playerId: Int, description: LivingEntity.SwingDescription?): Float {"
    }
    swaps["third_person_swing_start"] = when {
        !sdl -> "ThirdPersonSwing(currentTime, current).also { thirdPersonSwings[playerId] = it }"
        else -> "ThirdPersonSwing(currentTime, current, description).also { thirdPersonSwings[playerId] = it }"
    }
    swaps["swing_descriptions"] = when {
        !sdl -> "// Empty."
        else -> """
            @JvmStatic
            fun getThirdPersonSwingDescription(current: LivingEntity.SwingDescription?, stack: ItemStack, playerId: Int): LivingEntity.SwingDescription? {
                if (!shouldApplyThirdPerson(stack, playerId)) return current
                return if (playerId == player.id) getSwingDescription(current, stack)
                else thirdPersonSwings[playerId]?.description ?: current
            }

            @JvmStatic
            fun getSwingDescription(current: LivingEntity.SwingDescription?, stack: ItemStack): LivingEntity.SwingDescription? {
                if (!enabled || (stack.isEmpty && !affectHand()) || (stack.has(DataComponents.MAP_ID) && !affectMap())) return current
                return if (swinging || prevAttackAnim > 0f) swingDescription ?: current else current
            }
        """.trimIndent()
    }
    swaps["on_swing_signature"] = when {
        !sdl -> "fun onSwing() {"
        else -> "fun onSwing(hand: InteractionHand, animation: SwingAnimation) {"
    }
    swaps["on_swing_description"] = when {
        !sdl -> "// Empty."
        else -> "swingDescription = LivingEntity.SwingDescription(hand, animation, getCurrentSwingDuration())"
    }
    swaps["menu_mouse_import"] = when {
        !sdl -> "// Empty."
        else -> "import org.lwjgl.sdl.SDLMouse"
    }
    swaps["options_screen"] = when {
        !gui -> "addButton(\"Options\") { minecraft.setScreen(OptionsScreen(this, minecraft.options, false)) }"
        gui && !sdl -> "addButton(\"Options\") { minecraft.gui.setScreen(OptionsScreen(this, minecraft.options, false)) }"
        else -> "addButton(\"Options\") { minecraft.gui.setScreen(OptionsScreen(this, minecraft.options)) }"
    }
    swaps["menu_mouse_button"] = when {
        !sdl -> "MouseButtonInfo(0, 0),"
        else -> "MouseButtonInfo(SDLMouse.SDL_BUTTON_LEFT, 0),"
    }
    swaps["menu_panorama"] = when {
        !gui -> "else extractPanorama(guiGraphics, deltaTicks)"
        else -> "else minecraft.gameRenderer.panorama().extractRenderState(guiGraphics, width, height)"
    }
    swaps["chat_message_content"] = when {
        !gui -> "return removeLines { it.id == id || it.content.string.noControlCodes == text }"
        else -> "return removeLines { it.id == id || it.content().string.noControlCodes == text }"
    }
    swaps["chat_message_copy"] = when {
        !gui -> "val line = GuiMessage(msg.addedTime, replaceWith, null, GuiMessageSource.SYSTEM_CLIENT, indicator)"
        else -> "val line = GuiMessage(msg.addedTime(), replaceWith, null, msg.source(), indicator)"
    }
    swaps["client_command_dispatch"] = when {
        !gui -> "return ClientCommandInternals.executeCommand(command)"
        else -> """
            val source = mc.connection?.suggestionsProvider as? FabricClientCommandSource ?: return false
            return ClientCommandInternals.executeCommand(command, source, null)
        """.trimIndent()
    }
    swaps["legacy_format_colors"] = when {
        !gui -> ".mapNotNull { it.color?.let { color -> color to it } }"
        else -> ".mapNotNull { formatting -> net.minecraft.network.chat.TextColor.fromLegacyFormat(formatting)?.getValue()?.let { it to formatting } }"
    }
    swaps["text_style_color"] = when {
        !gui -> "style.color?.value?.let { rgbMap[it]?.let(::append) }"
        else -> "style.getColor()?.value?.let { rgbMap[it]?.let(::append) }"
    }
    swaps["block_center_extension"] = when {
        !gui -> "// Empty."
        else -> "inline val BlockPos.center: Vec3 get() = Vec3(x + 0.5, y + 0.5, z + 0.5)"
    }
    swaps["render_buffer_size"] = when {
        !gui -> ".bufferSize(RenderType.TRANSIENT_BUFFER_SIZE)"
        else -> "// Empty."
    }
    swaps["primitive_topology_import"] = when {
        !gui -> "import com.mojang.blaze3d.vertex.VertexFormat"
        gui && !sdl -> "import com.mojang.blaze3d.PrimitiveTopology"
        else -> "import com.mojang.renderpearl.api.pipeline.PrimitiveTopology"
    }
    swaps["player_face"] = when {
        !gui -> """
            withMatrix {
                pose().rotate(180f.rad)
                PlayerFaceExtractor.extractRenderState(this, textures, x, y, size)
            }
        """.trimIndent()
        else -> "drawImage(textures.body.texturePath(), x, y, size, size)"
    }
    swaps["use_key_state"] = when {
        !sdl -> """
            InputConstants.Type.MOUSE -> Mouse.isButtonDown(useKey.value)
            InputConstants.Type.KEYSYM -> Keyboard.isKeyDown(useKey.value)
            InputConstants.Type.SCANCODE -> false
        """.trimIndent()
        else -> """
            InputConstants.Type.MOUSE -> Mouse.isSDLButtonDown(useKey.value)
            InputConstants.Type.KEYBOARD -> Keyboard.isKeyDown(useKey.value)
        """.trimIndent()
    }
    swaps["pet_rarity_colors"] = when {
        !gui -> """
            ChatFormatting.WHITE.color -> PetRarity.COMMON
            ChatFormatting.GREEN.color -> PetRarity.UNCOMMON
            ChatFormatting.BLUE.color -> PetRarity.RARE
            ChatFormatting.DARK_PURPLE.color -> PetRarity.EPIC
            ChatFormatting.GOLD.color -> PetRarity.LEGENDARY
            ChatFormatting.LIGHT_PURPLE.color -> PetRarity.MYTHIC
            ChatFormatting.RED.color -> PetRarity.SPECIAL
        """.trimIndent()
        else -> """
            TextColor.fromLegacyFormat(ChatFormatting.WHITE)?.getValue() -> PetRarity.COMMON
            TextColor.fromLegacyFormat(ChatFormatting.GREEN)?.getValue() -> PetRarity.UNCOMMON
            TextColor.fromLegacyFormat(ChatFormatting.BLUE)?.getValue() -> PetRarity.RARE
            TextColor.fromLegacyFormat(ChatFormatting.DARK_PURPLE)?.getValue() -> PetRarity.EPIC
            TextColor.fromLegacyFormat(ChatFormatting.GOLD)?.getValue() -> PetRarity.LEGENDARY
            TextColor.fromLegacyFormat(ChatFormatting.LIGHT_PURPLE)?.getValue() -> PetRarity.MYTHIC
            TextColor.fromLegacyFormat(ChatFormatting.RED)?.getValue() -> PetRarity.SPECIAL
        """.trimIndent()
    }
    swaps["interact_entity_packet"] = when {
        !gui -> "ServerboundInteractPacket(entity.id, InteractionHand.MAIN_HAND, entity.boundingBox.center.subtract(entity.position()), player.isShiftKeyDown)"
        else -> "ServerboundInteractPacket(entity.id, InteractionHand.MAIN_HAND, Vec3.ZERO, player.isShiftKeyDown)"
    }
    swaps["screen_extract_before"] = when {
        !gui -> "super.extractRenderState(ctx, mouseX, mouseY, deltaTicks)"
        else -> "// Empty."
    }
    swaps["screen_extract_after"] = when {
        !gui -> "// Empty."
        else -> "super.extractRenderState(ctx, mouseX, mouseY, deltaTicks)"
    }
    swaps["gui_text_shadow"] = when {
        !gui -> "ctx.drawText(string, 0, 0, colour, fontScale, shadow)"
        else -> """
            if (shadow) {
                val visual = Component.literal(string).visualOrderText
                val shadowSeq = FormattedCharSequence { sink ->
                    visual.accept { index, style, codePoint ->
                        val base = style.color?.value ?: colour
                        val dark = TextColor.fromRgb(base.multiply(0.25f))
                        sink.accept(index, style.withColor(dark), codePoint)
                    }
                }
                ctx.drawText(shadowSeq, fontScale, fontScale, shadow = false, scale = fontScale)
            }
            ctx.drawText(string, 0, 0, colour, fontScale, false)
        """.trimIndent()
    }
    swaps["ambient_light_hook"] = when {
        !gui -> """
            @ModifyExpressionValue(
                    method = "extract",
                    at = @At(
                            value = "INVOKE",
                            target = "Ljava/lang/Math;max(FF)F"
                    )
            )
            private float getAmbientLight(float original) {
                return should(RenderOptimiser.getFullBright()) ? 15.0f : original;
            }
        """.trimIndent()
        gui && !sdl -> """
            @ModifyExpressionValue(
                    method = "extract",
                    at = @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/util/ARGB;vector3fFromRGB24(I)Lorg/joml/Vector3f;"
                    ),
                    slice = @Slice(
                            from = @At(
                                    value = "FIELD",
                                    target = "Lnet/minecraft/world/attribute/EnvironmentAttributes;AMBIENT_LIGHT_COLOR:Lnet/minecraft/world/attribute/EnvironmentAttribute;"
                            )
                    )
            )
            private Vector3f getAmbientLight(Vector3f original) {
                return should(RenderOptimiser.getFullBright()) ? new Vector3f(15.0f) : original;
            }
        """.trimIndent()
        else -> """
            @WrapOperation(
                    method = "extract",
                    at = @At(
                            value = "FIELD",
                            target = "Lnet/minecraft/client/renderer/state/LightmapRenderState;ambientColor:Lorg/joml/Vector3fc;",
                            opcode = Opcodes.PUTFIELD
                    ),
                    require = 1
            )
            private void setAmbientLight(LightmapRenderState state, Vector3fc color, Operation<Void> original) {
                original.call(state, should(RenderOptimiser.getFullBright()) ? new Vector3f(15.0f) : color);
            }
        """.trimIndent()
    }
    swaps["nametag_hooks"] = when {
        !gui -> """
            @ModifyArgs(
                    method = "render",
                    at = @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/network/chat/Component;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font${'$'}DisplayMode;II)V"
                    )
            )
            private void draw(Args args) {
                if (!NameTags.INSTANCE.getEnabled()) return;
                args.set(4, NameTags.getShadow());
                if (NameTags.getCustomBg()) args.set(8, NameTags.getBgColour().getRgb());
            }
        """.trimIndent()
        gui && !sdl -> """
            @ModifyArg(
                    method = "prepareText",
                    at = @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/gui/Font;prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font${'$'}PreparedText;"
                    ),
                    index = 4
            )
            private static boolean quoi${'$'}modifyShadow(boolean shadow) {
                return NameTags.INSTANCE.getEnabled() ? NameTags.getShadow() : shadow;
            }

            @ModifyArg(
                    method = "prepareText",
                    at = @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/gui/Font;prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font${'$'}PreparedText;"
                    ),
                    index = 6
            )
            private static int quoi${'$'}modifyBackground(int backgroundColor) {
                return NameTags.INSTANCE.getEnabled() && NameTags.getCustomBg() ? NameTags.getBgColour().getRgb() : backgroundColor;
            }
        """.trimIndent()
        else -> """
            @ModifyArg(
                    method = "nameTag",
                    at = @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/renderer/feature/TextFeatureRenderer${'$'}Content${'$'}Text;<init>(FFLnet/minecraft/util/FormattedCharSequence;ZIII)V"
                    ),
                    index = 3
            )
            private static boolean quoi${'$'}modifyShadow(boolean shadow) {
                return NameTags.INSTANCE.getEnabled() ? NameTags.getShadow() : shadow;
            }

            @ModifyArg(
                    method = "nameTag",
                    at = @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/renderer/feature/TextFeatureRenderer${'$'}Content${'$'}Text;<init>(FFLnet/minecraft/util/FormattedCharSequence;ZIII)V"
                    ),
                    index = 5
            )
            private static int quoi${'$'}modifyBackground(int backgroundColor) {
                return NameTags.INSTANCE.getEnabled() && NameTags.getCustomBg() ? NameTags.getBgColour().getRgb() : backgroundColor;
            }
        """.trimIndent()
    }
    swaps["block_center_import"] = when {
        !gui -> "// Empty."
        else -> "import quoi.utils.center"
    }
}
