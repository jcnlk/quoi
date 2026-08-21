package quoi.module.impl.general

import kotlinx.coroutines.launch
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.world.item.ItemStack
import quoi.QuoiMod.scope
import quoi.api.commands.internal.GreedyString
import quoi.api.events.GuiEvent
import quoi.api.events.core.on
import quoi.api.input.Keybinds
import quoi.api.skyblock.Pet as SkyblockPet
import quoi.config.Config
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.impl.KeybindComponent
import quoi.module.settings.impl.MapSetting
import quoi.utils.ChatUtils
import quoi.utils.ChatUtils.button
import quoi.utils.ChatUtils.literal
import quoi.utils.ChatUtils.modMessage
import quoi.utils.skyblock.item.ItemUtils.loreString
import quoi.utils.skyblock.item.ItemUtils.skyblockId
import quoi.utils.skyblock.item.ItemUtils.skyblockUuid
import quoi.utils.skyblock.player.PetUtils.pet
import quoi.utils.skyblock.player.container.ContainerUtils
import quoi.utils.skyblock.player.container.ContainerUtils.clickSlot
import quoi.utils.skyblock.player.container.task.ContainerTaskResult
import quoi.utils.skyblock.player.container.task.containerTask

/**
 * modified OdinFabric (BSD 3-Clause)
 * copyright (c) 2025-2026 odtheking
 * original: https://github.com/odtheking/Odin/blob/main/src/main/kotlin/com/odtheking/odin/features/impl/skyblock/PetKeybinds.kt
 */
