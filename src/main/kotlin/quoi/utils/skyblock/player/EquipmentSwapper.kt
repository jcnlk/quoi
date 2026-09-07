package quoi.utils.skyblock.player

import net.minecraft.world.item.ItemStack
import quoi.QuoiMod.mc
import quoi.annotations.Init
import quoi.api.commands.QuoiCommand
import quoi.api.commands.internal.GreedyString
import quoi.utils.ChatUtils
import quoi.utils.ChatUtils.modMessage
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.skyblock.item.ItemUtils.skyblockId
import quoi.utils.skyblock.player.container.ContainerUtils
import quoi.utils.skyblock.player.container.task.ContainerTask
import quoi.utils.skyblock.player.container.task.ContainerTaskResult
import quoi.utils.skyblock.player.container.task.containerTask
import quoi.utils.skyblock.player.container.task.item

@Init
object EquipmentSwapper {
    private var task: ContainerTask? = null

    init {
        QuoiCommand.command.sub("equip") { name: GreedyString ->
            if (!equip(name.string)) modMessage("&cAn equipment swap is already in progress.")
        }.description("Equips items from your inventory through /eq.")
    }

    fun equip(
        name: String,
        blockInput: Boolean = false,
        fastMode: Boolean = false,
        itemId: String? = null,
        timeout: Int = 20,
    ): Boolean {
        val player = mc.player ?: return false
        if (task != null) return false

        val matches = { stack: ItemStack ->
            !stack.isEmpty && if (itemId != null) stack.skyblockId == itemId
            else stack.displayName.string.noControlCodes.contains(name, ignoreCase = true)
        }

        if ((36..39).any { matches(player.inventory.getItem(it)) }) return true

        val newTask = containerTask(
            name = "Equipment",
            force = fastMode,
            preventMovement = true,
            blockInput = blockInput,
            fastMode = fastMode,
        ) {
            action { ChatUtils.command("stats") }
            awaitContainer("Stats & Equipment", waitForItems = true, timeout = timeout)

            quickMove(item(matches).inv, timeout = timeout, failureMessage = "Could not find $name in your inventory.")
            afterClick { mc.player?.closeContainer() }

            awaitContainer("Stats & Equipment", timeout = timeout)

            onFinished { result ->
                if (result is ContainerTaskResult.Failure) {
                    modMessage("&cFailed to equip $name: ${result.message}")
                }
                if (result != ContainerTaskResult.Busy && ContainerUtils.containerId != 0) {
                    mc.player?.closeContainer()
                }

                task = null
            }
        }

        task = newTask
        newTask.run()
        return newTask.result != ContainerTaskResult.Busy
    }
}
