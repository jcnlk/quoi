package quoi.utils.ui.notifications

import net.minecraft.client.gui.GuiGraphicsExtractor
import quoi.QuoiMod.mc
import quoi.annotations.Init
import quoi.api.abobaui.dsl.ms
import quoi.api.animations.Animation
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.events.GuiEvent
import quoi.api.events.RenderEvent
import quoi.api.events.core.EventListener
import quoi.api.events.core.on
import quoi.utils.ThemeManager.theme
import quoi.utils.height
import quoi.utils.ui.rendering.UIRenderer
import quoi.utils.ui.rendering.UIRenderer.defaultFont
import quoi.utils.width
import kotlin.math.max

/**
 * Toasts in the bottom right corner, drawn over the hud and any open screen.
 * Can be sent from any thread.
 */
@Init
object Notifications : EventListener {

    enum class Type {
        INFO, SUCCESS, WARNING, ERROR;

        val accent: Colour
            get() = when (this) {
                INFO -> theme.primary
                SUCCESS -> if (theme.isDark) SUCCESS_DARK else SUCCESS_LIGHT
                WARNING -> if (theme.isDark) WARNING_DARK else WARNING_LIGHT
                ERROR -> theme.error
            }
    }

    private val SUCCESS_DARK = Colour.RGB(120, 220, 140)
    private val SUCCESS_LIGHT = Colour.RGB(30, 140, 60)
    private val WARNING_DARK = Colour.RGB(255, 200, 90)
    private val WARNING_LIGHT = Colour.RGB(180, 120, 0)

    private const val WIDTH = 300f
    private const val MARGIN = 20f
    private const val GAP = 10f
    private const val PADDING = 12f
    private const val ACCENT_WIDTH = 4f
    private const val RADIUS = 8f
    private const val TITLE_SIZE = 17f
    private const val TEXT_SIZE = 14f
    private const val LINE_GAP = 3f
    private const val PROGRESS_HEIGHT = 2f
    private const val MAX_VISIBLE = 5

    private val ENTER = 350.ms
    private val EXIT = 250.ms
    private val MOVE = 300.ms

    private class Toast(
        val title: String,
        val message: String,
        val type: Type,
        val duration: Long,
    ) {
        var lines: List<String>? = null
        var height = 0f
        var createdAt = 0L
        var exiting = false
        var removed = false
        // reset on first draw
        var enter = Animation(ENTER, Animation.Style.EaseOutQuint)
        var exit: Animation? = null
        var y: Animation? = null

        val elapsed get() = System.currentTimeMillis() - createdAt

        fun dismiss() {
            if (exiting) return
            exiting = true
            exit = Animation(EXIT, Animation.Style.EaseInQuad).onFinish { removed = true }
        }
    }

    private val pending = java.util.concurrent.ConcurrentLinkedQueue<Toast>()
    private val toasts = ArrayList<Toast>()

    /**
     * @param title supports colour codes
     * @param message wrapped to the toast width
     * @param duration in ms
     */
    fun send(title: String, message: String = "", type: Type = Type.INFO, duration: Long = 4000L) {
        pending.add(Toast(title, message, type, duration.coerceAtLeast(500L)))
    }

    fun info(title: String, message: String = "", duration: Long = 4000L) = send(title, message, Type.INFO, duration)
    fun success(title: String, message: String = "", duration: Long = 4000L) = send(title, message, Type.SUCCESS, duration)
    fun warning(title: String, message: String = "", duration: Long = 4000L) = send(title, message, Type.WARNING, duration)
    fun error(title: String, message: String = "", duration: Long = 4000L) = send(title, message, Type.ERROR, duration)

    fun clear() {
        pending.clear()
        toasts.forEach { it.dismiss() }
    }

    init {
        // hud renders under screens
        on<RenderEvent.Overlay>(priority = -100) {
            if (mc.gui.screen() == null) draw(ctx)
        }

        on<GuiEvent.DrawPost>(priority = -100) {
            draw(ctx)
        }
    }

