package quoi.utils.skyblock.player

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
import quoi.api.events.core.EventBus.on
import quoi.module.impl.misc.PetKeybinds.petMap
import quoi.utils.ChatUtils.modMessage
import quoi.utils.Scheduler.wait
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.skyblock.ItemUtils.lore
import quoi.utils.skyblock.ItemUtils.loreString
import quoi.utils.skyblock.player.ContainerUtils.closeContainer
import quoi.utils.skyblock.player.MovementUtils.stop
import java.util.ArrayDeque

@Init
object PetUtils {
    private val petQueue = ArrayDeque<PetRequest>()
    private var inProgress = false
    private var preventMoveCurrent = true
    private var switchingPetName: String? = null
        private set

    init {
        QuoiCommand.command.sub("pet") { name: GreedyString ->
            if (!switchPet(name.string)) modMessage("&cFailed to queue pet switch.")
        }.description("Queues a pet switch by pet name.").suggests { petSuggestions() }

        on<TickEvent.Start> {
            val player = mc.player ?: return@on
            if (preventMoveCurrent && inProgress) player.stop()
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
                preventMoveCurrent = request.preventMove
                switchingPetName = request.name

                val result = switchPetNow(request.name, request.item)
                modMessage(result.chatMessage)
                switchingPetName = null
                wait(2)
            }

            switchingPetName = null
            preventMoveCurrent = true
            inProgress = false
        }
    }

    private suspend fun switchPetNow(name: String, item: String?): PetSwitchResult {
        val items = ContainerUtils.getContainerItems("petsmenu", "Pets")
        if (items.isEmpty()) {
            return PetSwitchResult.failure("Timed out opening Pets")
        }

        val slot = petSlots.firstOrNull { index ->
            val pet = items.getOrNull(index) ?: return@firstOrNull false
            val petName = pet.displayName?.string?.let(::cleanPetName) ?: return@firstOrNull false
            petName.contains(name, ignoreCase = true) && pet.matchesPetItem(item)
        }

        if (slot == null) {
            closeContainer()
            return PetSwitchResult.failure("Couldn't find ${petLabel(name, item)}")
        }

        val pet = items[slot] ?: run {
            closeContainer()
            return PetSwitchResult.failure("Couldn't read ${petLabel(name, item)}")
        }

        return when {
            pet.isEquippedPet() -> {
                closeContainer()
                PetSwitchResult.alreadyEquipped(petLabel(name, item))
            }

            pet.isSummonablePet() && ContainerUtils.click(slot) -> PetSwitchResult.success(petLabel(name, item))
            pet.isSummonablePet() -> {
                closeContainer()
                PetSwitchResult.failure("Failed to click ${petLabel(name, item)}")
            }

            else -> {
                closeContainer()
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

    private fun resetState() {
        petQueue.clear()
        inProgress = false
        preventMoveCurrent = true
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
