package quoi.module.impl.misc

import quoi.api.events.core.on
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomData
import quoi.api.events.TickEvent
import quoi.api.skyblock.location.Location
import quoi.module.Module
import quoi.utils.ChatUtils.modMessage
import quoi.utils.skyblock.player.container.ContainerUtils.clickSlot

object AutoBookCombine : Module(
    "Auto Book Combine",
    desc = "Automatically combines matching enchanted books in the Hypixel Anvil."
) {
    private val clickDelay by slider("Click delay", 200L, 50L, 1_000L, 50L, desc = "Delay between clicks.", "ms")
    private val resultDelay by slider("Result delay", 500L, 100L, 2_000L, 50L, desc = "Delay before taking the combined book.", "ms")
    private val disableAfterFinish by switch("Disable after finish", true, desc = "Disables the module after all matching books have been combined.")
    private val autoCloseAfterFinish by switch("Auto close after finish", false, desc = "Closes the anvil after successfully combining books.")

    private var currentStep = 0
    private var nextActionAt = 0L
    private var activePair: Pair<Int, Int>? = null
    private var wasInAnvil = false
    private var finishedForCurrentAnvil = false
    private var combinedInCurrentAnvil = false

    init {
        on<TickEvent.End> {
            val screen = mc.screen as? AbstractContainerScreen<*>
            if (!Location.inSkyblock || screen?.title?.string != "Anvil") {
                if (wasInAnvil) reset()
                wasInAnvil = false
                return@on
            }

            wasInAnvil = true

            val now = System.currentTimeMillis()
            if (now < nextActionAt) return@on
            if (finishedForCurrentAnvil) return@on

            val pair = activePair ?: nextPair(screen) ?: return@on

            when (currentStep) {
                0 -> click(pair.first, screen, clickDelay, shift = true)
                1 -> click(pair.second, screen, clickDelay, shift = true)
                2 -> {
                    click(RESULT_SLOT, screen, resultDelay)
                    combinedInCurrentAnvil = true
                }
                3 -> click(RESULT_SLOT, screen, resultDelay)
                else -> {
                    activePair = null
                    currentStep = 0
                    return@on
                }
            }

            currentStep++
        }
    }

    override fun onDisable() {
        reset()
        super.onDisable()
    }

    private fun click(slot: Int, screen: AbstractContainerScreen<*>, delay: Long, shift: Boolean = false) {
        player.clickSlot(slot, screen.menu.containerId, shift = shift)
        nextActionAt = System.currentTimeMillis() + delay
    }

    private fun nextPair(screen: AbstractContainerScreen<*>): Pair<Int, Int>? {
        val pair = findBookPair(screen)
        if (pair == null) {
            if (combinedInCurrentAnvil) {
                modMessage("&aFinished Book Combining!")
                if (autoCloseAfterFinish) {
                    screen.onClose()
                }
            }
            reset()
            finishedForCurrentAnvil = true
            if (disableAfterFinish) {
                toggle()
            }
            return null
        }

        activePair = pair
        currentStep = 0
        return pair
    }

    private fun findBookPair(screen: AbstractContainerScreen<*>): Pair<Int, Int>? {
        val bookPairs = linkedMapOf<String, MutableList<Int>>()

        screen.menu.slots
            .drop(CONTAINER_SIZE)
            .forEach { slot ->
                val enchant = slot.item.singleEnchantmentKey() ?: return@forEach
                bookPairs.getOrPut(enchant) { mutableListOf() }.add(slot.index)
            }

        return bookPairs.entries.firstNotNullOfOrNull { (enchant, books) ->
            if (books.size > 1 && !enchant.endsWith("5") && !enchant.endsWith("10")) {
                books[0] to books[1]
            } else null
        }
    }

    private fun ItemStack.singleEnchantmentKey(): String? {
        if (isEmpty || item != Items.ENCHANTED_BOOK) return null

        val enchantments = getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
            .copyTag()
            .getCompound("enchantments")
            .orElse(null)
            ?: return null

        val keys = enchantments.keySet()
        if (keys.size != 1) return null

        val key = keys.first()
        val level = enchantments.getInt(key).orElse(null) ?: return null
        return "$key$level"
    }

    private fun reset() {
        currentStep = 0
        nextActionAt = 0L
        activePair = null
        finishedForCurrentAnvil = false
        combinedInCurrentAnvil = false
    }

    private const val CONTAINER_SIZE = 54
    private const val RESULT_SLOT = 22
}
