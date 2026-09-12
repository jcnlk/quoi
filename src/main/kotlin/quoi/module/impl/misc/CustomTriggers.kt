package quoi.module.impl.misc

import quoi.api.customtriggers.TriggerManager
import quoi.api.customtriggers.ui.CustomTriggerEditor
import quoi.module.Module
import quoi.utils.ui.screens.UIScreen.Companion.open

object CustomTriggers : Module(
    "Custom Triggers",
    desc = "Run configurable actions in SkyBlock when events and conditions match.",
    tag = Tag.BETA
) {
    @Suppress("unused")
    private val editor by button("Open editor") { open(CustomTriggerEditor().build()) }

    init {
        TriggerManager.init()
        command.sub("ct") { open(CustomTriggerEditor().build()) }.description("Opens Custom Triggers editor.")
    }

    override fun onDisable() = TriggerManager.reset()
}