package quoi.api.commands

import quoi.api.commands.internal.BaseCommand
import quoi.api.commands.internal.GreedyString
import quoi.utils.ChatUtils.modMessage
import quoi.utils.skyblock.player.container.CONTAINER_MENU
import quoi.utils.skyblock.player.container.task.containerTask
import quoi.utils.skyblock.player.container.task.presets.equipItem
import quoi.utils.skyblock.player.container.task.presets.equipLoadout
import quoi.utils.skyblock.player.container.task.presets.equipPet
import quoi.utils.skyblock.player.container.task.presets.equipWardrobe
import kotlin.jvm.JvmSerializableLambda

object SwapCommands {
    /**
     * Adds pet, equipment, loadout and wardrobe subcommands to [command]
     */
    fun addTo(command: BaseCommand) {
        command.sub("pet", @JvmSerializableLambda { name: GreedyString ->
            containerTask(name = "Pet: ${name.string}", settings = CONTAINER_MENU) {
                equipPet(name.string)
                onComplete { modMessage("&aSwitched to &f${name.string}") }
            }.run()
        }).description("Switches pet by name.")

        command.sub("equip", @JvmSerializableLambda { name: GreedyString ->
            containerTask(name = "Equipment", settings = CONTAINER_MENU) {
                equipItem(name.string)
                onComplete { modMessage("&aEquipped &f${name.string}") }
            }.run()
        }).description("Equips items from your inventory through /eq.")

        command.sub("loadout", @JvmSerializableLambda { slot: Int ->
            containerTask(name = "Loadout $slot", settings = CONTAINER_MENU) {
                equipLoadout(slot)
                onComplete { modMessage("&aEquipped loadout &f$slot") }
            }.run()
        }).description("Equips a loadout slot from 1 to 12.")

        command.sub("wardrobe", @JvmSerializableLambda { slot: Int ->
            containerTask(name = "Wardrobe $slot", settings = CONTAINER_MENU) {
                equipWardrobe(slot)
                onComplete { modMessage("&aEquipped wardrobe slot &f$slot") }
            }.run()
        }).description("Equips a wardrobe slot from 1 to 9.")
    }
}
