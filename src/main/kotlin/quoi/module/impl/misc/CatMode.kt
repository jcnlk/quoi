package quoi.module.impl.misc

import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvents
import net.minecraft.util.FormattedCharSequence
import quoi.api.events.GuiEvent
import quoi.api.events.PacketEvent
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.utils.rad
import quoi.utils.render.DrawContextUtils.drawImage
import quoi.utils.render.DrawContextUtils.withMatrix
import kotlin.random.Random

object CatMode : Module(
    "Cat Mode",
    desc = "MEOWMEOWMEOWMEOWMEOWMEOWMEOW"
) {
    private val meowSound by switch("Meowound", desc = "Meow sound everywhere")
    private val meowText by switch("Meow meow?", desc = "Meow everywhere")
    private val fallingCats by switch("Catocalypsis", desc = "THEY'RE EVERYWHERE")
    private val darken by switch("Darken", desc = "Makes the kittens darker so they don't distract you.")
        .childOf(::fallingCats)
    private val catTexture by selector("Type", CatTexture.Trans, desc = "Texture used for the falling cats.")
        .childOf(::fallingCats)
    private val catSize by slider("Size", 15, 10, 50, 1, desc = "Size of the falling cats.", unit = "px")
        .childOf(::fallingCats)
    private val catSpeed by slider("Speed", 1.0f, 0.5f, 3.0f, 0.1f, desc = "Speed of the falling cats.")
        .childOf(::fallingCats)

    private val renderer = FallingCatsRenderer()

    init {
        on<GuiEvent.DrawPost> {
            if (!fallingCats || mc.level == null) return@on
            renderer.draw(ctx, screen.width, screen.height, catTexture.selected.path, catSize, catSpeed, darken)
        }

        on<PacketEvent.Received, ClientboundSoundPacket> {
            if (!meowSound || packet.sound == SoundEvents.CAT_AMBIENT) return@on

            cancel()
            mc.level?.playLocalSound(
                packet.x,
                packet.y,
                packet.z,
                SoundEvents.CAT_AMBIENT,
                packet.source,
                packet.volume,
                packet.pitch,
                false
            )
        }
    }

    @JvmStatic
    fun replaceText(text: String?): String? {
        if (text == null || !enabled || !meowText) return text
        return meowify(text)
    }

    @JvmStatic
    fun replaceText(text: FormattedCharSequence): FormattedCharSequence {
        if (!enabled || !meowText) return text

        val original = buildString {
            text.accept { _, _, codePoint ->
                appendCodePoint(codePoint)
                true
            }
        }
        val replaced = meowify(original)
        return if (replaced == original) text else Component.literal(replaced).visualOrderText
    }

    private fun meowify(text: String): String {
        if (text.isBlank()) return text

        val words = "\\S+".toRegex().findAll(text).count()
        return if (words == 0) text else List(words) { "meow" }.joinToString(" ")
    }

    private enum class CatTexture(val path: Identifier) {
        Trans(Identifier.parse("quoi:ui/fallingkittens/trans.png")),
        Flushed(Identifier.parse("quoi:ui/fallingkittens/flushed.png")),
        Bread(Identifier.parse("quoi:ui/fallingkittens/bread.png")),
        Cut(Identifier.parse("quoi:ui/fallingkittens/cut.png")),
        Toast(Identifier.parse("quoi:ui/fallingkittens/toast.png")),
    }

    private class FallingCatsRenderer {
        private val kittens = List(150) { Kitten() }

        fun draw(
            ctx: net.minecraft.client.gui.GuiGraphics,
            width: Int,
            height: Int,
            texture: Identifier,
            size: Int,
            speedMultiplier: Float,
            darken: Boolean
        ) {
            if (width <= 0 || height <= 0) return
            kittens.forEach {
                it.update(width, height, speedMultiplier)
                it.draw(ctx, texture, size, darken)
            }
        }
    }

    private class Kitten {
        private var x = 0f
        private var y = 0f
        private val speed = Random.nextFloat() * 2f + 1f
        private var rotation = Random.nextFloat() * 360f
        private val rotationSpeed = Random.nextFloat() * 2f - 1f
        private var lastUpdateTime = System.nanoTime()
        private var initialised = false

        fun update(width: Int, height: Int, speedMultiplier: Float) {
            if (!initialised) {
                resetPosition(width, height)
                initialised = true
                lastUpdateTime = System.nanoTime()
                return
            }

            val currentTime = System.nanoTime()
            var deltaTime = (currentTime - lastUpdateTime) / 10_000_000.0f

            if (deltaTime > 250f) {
                resetPosition(width, height)
                deltaTime = 0f
            }

            lastUpdateTime = currentTime

            y += speed * deltaTime * 0.25f * speedMultiplier
            rotation += rotationSpeed * deltaTime * 0.2f

            if (y - 50 > height) resetPosition(width, height, isOffscreen = true)
        }

        fun draw(
            ctx: net.minecraft.client.gui.GuiGraphics,
            texture: Identifier,
            size: Int,
            darken: Boolean
        ) {
            val offset = size / 2f

            ctx.withMatrix(x - offset, y - offset) {
                ctx.pose().translate(offset, offset)
                ctx.pose().rotate(rotation.rad)
                ctx.pose().translate(-offset, -offset)

                ctx.drawImage(texture, 0, 0, size, size)
                if (darken) ctx.fill(0, 0, size, size, 0x4D000000)
            }
        }

        private fun resetPosition(width: Int, height: Int, isOffscreen: Boolean = false) {
            x = Random.nextFloat() * width
            y = if (isOffscreen) -15f else Random.nextFloat() * height
        }
    }
}
