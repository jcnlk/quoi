package quoi.utils.skyblock.player

import net.minecraft.world.item.ItemStack
import quoi.QuoiMod.mc
import quoi.annotations.Init
import quoi.api.commands.QuoiCommand
import quoi.api.commands.internal.GreedyString
import quoi.api.skyblock.location.Location.inSkyblock
import quoi.module.impl.misc.PetKeybinds.petMap
import quoi.utils.ChatUtils
import quoi.utils.ChatUtils.modMessage
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.skyblock.item.ItemUtils.lore
import quoi.utils.skyblock.item.ItemUtils.loreString
import quoi.utils.skyblock.player.container.ContainerUtils
import quoi.utils.skyblock.player.container.task.ContainerTask
import quoi.utils.skyblock.player.container.task.ContainerTaskResult
import quoi.utils.skyblock.player.container.task.containerTask
import quoi.utils.skyblock.player.container.task.item
import quoi.utils.skyblock.player.container.task.menu

@Init
object PetUtils {
    private val menuTitle = Regex(
        """^(?:\(\d+/\d+\) )?Pets$""",
        RegexOption.IGNORE_CASE,
    )

    @Volatile
    private var task: ContainerTask? = null

    init {
        QuoiCommand.command.sub("pet") { name: GreedyString ->
            if (!switchPet(name.string)) modMessage("&cA container action is already in progress.")
        }.description("Switches pet by name.").suggests { petSuggestions() }
    }

    @JvmOverloads
    fun switchPet(
        name: String,
        item: String? = null,
        blockInput: Boolean = false,
        fastMode: Boolean = false,
    ): Boolean {
        val cleanedName = cleanPetName(name).trim()
        val cleanedItem = item?.let(::cleanPetItemName)?.takeIf(String::isNotEmpty)
        if (cleanedName.isEmpty()) return false
        if (!inSkyblock) {
            modMessage("&cYou are not in SkyBlock.")
            return false
        }
        if (task?.let { it.result == null } == true) return false

        var matchedState = PetState.NOT_FOUND
        val label = petLabel(cleanedName, cleanedItem)
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
                matchedState = when {
                    stack.isEquippedPet() -> PetState.EQUIPPED
                    stack.isSummonablePet() -> PetState.SUMMONABLE
                    else -> PetState.UNAVAILABLE
                }
                matchedState != PetState.SUMMONABLE
            }
            wait(1)
            action {
                if (matchedState != PetState.SUMMONABLE && ContainerUtils.containerId != 0) {
                    mc.player?.closeContainer()
                }
            }

            onFinished { result ->
                if (result != ContainerTaskResult.Success &&
                    result != ContainerTaskResult.Busy &&
                    ContainerUtils.containerId != 0
                ) {
                    mc.player?.closeContainer()
                }

                when (result) {
                    ContainerTaskResult.Success -> when (matchedState) {
                        PetState.EQUIPPED -> modMessage("&e$label is already equipped")
                        PetState.SUMMONABLE -> modMessage("&aSwitched to &f$label")
                        PetState.UNAVAILABLE -> modMessage("&c$label is not summonable")
                        PetState.NOT_FOUND -> modMessage("&cCouldn't find $label")
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

    fun isBusy(): Boolean = task?.let { it.result == null } == true

    private fun petSuggestions(): List<String> = petMap.values
        .map(::cleanPetName)
        .distinctBy { it.lowercase() }
        .sorted()

    private fun cleanPetName(name: String): String = name.noControlCodes
        .replace(Regex("""⭐?\s*\[Lvl \d+] """), "")
        .trim('[', ']', ' ')

    private fun cleanPetItemName(name: String): String = name.noControlCodes.trim()

    private fun petLabel(name: String, item: String?): String = item?.let { "$name ($it)" } ?: name

    private fun ItemStack.isEquippedPet(): Boolean =
        loreString?.contains("Click to despawn!", ignoreCase = true) == true

    private fun ItemStack.isSummonablePet(): Boolean =
        loreString?.contains("Left-click to summon!", ignoreCase = true) == true

    private fun ItemStack.matchesPet(name: String, item: String?): Boolean {
        val petName = cleanPetName(displayName.string)
        return petName.contains(name, ignoreCase = true) && matchesPetItem(item)
    }

    private fun ItemStack.matchesPetItem(item: String?): Boolean {
        if (item == null) return true

        val heldItem = lore?.firstNotNullOfOrNull { line ->
            val cleanedLine = line.noControlCodes
            cleanedLine
                .substringAfter("Held Item:", "")
                .trim()
                .takeIf { cleanedLine.startsWith("Held Item:", ignoreCase = true) }
        } ?: return false

        return heldItem.contains(item, ignoreCase = true)
    }

    private enum class PetState {
        NOT_FOUND,
        EQUIPPED,
        SUMMONABLE,
        UNAVAILABLE,
    }
}
