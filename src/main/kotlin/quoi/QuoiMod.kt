package quoi

import kotlinx.coroutines.CoroutineScope
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry
import net.minecraft.client.Minecraft
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import quoi.annotations.AnnotationLoader
import quoi.api.commands.QuoiCommand
import quoi.api.events.GameEvent
import quoi.api.events.core.once
import quoi.config.Config
import quoi.module.ModuleManager
import quoi.utils.ui.hud.HudManager
import quoi.utils.ui.rendering.NVGSpecialRenderer
import kotlin.coroutines.EmptyCoroutineContext

object QuoiMod : ClientModInitializer {

    const val MOD_ID = "quoi"
    val mc: Minecraft get() = Minecraft.getInstance()
    val scope = CoroutineScope(EmptyCoroutineContext)
    val logger: Logger = LogManager.getLogger("quoi")

    override fun onInitializeClient() {
        PictureInPictureRendererRegistry.register { context ->
            NVGSpecialRenderer(context.bufferSource())
        }

        ModuleManager.initialise()
        AnnotationLoader.load()

        once<GameEvent.Load> {
            HudManager.init()
        }
        QuoiCommand.init()
        Config.load()
    }
}
