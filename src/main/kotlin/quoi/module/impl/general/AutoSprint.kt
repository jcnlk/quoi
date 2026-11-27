package quoi.module.impl.general

import quoi.api.events.TickEvent
import quoi.api.events.core.on
import quoi.api.input.Keyboard
import quoi.module.Module

object AutoSprint : Module(
    "Auto Sprint",
    desc = "Automatically sprints."
) {
    private var wasUnderWater = false

    init {
        on<TickEvent.End> {
            if (player.isInWater || player.isUnderWater) {
                if (!wasUnderWater) mc.options.keySprint.isDown = false
                wasUnderWater = true
            } else {
                mc.options.keySprint.isDown = true
                wasUnderWater = false
            }
        }
    }

    override fun onDisable() {
        val options = mc.options
        val sprintKey = options.keySprint
        val physicallyDown = !sprintKey.isUnbound && Keyboard.isKeyDown(sprintKey.key)
        if (sprintKey.isDown != physicallyDown) {
            sprintKey.isDown = physicallyDown || options.toggleSprint().get()
        }
    }
}
