package quoi.module.impl.general

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import quoi.api.events.TickEvent
import quoi.api.events.core.on
import quoi.api.skyblock.location.Island
import quoi.module.Module
import quoi.module.settings.Setting.Companion.json
import quoi.utils.ChatUtils.modMessage
import quoi.utils.skyblock.item.ItemUtils.extraAttributes
import quoi.utils.skyblock.player.container.ContainerSettings
import quoi.utils.skyblock.player.container.task.ContainerTask
import quoi.utils.skyblock.player.container.task.containerTask
import quoi.utils.skyblock.player.container.task.inv
import quoi.utils.skyblock.player.container.task.menu

object AutoBookCombine : Module(
    "Auto Book Combine",
    desc = "Automatically combines matching enchanted books in the Hypixel Anvil.",
    area = Island.Skyblock
) {
    private val containerSettings = ContainerSettings(this, showProgress = false)
    private val resultDelay by slider("Result delay", 10, 2, 40, 1, desc = "Delay before taking the combined book.", unit = " ticks").json("Result delay ticks")
    private val disableAfterFinish by switch("Disable after finish", true, desc = "Disables the module after all matching books have been combined.")
    private val autoCloseAfterFinish by switch("Auto close after finish", desc = "Closes the anvil after successfully combining books.")

    private var combineTask: ContainerTask? = null
    private var finishedForCurrentAnvil = false
    private var combinedInCurrentAnvil = false

    init {
        on<TickEvent.End> {
            val screen = currentAnvil() ?: return@on reset()

            if (finishedForCurrentAnvil || combineTask != null) return@on

            val pair = findBookPair(screen) ?: return@on finish(screen)
            val isCurrentAnvil = { currentAnvil()?.menu === screen.menu && player.containerMenu === screen.menu }

            val task = containerTask(
                name = name,
                settings = containerSettings,
            ) {
                check("Anvil was closed", isCurrentAnvil)
                quickMove(pair.first.inv).unless { !isCurrentAnvil() }

                check("Anvil was closed", isCurrentAnvil)
                quickMove(pair.second.inv).unless { !isCurrentAnvil() }

                repeat(2) {
                    check("Anvil was closed", isCurrentAnvil)
                    pickup(22.menu).unless { !isCurrentAnvil() } // combined book slot
                    wait(resultDelay)
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

        for (slot in screen.menu.slots.filter { it.container === player.inventory }) {
            val enchant = slot.item.singleEnchantment() ?: continue
            if (enchant.level == 5 || enchant.level == 10) continue

            val inventoryIndex = slot.containerSlot
            val inventorySlot = if (inventoryIndex in 0..8) inventoryIndex + 36 else inventoryIndex
            val firstSlot = firstSlots.putIfAbsent(enchant, inventorySlot)
            if (firstSlot != null) return firstSlot to inventorySlot
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

    private fun currentAnvil() = (mc.screen as? AbstractContainerScreen<*>)?.takeIf { it.title.string == "Anvil" }

    private data class Enchantment(val id: String, val level: Int)
}
