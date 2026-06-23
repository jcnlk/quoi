package quoi.utils.skyblock.player

import quoi.api.events.core.EventListener

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.minecraft.world.item.ItemStack
import quoi.QuoiMod.mc
import quoi.QuoiMod.scope
import quoi.annotations.Init
import quoi.api.commands.QuoiCommand
import quoi.api.commands.internal.GreedyString
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.on
import quoi.module.impl.misc.PetKeybinds.petMap
import quoi.utils.ChatUtils.modMessage
import quoi.utils.Scheduler.wait
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.skyblock.item.ItemUtils.lore
import quoi.utils.skyblock.item.ItemUtils.loreString
import quoi.utils.skyblock.player.ContainerUtils.closeContainer
import quoi.utils.skyblock.player.MovementUtils.stop
import java.util.ArrayDeque

/**
 * TODO:
 *  add block input option
 */

@Init
object PetUtils : EventListener {
    private const val PETS_MENU_TITLE = """(?:\(\d+/\d+\) )?Pets"""

    private val petQueue = ArrayDeque<PetRequest>()
    private var inProgress = false
    private var blockingMovement = false
    private var switchingPetName: String? = null

    init {
        QuoiCommand.command.sub("pet") { name: GreedyString ->
            if (!switchPet(name.string)) modMessage("&cFailed to queue pet switch.")
        }.description("Queues a pet switch by pet name.").suggests { petSuggestions() }

        on<TickEvent.Start> {
            val player = mc.player ?: return@on
            if (blockingMovement) player.stop()
        }

        on<WorldEvent.Change> {
            resetState()
        }
    }

    @JvmOverloads
    fun switchPet(name: String, item: String? = null, preventMove: Boolean = true): Boolean {
        val cleanedName = cleanPetName(name).trim()
        val cleanedItem = item?.let(::cleanPetItemName)?.takeIf(String::isNotEmpty)
        if (cleanedName.isEmpty()) return false
        if (petQueue.any { it.matches(cleanedName, cleanedItem) }) return false

        petQueue += PetRequest(cleanedName, cleanedItem, preventMove)
        processQueue()
        return true
    }

    fun isBusy(): Boolean = inProgress || petQueue.isNotEmpty()

    private fun processQueue() {
        if (inProgress || petQueue.isEmpty()) return
        inProgress = true

        scope.launch(Dispatchers.IO) {
            while (petQueue.isNotEmpty()) {
                val request = petQueue.removeFirst()
                switchingPetName = request.name

                val result = switchPetNow(request.name, request.item, request.preventMove)
                modMessage(result.chatMessage)
                switchingPetName = null
                wait(2)
            }

            switchingPetName = null
            stopMovementBlock()
            inProgress = false
        }
    }

    private suspend fun switchPetNow(name: String, item: String?, preventMove: Boolean): PetSwitchResult {
        val items = ContainerUtils.getContainerItems(
            "petsmenu",
            Regex(PETS_MENU_TITLE),
            onMenuOpen = { blockingMovement = preventMove },
        )
        if (items.isEmpty()) {
            stopMovementBlock()
            return PetSwitchResult.failure("Timed out opening Pets")
        }

        val slot = petSlots.firstOrNull { index ->
            val pet = items.getOrNull(index) ?: return@firstOrNull false
            val petName = cleanPetName(pet.displayName.string)
            petName.contains(name, ignoreCase = true) && pet.matchesPetItem(item)
        }

        if (slot == null) {
            closePetMenu()
            return PetSwitchResult.failure("Couldn't find ${petLabel(name, item)}")
        }

        val pet = items[slot] ?: run {
            closePetMenu()
            return PetSwitchResult.failure("Couldn't read ${petLabel(name, item)}")
        }

        return when {
            pet.isEquippedPet() -> {
                closePetMenu()
                PetSwitchResult.alreadyEquipped(petLabel(name, item))
            }

            pet.isSummonablePet() && ContainerUtils.click(slot, afterClick = ::stopMovementBlock) -> PetSwitchResult.success(petLabel(name, item))
            pet.isSummonablePet() -> {
                closePetMenu()
                PetSwitchResult.failure("Failed to click ${petLabel(name, item)}")
            }

            else -> {
                closePetMenu()
                PetSwitchResult.failure("${petLabel(name, item)} is not summonable")
            }
        }
    }

    private fun petSuggestions(): List<String> {
        return petMap.values
            .map(::cleanPetName)
            .distinctBy { it.lowercase() }
            .sorted()
    }

    private fun cleanPetName(name: String): String {
        return name.noControlCodes
            .replace(Regex("""⭐?\s*\[Lvl \d+] """), "")
            .trim('[', ']', ' ')
    }

    private fun cleanPetItemName(name: String): String = name.noControlCodes.trim()

    private fun petLabel(name: String, item: String?): String = item?.let { "$name ($it)" } ?: name

    private fun closePetMenu() {
        closeContainer()
        stopMovementBlock()
    }

    private fun stopMovementBlock() {
        blockingMovement = false
    }

    private fun resetState() {
        petQueue.clear()
        inProgress = false
        stopMovementBlock()
        switchingPetName = null
    }

    private fun ItemStack.isEquippedPet(): Boolean = loreString?.contains("Click to despawn!", ignoreCase = true) == true

    private fun ItemStack.isSummonablePet(): Boolean = loreString?.contains("Left-click to summon!", ignoreCase = true) == true

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

    private data class PetRequest(
        val name: String,
        val item: String?,
        val preventMove: Boolean,
    ) {
        fun matches(name: String, item: String?): Boolean {
            if (!this.name.equals(name, ignoreCase = true)) return false
            return when {
                this.item == null && item == null -> true
                this.item == null || item == null -> false
                else -> this.item.equals(item, ignoreCase = true)
            }
        }
    }

    private data class PetSwitchResult(
        val chatMessage: String,
    ) {
        companion object {
            fun success(name: String) = PetSwitchResult("&aSwitched to &f$name")
            fun alreadyEquipped(name: String) = PetSwitchResult("&e$name is already equipped")
            fun failure(reason: String) = PetSwitchResult("&c$reason")
        }
    }

    private val petSlots = (9..<45).filterNot { it % 9 == 0 || it % 9 == 8 }
}
