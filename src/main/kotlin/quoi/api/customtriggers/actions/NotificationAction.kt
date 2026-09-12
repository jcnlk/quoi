package quoi.api.customtriggers.actions

import quoi.api.abobaui.elements.ElementScope
import quoi.api.customtriggers.*
import quoi.config.TypeName
import quoi.utils.skyblock.player.PlayerUtils

@TypeName("notification")
class NotificationAction(var text: String = "", var subtitle: String = "", var duration: Int = 60) : TriggerAction {
    override fun validationError() = when {
        text.isBlank() && subtitle.isBlank() -> "Enter a title or subtitle."
        duration !in 1..1200 -> "Stay must be between 1 and 1200 ticks."
        else -> null
    }

    override fun execute(ctx: TriggerContext) = PlayerUtils.setTitle(ctx.expand(text), ctx.expand(subtitle), stayAlive = duration)
    override fun displayString() = "Show title: $text"
    override fun ElementScope<*>.draw() = fieldRow(
        { textField("Title", text) { text = it } },
        { textField("Subtitle", subtitle) { subtitle = it } },
        { intField("Stay (ticks)", { duration }, 1, 1200) { duration = it } },
        weights = listOf(2f, 2f, 1f)
    )
}
