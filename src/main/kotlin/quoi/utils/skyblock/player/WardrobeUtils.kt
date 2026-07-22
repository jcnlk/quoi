package quoi.utils.skyblock.player

import net.minecraft.world.item.ItemStack
import quoi.QuoiMod.mc
import quoi.annotations.Init
import quoi.api.commands.QuoiCommand
import quoi.api.skyblock.location.Location.inSkyblock
import quoi.utils.ChatUtils
import quoi.utils.ChatUtils.modMessage
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.skyblock.player.container.ContainerUtils
import quoi.utils.skyblock.player.container.task.ContainerTask
import quoi.utils.skyblock.player.container.task.ContainerTaskResult
import quoi.utils.skyblock.player.container.task.IndexSlot
import quoi.utils.skyblock.player.container.task.containerTask

@Init
object WardrobeUtils {
    private val menuTitle = Regex("""^\(\d+/\d+\) Armor Sets$""", RegexOption.IGNORE_CASE)

    private var task: ContainerTask? = null

    init {
        QuoiCommand.command.sub("wardrobe") { slot: Int ->
            if (!equip(slot)) modMessage("&cA container action is already in progress.")
        }.description("Equips a wardrobe slot from 1 to 9.")
    }

    @JvmOverloads
    fun equip(
        slot: Int,
        preventMove: Boolean = true,
        blockInput: Boolean = false,
        disableUnequip: Boolean = false,
        fastMode: Boolean = false,
    ): Boolean {
        if (slot !in 1..9) {
            modMessage("&cInvalid wardrobe slot. Use &e/quoi wardrobe <1-9>&c.")
            return false
        }
        if (!inSkyblock) {
            modMessage("&cYou are not in SkyBlock.")
            return false
        }
        if (task?.let { it.result == null } == true) return false

        val targetSlot = slot + 35
        var state = WardrobeState.UNKNOWN

        val newTask = containerTask(
            name = "Wardrobe $slot",
            force = fastMode,
            preventMovement = preventMove,
            blockInput = blockInput,
            fastMode = fastMode,
        ) {
            action { ChatUtils.command("wardrobe") }
            awaitContainer(menuTitle, waitForItems = true)

            action {
                state = mc.player?.containerMenu?.items?.getOrNull(targetSlot)?.wardrobeState()
                    ?: WardrobeState.EMPTY
            }
            check("Wardrobe slot $slot is empty") { state != WardrobeState.EMPTY }
            check("Wardrobe slot $slot is locked") { state != WardrobeState.LOCKED }
            check("Wardrobe slot $slot is not ready") { state != WardrobeState.UNKNOWN }

            pickup(IndexSlot(targetSlot, true))
                .unless { disableUnequip && it.wardrobeState() == WardrobeState.EQUIPPED }
            afterClick { mc.player?.closeContainer() }
            awaitContainer(menuTitle)
            action { mc.player?.closeContainer() }

            onFinished { result ->
                if (result != ContainerTaskResult.Success &&
                    result != ContainerTaskResult.Busy &&
                    ContainerUtils.containerId != 0
                ) {
                    mc.player?.closeContainer()
                }

                when (result) {
                    ContainerTaskResult.Success -> when {
                        disableUnequip && state == WardrobeState.EQUIPPED ->
                            modMessage("&eWardrobe slot &f$slot &eis already equipped.")
                        else -> modMessage("&aEquipped wardrobe slot &f$slot")
                    }
                    ContainerTaskResult.Busy -> Unit
                    ContainerTaskResult.Cancelled -> Unit
                    is ContainerTaskResult.Failure -> modMessage("&c${result.message}.")
                }

                task = null
            }
        }

        task = newTask
        newTask.run()
        return newTask.result != ContainerTaskResult.Busy
    }

    private fun ItemStack.wardrobeState(): WardrobeState {
        val name = displayName.string.noControlCodes
        return when {
            name.contains(": Empty", ignoreCase = true) -> WardrobeState.EMPTY
            name.contains(": Equipped", ignoreCase = true) -> WardrobeState.EQUIPPED
            name.contains(": Ready", ignoreCase = true) -> WardrobeState.READY
            name.contains(": Locked", ignoreCase = true) -> WardrobeState.LOCKED
            else -> WardrobeState.UNKNOWN
        }
    }

    private enum class WardrobeState {
        EQUIPPED,
        READY,
        EMPTY,
        LOCKED,
        UNKNOWN,
    }
}
