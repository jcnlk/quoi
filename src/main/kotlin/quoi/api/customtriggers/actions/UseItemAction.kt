package quoi.api.customtriggers.actions

import quoi.QuoiMod.mc
import quoi.api.abobaui.elements.ElementScope
import quoi.api.customtriggers.*
import quoi.config.TypeName
import quoi.utils.skyblock.player.PlayerUtils.interact

@TypeName("use_item")
class UseItemAction(var swing: Boolean = false) : TriggerAction {
    override fun execute(ctx: TriggerContext) {
        check(mc.player != null && mc.gameMode != null) { "Join a world to use an item" }
        interact(swing = swing)
    }

    override fun displayString() = "Use held item"

    override fun ElementScope<*>.draw() = toggleField("Swing hand", ::swing)
}