    private fun draw(ctx: GuiGraphicsExtractor) {
        while (true) toasts.add(0, pending.poll() ?: break)
        toasts.removeAll { it.removed }
        if (toasts.isEmpty()) return

        UIRenderer.frame(ctx) {
            var bottom = height - MARGIN
            var visible = 0

            for (toast in toasts) {
                if (toast.lines == null) layout(toast)
                if (toast.createdAt == 0L) {
                    toast.createdAt = System.currentTimeMillis()
                    toast.enter = Animation(ENTER, Animation.Style.EaseOutQuint)
                }

                if (!toast.exiting && (visible >= MAX_VISIBLE || toast.elapsed >= toast.duration)) toast.dismiss()
                if (!toast.exiting) visible++

                val targetY = bottom - toast.height
                val y = toast.y?.let {
                    if (it.to != targetY) it.retarget(targetY, MOVE, Animation.Style.EaseOutQuint)
                    it.get()
                } ?: targetY.also { toast.y = Animation(it, it, MOVE, Animation.Style.EaseOutQuint) }

                drawToast(toast, y)

                // free the slot while fading out
                val exitProgress = toast.exit?.get() ?: 0f
                bottom -= (toast.height + GAP) * (1f - exitProgress)
            }
        }
    }

    private fun layout(toast: Toast) {
        val textWidth = WIDTH - PADDING * 2 - ACCENT_WIDTH
        val lines = if (toast.message.isBlank()) emptyList()
        else toast.message.split('\n').flatMap { UIRenderer.wrapText(it, textWidth, TEXT_SIZE, defaultFont) }

        toast.lines = lines
        toast.height = PADDING * 2 + TITLE_SIZE +
                (if (lines.isEmpty()) 0f else LINE_GAP * 2 + lines.size * TEXT_SIZE + (lines.size - 1) * LINE_GAP) +
                PROGRESS_HEIGHT
    }

    private fun drawToast(toast: Toast, y: Float) {
        val enter = toast.enter.get()
        val exit = toast.exit?.get() ?: 0f
        val alpha = enter * (1f - exit)
        if (alpha <= 0.01f) return

        val slide = (1f - enter) * (WIDTH * 0.6f) + exit * (WIDTH * 0.3f)
        val x = width - MARGIN - WIDTH + slide
        val h = toast.height
        val accent = toast.type.accent

        UIRenderer.push()
        UIRenderer.globalAlpha(alpha)

        UIRenderer.dropShadow(x, y, WIDTH, h, blur = 10f, spread = 3f, radius = RADIUS, colour = Colour.BLACK.withAlpha(0.3f).rgb)
        UIRenderer.rect(x, y, WIDTH, h, theme.surfaceContainer.withAlpha(0.94f).rgb, RADIUS)
        UIRenderer.hollowRect(x, y, WIDTH, h, 1.5f, theme.outlineVariant.rgb, RADIUS)
        UIRenderer.rect(x, y, ACCENT_WIDTH, h, accent.rgb, RADIUS, RADIUS, 0f, 0f)

        val textX = x + ACCENT_WIDTH + PADDING
        var textY = y + PADDING
        UIRenderer.formattedText(toast.title, textX, textY, TITLE_SIZE, theme.onSurface.rgb, defaultFont)
        textY += TITLE_SIZE + LINE_GAP * 2

        toast.lines?.forEach {
            UIRenderer.formattedText(it, textX, textY, TEXT_SIZE, theme.onSurfaceVariant.rgb, defaultFont)
            textY += TEXT_SIZE + LINE_GAP
        }

        // remaining time
        val remaining = if (toast.exiting) 0f else 1f - (toast.elapsed.toFloat() / toast.duration).coerceIn(0f, 1f)
        val trackX = x + ACCENT_WIDTH
        val trackW = WIDTH - ACCENT_WIDTH
        val barY = y + h - PROGRESS_HEIGHT
        UIRenderer.pushScissor(trackX, barY, trackW, PROGRESS_HEIGHT)
        UIRenderer.rect(trackX, barY, max(0f, trackW * remaining), PROGRESS_HEIGHT, accent.withAlpha(0.8f).rgb, 0f, 0f, 0f, 0f)
        UIRenderer.popScissor()

        UIRenderer.pop()
    }
}
