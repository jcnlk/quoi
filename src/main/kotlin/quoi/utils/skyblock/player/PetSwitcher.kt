package quoi.utils.skyblock.player

import net.minecraft.world.item.ItemStack
import quoi.QuoiMod.mc
import quoi.annotations.Init
import quoi.api.commands.QuoiCommand
import quoi.api.commands.internal.GreedyString
import quoi.api.events.PetEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.EventListener
import quoi.api.events.core.on
import quoi.api.skyblock.Pet
import quoi.utils.ChatUtils
import quoi.utils.ChatUtils.modMessage
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.skyblock.item.ItemUtils.lore
import quoi.utils.skyblock.item.ItemUtils.loreString
import quoi.utils.skyblock.player.PetUtils.pet
import quoi.utils.skyblock.player.container.ContainerUtils
import quoi.utils.skyblock.player.container.task.ContainerTask
import quoi.utils.skyblock.player.container.task.ContainerTaskResult
import quoi.utils.skyblock.player.container.task.containerTask
import quoi.utils.skyblock.player.container.task.item

@Init
object PetSwitcher : EventListener {
    private val menuTitle = Regex("""^(?:\(\d+/\d+\) )?Pets$""")

    @Volatile
    private var task: ContainerTask? = null

    @Volatile
    private var pendingSwitch: PendingSwitch? = null

    init {
        QuoiCommand.command.sub("pet") { name: GreedyString ->
            if (!switchPet(name.string)) modMessage("&cA container action is already in progress.")
        }.description("Switches pet by name.")

        on<PetEvent.Change> {
            val pending = pendingSwitch ?: return@on
            if (cause != PetEvent.Cause.SUMMON) return@on

            pendingSwitch = null
            val switchedPet = pet ?: return@on
            if (!pending.pet.matches(switchedPet)) return@on

            modMessage("&aSwitched to &f${pending.label}")
        }

        on<WorldEvent.Change> {
            pendingSwitch = null
        }
    }

    @JvmOverloads
    fun switchPet(
        name: String,
        item: String? = null,
        blockInput: Boolean = false,
        fastMode: Boolean = false,
    ): Boolean {
        val cleanedName = Pet.cleanName(name)
        val cleanedItem = item?.let(::cleanPetItemName)?.takeIf(String::isNotEmpty)
        if (cleanedName.isEmpty()) return false
        if (isBusy()) return false

        val label = item?.let { "$cleanedName ($it)" } ?: cleanedName
        val target = item { stack -> stack.matchesPet(cleanedName, cleanedItem) }.menu

        val newTask = containerTask(
            name = "Pet: $cleanedName",
            force = fastMode,
            preventMovement = true,
            blockInput = blockInput,
            fastMode = fastMode,
        ) {
            action { ChatUtils.command("petsmenu") }
            awaitContainer(menuTitle, waitForItems = true)
            pickup(target, failureMessage = "Couldn't find $label").unless { stack ->
                val summonable = stack.loreString?.contains("Left-click to summon!") == true
                pendingSwitch = stack.pet
                    ?.takeIf { summonable }
                    ?.let { PendingSwitch(it, label) }
                !summonable
            }
            action { mc.player?.closeContainer() }

            onFinished { result ->
                if (result != ContainerTaskResult.Success &&
                    result != ContainerTaskResult.Busy &&
                    ContainerUtils.containerId != 0
                ) {
                    mc.player?.closeContainer()
                }
                if (result != ContainerTaskResult.Success) pendingSwitch = null

                task = null
            }
        }

        task = newTask
        newTask.run()
        return newTask.result != ContainerTaskResult.Busy
    }

    fun isBusy(): Boolean = task?.let { it.result == null } == true || pendingSwitch != null

    private fun cleanPetItemName(name: String): String = name.noControlCodes.trim()

    private fun ItemStack.matchesPet(name: String, item: String?): Boolean {
        val petName = pet?.normalizedName ?: Pet.normalizeName(hoverName.string)
        return petName.contains(Pet.normalizeName(name)) && matchesPetItem(item)
    }

    private fun ItemStack.matchesPetItem(item: String?): Boolean {
        if (item == null) return true
        if (pet?.heldItem?.contains(item, ignoreCase = true) == true) return true

        val heldItem = lore?.firstNotNullOfOrNull { line ->
            val cleanedLine = line.noControlCodes
            cleanedLine
                .substringAfter("Held Item:", "")
                .trim()
                .takeIf { cleanedLine.startsWith("Held Item:", ignoreCase = true) }
        } ?: return false

        return heldItem.contains(item, ignoreCase = true)
    }

    private fun Pet.matches(other: Pet): Boolean =
        uuid?.let { it == other.uuid } ?: other.matches(name, rarity)

    private data class PendingSwitch(val pet: Pet, val label: String)
}