object PetKeybinds : Module(
    name = "Pet CatKeyboard",
    desc = "CatKeyboard for the pets menu."
) {
    private val unequipKeybind by keybind("Unequip", desc = "Unequips the current pet.")
    private val nextPageKeybind by keybind("Next page", desc = "Goes to the next page.")
    private val previousPageKeybind by keybind("Previous page", desc = "Goes to the previous page.")
    private val noUnequip by switch("Disable unequip", desc = "Prevents using a pets keybind to unequip a pet. Does not prevent unequip keybind or normal clicking.")
    private val closeIfAlreadyEquipped by switch("Close if already equipped", desc = "If the pet is already equipped, closes the Pets menu instead.")
    private val fastMode by switch("Fast mode", desc = "Blocks movement and input only while the Pets menu data is being read.")

    private val advanced by text("CatKeyboard")
    private val petKeys = (1..9).map { i ->
        KeybindComponent("Pet $i", Keybinds.KEY_0 + i, "Pet $i on the list.").childOf(::advanced).value
    }


    private val petsRegex = Regex(
        """^(?:\((\d+)/(\d+)\) )?Pets(?: \((\d+)/(\d+)\))?$""",
        RegexOption.IGNORE_CASE,
    )

    val petMap by MapSetting("PetKeys map", mutableMapOf<String, String>())

    private var petsCache = emptyList<ItemStack>()

    const val LIST_ID = 67
    const val GET_ID = 69

    init {
        val petCommand = command.sub("petkeybinds").description("Pet CatKeyboard module settings.")

        petCommand.sub("clear") {
            petMap.clear()
            Config.save()
        }.description("Clears the pet list.")

        petCommand.sub("list") {
            if (petMap.isEmpty()) return@sub modMessage("Pet list is empty!")
            modMessage(petMap.asPet().toClickable("list"), LIST_ID)
        }.description("Shows the pet list.")

        petCommand.sub("get") {
            scope.launch {
                petsCache = getPets()
                if (petsCache.isEmpty()) return@launch
                modMessage(petsCache.asPet().toClickable("get"), GET_ID)
            }
        }.description("Gets pets menu pets.")

        petCommand.sub("add") {
            val item = if (player.mainHandItem.skyblockId == "PET") player.mainHandItem else null
            val uuid = item?.skyblockUuid ?: return@sub modMessage("§cYou can only add pets to the pet list!")
            if (petMap.size >= 9) return@sub modMessage("§cYou cannot add more than 9 pets to the list. Remove a pet using §e/petkeys remove §cor clear the list using §e/petkeys clear§c.")
            if (uuid in petMap) return@sub modMessage("§cThis pet is already in the list!")

            val name = SkyblockPet.cleanName(item.hoverName.string)
            petMap[uuid] = name
            modMessage("§aAdded &r$name&a to the pet list in position §6${petMap.keys.indexOf(uuid) + 1}§a!")
            Config.save()
        }.description("Adds the pet you're holding to the pet list.")

        petCommand.sub("addfromuuidname") { source: String, uuid: String, name: GreedyString ->
            if (uuid in petMap) return@sub modMessage("§cThis pet is already in the list!")

            petMap[uuid] = name.string
//            modMessage("&aAdded &r$name&a to the pet list in position ${petMap.keys.indexOf(uuid) + 1}!")
            Config.save()
            when (source) {
                "list" -> modMessage(petMap.asPet().toClickable("list"), LIST_ID)
                "get"  -> modMessage(petsCache.asPet().toClickable("get"), GET_ID)
            }
        }

        petCommand.sub("removefromuuidname") { source: String, uuid: String, name: GreedyString ->
            if (uuid !in petMap) return@sub modMessage("§cThis pet is not in the list!")

            petMap.remove(uuid)
//            modMessage("&aRemoved &r$name&a pet from the pet list!")
            Config.save()
            when (source) {
                "list" -> modMessage(petMap.asPet().toClickable("list"), LIST_ID)
                "get"  -> modMessage(petsCache.asPet().toClickable("get"), GET_ID)
            }
        }

        petCommand.sub("remove") { uuidName: GreedyString ->
            val (uuid, name) = uuidName.string.split(" ", limit = 2)
            if (uuid !in petMap) return@sub modMessage("This pet is not in the list!")
            petMap.remove(uuid)
            modMessage("&aRemoved &r$name&a from the pet list!")
            Config.save()
        }.description("Removes the pet from the pet list.").suggests { petMap.entries.map { (uuid, name) -> "$uuid $name" } }

        on<GuiEvent.Click> {
            if (screen is AbstractContainerScreen<*> && onClick(screen, button)) cancel()
        }

        on<GuiEvent.Key.Press> {
            if (screen is AbstractContainerScreen<*> && onClick(screen, this.key)) cancel()
        }
    }

    fun List<Pet>.toClickable(source: String): MutableComponent {
        val result = literal("Pet list:\n")
        this.forEachIndexed { i, (uuid, name, heldItem) ->
            val symbol = if (uuid !in petMap) "&a[✔]" else "&c[x]"
            val command = if (uuid !in petMap) "addfromuuidname" else "removefromuuidname"
            val hoverText = if (uuid !in petMap) "Click to add!" else "Click to remove!"

            result.append(button(symbol, "/quoi petkeybinds $command $source $uuid $name", hoverText))
            result.append(literal(" "))

            val heldStr = if (heldItem != null) " &7($heldItem)" else ""
            result.append(
                literal("&6$name$heldStr").withStyle(
                    Style.EMPTY.withHoverEvent(HoverEvent.ShowText(literal("$uuid")))
                )
            )
            if (i != size - 1) result.append(literal("\n"))
        }
        return result
    }

    private fun onClick(screen: AbstractContainerScreen<*>, keyCode: Int): Boolean {
        val title = petsRegex.matchEntire(screen.title.string) ?: return false
        val current = (title.groupValues[1].ifEmpty { title.groupValues[3] }).toIntOrNull() ?: 1
        val total = (title.groupValues[2].ifEmpty { title.groupValues[4] }).toIntOrNull() ?: 1
        var slot = when (keyCode) {
            nextPageKeybind.key ->
                if (current < total) 53
                else return false.also { modMessage("§cYou are already on the last page.") }

            previousPageKeybind.key ->
                if (current > 1) 45
                else return false.also { modMessage("§cYou are already on the first page.") }

            unequipKeybind.key ->
                screen.menu.slots.subList(10, 43)
                    .indexOfFirst { it.item.loreString?.contains("Click to despawn!") == true }
                    .takeIf { it != -1 }?.plus(10) ?: return false.also { modMessage("§cCouldn't find equipped pet") }

            else -> {
                val petIndex = petKeys.indexOfFirst { it.key == keyCode }.takeIf { it != -1 } ?: return false
                petMap.entries.elementAtOrNull(petIndex)?.let { (uuid, _) ->
                    screen.menu.slots.subList(10, 43).indexOfFirst { it?.item?.skyblockUuid == uuid }
                }?.takeIf { it != -1 }?.plus(10)
                    ?: return false//.also { modMessage("§cCouldn't find matching pet or there is no pet in that position.") }
            }
        }

        if (screen.menu.slots[slot].item.loreString?.contains("Click to despawn!") == true && unequipKeybind.key != keyCode) {
//            modMessage("§cThat pet is already equipped!")
            if (closeIfAlreadyEquipped) slot = 49
            else if (noUnequip) return false
        }

        player.clickSlot(slot, screen.menu.containerId)
        return true
    }

    private suspend fun getPets(timeout: Int = 20): List<ItemStack> {
        var pets = emptyList<ItemStack>()
        val task = containerTask(
            name = "Read pets",
            force = fastMode,
            fastMode = fastMode,
            showProgress = false,
        ) {
            action { ChatUtils.command("petsmenu") }
            awaitContainer(
                Regex(
                    """^(?:\(\d+/\d+\) )?Pets(?: \(\d+/\d+\))?$""",
                    RegexOption.IGNORE_CASE,
                ),
                waitForItems = true,
                timeout = timeout,
            )
            action {
                pets = player.containerMenu.items
                    .slice(9..<45)
                    .filterIndexed { index, item -> index % 9 != 0 && index % 9 != 8 && !item.isEmpty }
            }
            action { player.closeContainer() }

            onFinished { result ->
                if (result != ContainerTaskResult.Busy && ContainerUtils.containerId != 0) {
                    player.closeContainer()
                }
            }
        }

        if (mc.isSameThread) task.run() else mc.execute { task.run() }
        val result = task.await()

        if (result != ContainerTaskResult.Success) {
            if (result is ContainerTaskResult.Failure) modMessage("&c${result.message}.")
            return emptyList()
        }

        return pets
    }

    fun List<ItemStack>.asPet(): List<Pet> = map { stack ->
        Pet(
            stack.skyblockUuid,
            SkyblockPet.cleanName(stack.hoverName.string),
            stack.pet?.heldItem?.removePrefix("PET_ITEM_") ?: "NONE"
        )
    }

    fun Map<String, String>.asPet(): List<Pet> = map { (uuid, name) ->
        Pet(uuid, name)
    }

    data class Pet(val uuid: String?, val name: String, val heldItem: String? = null)
}