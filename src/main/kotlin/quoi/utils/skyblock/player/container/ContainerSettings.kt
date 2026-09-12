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
    val fastMode: Boolean get() = false
    val showProgress: Boolean get() = true
}

class ContainerSettings(parent: AreaBoundListener) : SettingGroup(parent, "Settings"), IContainerSettings {
    override val startDelay by rangeSlider("Start delay", 1 to 2, 0, 5, unit = "t")
    override val clickDelay by rangeSlider("Click delay", 1 to 2, 0, 5, unit = "t")
    override val endDelay by rangeSlider("End delay", 1 to 2, 0, 5, unit = "t")
    override val invWalk by switch("Inventory walk")
    override val silent by switch("Silent GUI")
}

val CONTAINER_ZERO = object : IContainerSettings {}

/**
 * Container settings for callers without a [ContainerSettings] group
 */
data class ContainerOptions(
    override val startDelay: Pair<Int, Int> = 0 to 0,
    override val clickDelay: Pair<Int, Int> = 0 to 0,
    override val endDelay: Pair<Int, Int> = 0 to 0,
    override val invWalk: Boolean = true,
    override val silent: Boolean = true,
    override val blockInput: Boolean = !invWalk,
    override val fastMode: Boolean = false,
    override val showProgress: Boolean = true,
) : IContainerSettings {
    init {
        for (range in listOf(startDelay, clickDelay, endDelay)) {
            require(range.first >= 0 && range.second >= range.first) { "Invalid container delay: $range" }
        }
    }
}

/**
 * Creates settings for menu commands and triggers
 * Fast mode removes the click delay and only blocks movement/input from opening through the last click
 */
fun menuSettings(
    preventMovement: Boolean = true,
    blockInput: Boolean = false,
    fastMode: Boolean = false,
    showProgress: Boolean = true,
): IContainerSettings = ContainerOptions(
    clickDelay = if (fastMode) 0 to 0 else 1 to 1,
    invWalk = !preventMovement,
    blockInput = blockInput,
    fastMode = fastMode,
    showProgress = showProgress,
)
