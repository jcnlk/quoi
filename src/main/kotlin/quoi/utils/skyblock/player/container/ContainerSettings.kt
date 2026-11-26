package quoi.utils.skyblock.player.container

import quoi.api.events.core.AreaBoundListener
import quoi.module.settings.group.SettingGroup

interface IContainerSettings {
    val startDelay: Pair<Int, Int> get() = 0 to 0
    val clickDelay: Pair<Int, Int> get() = 0 to 0
    val endDelay: Pair<Int, Int> get() = 0 to 0
    val invWalk: Boolean get() = true
    val silent: Boolean get() = true
    val blockInput: Boolean get() = !invWalk
    val showProgress: Boolean get() = true
}

class ContainerSettings(
    parent: AreaBoundListener,
    startDelay: Pair<Int, Int> = CONTAINER_MENU.startDelay,
    clickDelay: Pair<Int, Int> = CONTAINER_MENU.clickDelay,
    endDelay: Pair<Int, Int> = CONTAINER_MENU.endDelay,
    invWalk: Boolean = CONTAINER_MENU.invWalk,
    silent: Boolean = CONTAINER_MENU.silent,
    blockInput: Boolean = CONTAINER_MENU.blockInput,
    showProgress: Boolean = CONTAINER_MENU.showProgress,
) : SettingGroup(parent, "Settings"), IContainerSettings {
    init {
        for (range in listOf(startDelay, clickDelay, endDelay)) {
            require(range.first >= 0 && range.second >= range.first) { "Invalid container delay: $range" }
        }
    }

    override var startDelay by rangeSlider("Start delay", startDelay, 0, 10, unit = "t")
    override var clickDelay by rangeSlider("Click delay", clickDelay, 0, 10, unit = "t")
    override var endDelay by rangeSlider("End delay", endDelay, 0, 10, unit = "t")
    override var invWalk by switch("Inventory walk", invWalk)
    override var silent by switch("Silent GUI", silent)
    override var blockInput by switch(
        "Block input",
        blockInput,
        desc = "Blocks keyboard and mouse input during the task."
    )
    override var showProgress by switch(
        "Show progress",
        showProgress,
        desc = "Displays the current task and its progress."
    )
}

val CONTAINER_ZERO = object : IContainerSettings {}

val CONTAINER_MENU = object : IContainerSettings {
    override val clickDelay = 1 to 1
    override val invWalk = false
    override val blockInput = false
}
