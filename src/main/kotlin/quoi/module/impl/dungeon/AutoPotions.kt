package quoi.module.impl.dungeon

import net.minecraft.world.item.ItemStack
import quoi.api.events.ChatEvent
import quoi.api.events.DungeonEvent
import quoi.api.events.core.on
import quoi.api.skyblock.dungeon.Floor
import quoi.api.skyblock.location.Island
import quoi.api.skyblock.location.invoke
import quoi.module.Module
import quoi.utils.ChatUtils
import quoi.utils.ChatUtils.modMessage
import quoi.utils.Scheduler
import quoi.utils.skyblock.item.ItemUtils.extraAttributes
import quoi.utils.skyblock.player.container.ContainerUtils
import quoi.utils.skyblock.player.container.task.ContainerTask
import quoi.utils.skyblock.player.container.task.ContainerTaskResult
import quoi.utils.skyblock.player.container.task.containerTask
import quoi.utils.skyblock.player.container.task.menu

/**
 * TODO:
 *  auto swap to hotbar?
 *  auto drink?
 */

// https://github.com/Noamm9/CatgirlAddons/blob/main/src/main/kotlin/catgirlroutes/module/impl/dungeons/AutoPot.kt
object AutoPotions : Module(
    "Auto Potions",
    desc = "Automatically gets a potion from your potion bag.",
    area = Island.Dungeon(inClear = true)
) {
    private val preventMoving by switch("Prevent moving", desc = "Stops your movement while grabbing potions is in progress.")
    private val blockInputs by switch("Block inputs", desc = "Blocks keyboard and mouse input while grabbing potions.")
    private val fastMode by switch("Fast mode", desc = "Blocks movement and input only from the menu opening until the target click.")
    private val triggerDelay by slider("Trigger delay", 1, 0, 20, 1, "Delay until potion grab starts.", "t")
    private val grabDelay by slider("Grab delay", 0, 0, 10, 1, "Extra delay before grabbing potion from potion bag.", "t")

    private val floors by multiSelect(
        "Floors",
        setOf(Floor.M7),
        Floor.entries,
        desc = "Floors where a potion is obtained automatically."
    )
    private val minimumPotionTier by selector(
        "Minimum potion tier",
        PotionTier.VII,
        desc = "Lowest Dungeon Potion tier potion."
    )

    private var task: ContainerTask? = null

    init {
        on<DungeonEvent.Enter> {
            Scheduler.scheduleTask(triggerDelay) {
                if (!active || floor !in floors) return@scheduleTask modMessage("no floor match")
                if (task?.let { it.result == null } == true) return@scheduleTask modMessage("result shit")

                val inventory = mc.player?.inventory?.nonEquipmentItems?.take(36) ?: return@scheduleTask modMessage("inv shit")
                val existingTier = inventory.mapNotNull { it.dungeonPotionTier() }.maxByOrNull(PotionTier::level)

                if (existingTier != null && existingTier.level >= minimumPotionTier.selected.level) {
                    modMessage("&eAlready have a Dungeon $existingTier Potion.")
                    return@scheduleTask
                }

                if (inventory.none(ItemStack::isEmpty)) {
                    modMessage("&cCouldn't get a potion: your inventory is full.")
                    return@scheduleTask
                }

                modMessage("Getting potion!")

                val newTask = containerTask(
                    name = "Getting potion",
                    force = true,
                    preventMovement = preventMoving,
                    blockInput = blockInputs,
                    fastMode = fastMode
                ) {
                    action { ChatUtils.command("potionbag") }
                    awaitContainer("Potion Bag", waitForItems = true)
                    check("No potion left in slot 1 of your Potion Bag") {
                        player.containerMenu.items.getOrNull(0)?.isEmpty == false
                    }
                    if (grabDelay > 0) wait(grabDelay)
                    pickup(0.menu)
                    action { mc.player?.closeContainer() }

                    onFinished { result ->
                        if (result != ContainerTaskResult.Success &&
                            result != ContainerTaskResult.Busy &&
                            ContainerUtils.containerId != 0
                        ) {
                            mc.player?.closeContainer()
                        }

                        when (result) {
                            ContainerTaskResult.Success,
                            ContainerTaskResult.Cancelled -> Unit

                            ContainerTaskResult.Busy -> modMessage("&cCouldn't get a potion: another container action is active.")
                            is ContainerTaskResult.Failure -> modMessage("&cCouldn't get a potion: ${result.message}.")
                        }

                        task = null
                    }
                }

                task = newTask
                newTask.run()
            }
        }

        on<ChatEvent.Packet> {
            if (unformatted != "You need the Cookie Buff active to use this feature!") return@on
            modMessage("No cookie active, canceling grab potion action.")
            reset()
        }
    }

    override fun onDisable() {
        reset()
    }

    private fun reset() {
        task?.cancel()
        task = null
    }

    private fun ItemStack.dungeonPotionTier(): PotionTier? {
        val attributes = extraAttributes ?: return null
        if (attributes.getString("id").orElse(null) != "POTION") return null
        if (!attributes.getString("potion_name").orElse(null).equals("Dungeon", ignoreCase = true)) return null

        val level = attributes.getInt("potion_level").orElse(null) ?: return null
        return PotionTier.entries.firstOrNull { it.level == level }
    }

    private enum class PotionTier(val level: Int) {
        I(1), II(2), III(3), IV(4), V(5), VI(6), VII(7)
    }
}
