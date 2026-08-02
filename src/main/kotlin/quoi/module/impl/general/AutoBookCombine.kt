package quoi.module.impl.general

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import quoi.api.events.TickEvent
import quoi.api.events.core.on
import quoi.api.skyblock.location.Location
import quoi.module.Module
import quoi.module.settings.Setting.Companion.json
import quoi.utils.ChatUtils.modMessage
import quoi.utils.skyblock.item.ItemUtils.extraAttributes
import quoi.utils.skyblock.player.container.ContainerUtils.containerSize
import quoi.utils.skyblock.player.container.task.ContainerTask
import quoi.utils.skyblock.player.container.task.any
import quoi.utils.skyblock.player.container.task.containerTask

object AutoBookCombine : Module(
    "Auto Book Combine",
    desc = "Automatically combines matching enchanted books in the Hypixel Anvil."
) {
    private val clickDelay by slider("Click delay", 4, 1, 20, 1, desc = "Delay between clicks.", unit = " ticks").json("Click delay ticks")
    private val resultDelay by slider("Result delay", 10, 2, 40, 1, desc = "Delay before taking the combined book.", unit = " ticks").json("Result delay ticks")
    private val disableAfterFinish by switch("Disable after finish", true, desc = "Disables the module after all matching books have been combined.")
    private val autoCloseAfterFinish by switch("Auto close after finish", false, desc = "Closes the anvil after successfully combining books.")

    private var combineTask: ContainerTask? = null
    private var finishedForCurrentAnvil = false
    private var combinedInCurrentAnvil = false

    init {
        on<TickEvent.End> {
            val screen = currentAnvil() ?: return@on reset()

            if (finishedForCurrentAnvil || combineTask != null) return@on

            val pair = findBookPair(screen) ?: return@on finish(screen)

            val containerId = screen.menu.containerId
            val isCurrentAnvil = {
                currentAnvil()?.menu?.containerId == containerId
            }

            val task = containerTask(
                force = true,
                preventMovement = false,
                blockInput = false,
                showProgress = false,
            ) {
                check("Anvil was closed", isCurrentAnvil)
                quickMove(pair.first.any)
                wait(clickDelay + 1)

                check("Anvil was closed", isCurrentAnvil)
                quickMove(pair.second.any)
                wait(clickDelay + 1)

                repeat(2) {
                    check("Anvil was closed", isCurrentAnvil)
                    pickup(22.any) // combined book slot
                    wait(resultDelay + 1)
                }

                onComplete { combinedInCurrentAnvil = true }
                onFinished { combineTask = null }
            }

            combineTask = task
            task.run()
        }
    }

    override fun onDisable() {
        reset()
        super.onDisable()
    }

    private fun finish(screen: AbstractContainerScreen<*>) {
        if (combinedInCurrentAnvil) {
            modMessage("&aFinished Book Combining!")
            if (autoCloseAfterFinish) screen.onClose()
        }

        reset()
        finishedForCurrentAnvil = true
        if (disableAfterFinish) toggle()
    }

    private fun findBookPair(screen: AbstractContainerScreen<*>): Pair<Int, Int>? {
        val firstSlots = mutableMapOf<Enchantment, Int>()

        for (slot in screen.menu.slots.drop(screen.menu.type.containerSize)) {
            val enchant = slot.item.singleEnchantment() ?: continue
            if (enchant.level == 5 || enchant.level == 10) continue

            val firstSlot = firstSlots.putIfAbsent(enchant, slot.index)
            if (firstSlot != null) return firstSlot to slot.index
        }

        return null
    }

    private fun ItemStack.singleEnchantment(): Enchantment? {
        if (isEmpty || item != Items.ENCHANTED_BOOK) return null

        val enchantments = extraAttributes?.getCompound("enchantments")?.orElse(null) ?: return null

        val keys = enchantments.keySet()
        if (keys.size != 1) return null

        val key = keys.first()
        val level = enchantments.getInt(key).orElse(null) ?: return null
        return Enchantment(key, level)
    }

    private fun reset() {
        combineTask?.cancel()
        combineTask = null
        finishedForCurrentAnvil = false
        combinedInCurrentAnvil = false
    }

    private fun currentAnvil() = (mc.gui.screen() as? AbstractContainerScreen<*>)?.takeIf { Location.inSkyblock && it.title.string == "Anvil" }

    private data class Enchantment(val id: String, val level: Int)
}
