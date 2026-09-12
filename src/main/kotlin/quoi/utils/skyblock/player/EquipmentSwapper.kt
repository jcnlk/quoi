package quoi.utils.skyblock.player

import net.minecraft.world.item.ItemStack
import quoi.QuoiMod.mc
import quoi.annotations.Init
import quoi.api.commands.QuoiCommand
import quoi.api.commands.internal.GreedyString
import quoi.utils.ChatUtils.modMessage
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.skyblock.player.container.menuSettings
import quoi.utils.skyblock.player.container.task.ContainerManager
import quoi.utils.skyblock.player.container.task.ContainerTaskResult
import quoi.utils.skyblock.player.container.task.containerTask
import quoi.utils.skyblock.player.container.task.item

@Init
object EquipmentSwapper {

    init {
        QuoiCommand.command.sub("equip") { name: GreedyString ->
            if (!equip(name.string)) modMessage("&cAn equipment swap is already in progress.")
        }.description("Equips items from your inventory through /eq.")
    }

    fun equip(
        name: String,
        blockInput: Boolean = false,
        fastMode: Boolean = false,
    ): Boolean {
        val player = mc.player ?: return false
        if (name.isBlank() || ContainerManager.active) return false

        val matches = { stack: ItemStack ->
            !stack.isEmpty && stack.displayName.string.noControlCodes.contains(name, ignoreCase = true)
        }

        if ((36..39).any { matches(player.inventory.getItem(it)) }) return true

        val newTask = containerTask(
            name = "Equipment",
            settings = menuSettings(blockInput = blockInput, fastMode = fastMode),
        ) {
            openContainer("stats", "Stats & Equipment")

            quickMove(item(matches).inv)
            closeContainer()

            awaitContainer("Stats & Equipment")

            onFinished { result ->
                if (result is ContainerTaskResult.Failure) modMessage("&c${result.message}.")
            }
        }

        return newTask.run().result == null
    }
}