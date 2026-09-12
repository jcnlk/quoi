package quoi.utils.skyblock.player

import net.minecraft.world.item.ItemStack
import quoi.QuoiMod.mc
import quoi.annotations.Init
import quoi.api.commands.QuoiCommand
import quoi.utils.ChatUtils.modMessage
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.skyblock.player.container.menuSettings
import quoi.utils.skyblock.player.container.task.ContainerManager
import quoi.utils.skyblock.player.container.task.ContainerTaskResult
import quoi.utils.skyblock.player.container.task.menu
import quoi.utils.skyblock.player.container.task.containerTask

@Init
object WardrobeUtils {

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
        if (ContainerManager.active) return false

        val targetSlot = slot + 35
        var state = WardrobeState.UNKNOWN

        val newTask = containerTask(
            name = "Wardrobe $slot",
            settings = menuSettings(preventMovement = preventMove, blockInput = blockInput, fastMode = fastMode),
        ) {
            openContainer("wardrobe", "(1/3) Armor Sets")

            action {
                state = mc.player?.containerMenu?.items?.getOrNull(targetSlot)?.wardrobeState() ?: WardrobeState.EMPTY
            }
            check("Wardrobe slot $slot is empty") { state != WardrobeState.EMPTY }
            check("Wardrobe slot $slot is locked") { state != WardrobeState.LOCKED }
            check("Wardrobe slot $slot is not ready") { state != WardrobeState.UNKNOWN }

            pickup(targetSlot.menu).unless { disableUnequip && it.wardrobeState() == WardrobeState.EQUIPPED }
            closeContainer()
            awaitContainer("(1/3) Armor Sets")

            onFinished { result ->
                when (result) {
                    ContainerTaskResult.Success -> when {
                        disableUnequip && state == WardrobeState.EQUIPPED -> modMessage("&eWardrobe slot &f$slot &eis already equipped.")
                        else -> modMessage("&aEquipped wardrobe slot &f$slot")
                    }
                    ContainerTaskResult.Busy,
                    ContainerTaskResult.Cancelled -> Unit
                    is ContainerTaskResult.Failure -> modMessage("&c${result.message}.")
                }

            }
        }

        return newTask.run().result == null
    }

    private fun ItemStack.wardrobeState(): WardrobeState {
        val name = hoverName.string.noControlCodes
        return when {
            name.endsWith(": Empty") -> WardrobeState.EMPTY
            name.endsWith(": Equipped") -> WardrobeState.EQUIPPED
            name.endsWith(": Ready") -> WardrobeState.READY
            name.endsWith(": Locked") -> WardrobeState.LOCKED
            else -> WardrobeState.UNKNOWN
        }
    }

    private enum class WardrobeState {
        EQUIPPED, READY, EMPTY, LOCKED, UNKNOWN,
    }
}
