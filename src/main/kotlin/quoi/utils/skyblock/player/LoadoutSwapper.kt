package quoi.utils.skyblock.player

import net.minecraft.world.item.ItemStack
import quoi.QuoiMod.mc
import quoi.annotations.Init
import quoi.api.commands.QuoiCommand
import quoi.utils.ChatUtils
import quoi.utils.ChatUtils.modMessage
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.skyblock.item.ItemUtils.loreString
import quoi.utils.skyblock.player.container.ContainerUtils
import quoi.utils.skyblock.player.container.task.ContainerTask
import quoi.utils.skyblock.player.container.task.ContainerTaskResult
import quoi.utils.skyblock.player.container.task.IndexSlot
import quoi.utils.skyblock.player.container.task.containerTask

@Init
object LoadoutSwapper {
    private val loadoutSlots = listOf(14, 15, 16, 23, 24, 25, 32, 33, 34, 41, 42, 43)
    private var task: ContainerTask? = null

    init {
        QuoiCommand.command.sub("loadout") { slot: Int ->
            if (!equip(slot)) modMessage("&cA container action is already in progress.")
        }.description("Equips a loadout slot from 1 to ${loadoutSlots.size}.")
    }

    @JvmOverloads
    fun equip(
        slot: Int,
        preventMove: Boolean = true,
        blockInput: Boolean = false,
        fastMode: Boolean = false,
    ): Boolean {
        if (slot !in 1..loadoutSlots.size) {
            modMessage("&cInvalid loadout slot. Use &e/quoi loadout <1-${loadoutSlots.size}>&c.")
            return false
        }
        if (task?.result == null) return false

        val targetSlot = loadoutSlots[slot - 1]
        val newTask = containerTask(
            name = "Loadout $slot",
            force = fastMode,
            preventMovement = preventMove,
            blockInput = blockInput,
            fastMode = fastMode,
        ) {
            action { ChatUtils.command("loadout") }
            awaitContainer("(1/3) Loadouts", waitForItems = true)
            check("Loadout slot $slot is not equipable") {
                mc.player?.containerMenu?.items?.getOrNull(targetSlot)?.isLoadoutButton() == true
            }
            pickup(IndexSlot(targetSlot, true))
            action { mc.player?.closeContainer() }
            awaitContainer("(1/3) Loadouts")
            action { mc.player?.closeContainer() }

            onFinished { result ->
                if (result != ContainerTaskResult.Success &&
                    result != ContainerTaskResult.Busy &&
                    ContainerUtils.containerId != 0
                ) {
                    mc.player?.closeContainer()
                }

                when (result) {
                    ContainerTaskResult.Success -> modMessage("&aEquipped loadout &f$slot")
                    ContainerTaskResult.Busy,
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

    private fun ItemStack.isLoadoutButton(): Boolean {
        val lore = loreString?.noControlCodes ?: return false
        return lore.contains("Left-click to equip!")
    }
}
