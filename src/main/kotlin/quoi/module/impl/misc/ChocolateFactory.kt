package quoi.module.impl.misc

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.phys.Vec3
import quoi.api.colour.Colour
import quoi.api.events.ChatEvent
import quoi.api.events.PacketEvent
import quoi.api.events.RenderEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.skyblock.Island
import quoi.api.skyblock.Location
import quoi.module.Module
import quoi.utils.ChatUtils.literal
import quoi.utils.EntityUtils.getEntities
import quoi.utils.StringUtils.containsOneOf
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.aabb
import quoi.utils.render.drawText
import quoi.utils.render.drawWireFrameBox
import quoi.utils.skyblock.ItemUtils.lore
import quoi.utils.skyblock.ItemUtils.texture
import kotlin.math.max

/**
 * modified OdinLegacy (BSD 3-Clause)
 * copyright (c) 2023-2026 odtheking
 * original: https://github.com/odtheking/OdinLegacy/blob/main/odinclient/src/main/kotlin/me/odinclient/features/impl/skyblock/ChocolateFactory.kt
 */
object ChocolateFactory : Module(
    "Chocolate Factory",
    desc = "Automates the Chocolate Factory."
) {
    private val clickFactory by switch("Click Factory", desc = "Click the cookie in the Chocolate Factory menu.")
    private val autoUpgrade by switch("Auto Upgrade", desc = "Automatically upgrade the worker.")
    private val delay by slider("Delay", 150, 50, 1500, 5, unit = "ms", desc = "Delay between actions.")
    private val upgradeDelay by slider("Upgrade delay", 500, 300, 2000, 100, unit = "ms", desc = "Delay between upgrades.")
    private val claimStrays by switch("Claim Strays", desc = "Claim stray rabbits in the Chocolate Factory menu.")
    private val cancelSound by switch("Cancel Sound", desc = "Cancels the eating sound in the Chocolate Factory.")
    private val eggEsp by switch("Egg ESP", desc = "Shows the location of the egg.")

    private var chocolate = 0L
    private var bestWorker = 28
    private var bestCost = 0
    private var found = false
    private var lastActionAt = 0L
    private var lastUpgradeAt = 0L
    private var lastEggScanAt = 0L
    private val currentDetectedEggs = mutableListOf<Egg>()

    private val possibleLocations = arrayOf(
        Island.SpiderDen,
        Island.CrimsonIsle,
        Island.TheEnd,
        Island.GoldMine,
        Island.DeepCaverns,
        Island.DwarvenMines,
        Island.CrystalHollows,
        Island.FarmingIsland,
        Island.ThePark,
        Island.DungeonHub,
        Island.Hub,
        Island.BackwaterBayou
    )
    private val eggMessage = Regex(".*(A|found|collected).+Chocolate (Breakfast|Lunch|Dinner|Brunch|Déjeuner|Supper).*")

    private const val dinnerEggTexture =
        "ewogICJ0aW1lc3RhbXAiIDogMTcxMTQ2MjY0OTcwMSwKICAicHJvZmlsZUlkIiA6ICI3NGEwMzQxNWY1OTI0ZTA4YjMyMGM2MmU1NGE3ZjJhYiIsCiAgInByb2ZpbGVOYW1lIiA6ICJNZXp6aXIiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTVlMzYxNjU4MTlmZDI4NTBmOTg1NTJlZGNkNzYzZmY5ODYzMTMxMTkyODNjMTI2YWNlMGM0Y2M0OTVlNzZhOCIKICAgIH0KICB9Cn0"
    private const val lunchEggTexture =
        "ewogICJ0aW1lc3RhbXAiIDogMTcxMTQ2MjU2ODExMiwKICAicHJvZmlsZUlkIiA6ICI3NzUwYzFhNTM5M2Q0ZWQ0Yjc2NmQ4ZGUwOWY4MjU0NiIsCiAgInByb2ZpbGVOYW1lIiA6ICJSZWVkcmVsIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzdhZTZkMmQzMWQ4MTY3YmNhZjk1MjkzYjY4YTRhY2Q4NzJkNjZlNzUxZGI1YTM0ZjJjYmM2NzY2YTAzNTZkMGEiCiAgICB9CiAgfQp9"
    private const val breakfastEggTexture =
        "ewogICJ0aW1lc3RhbXAiIDogMTcxMTQ2MjY3MzE0OSwKICAicHJvZmlsZUlkIiA6ICJiN2I4ZTlhZjEwZGE0NjFmOTY2YTQxM2RmOWJiM2U4OCIsCiAgInByb2ZpbGVOYW1lIiA6ICJBbmFiYW5hbmFZZzciLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTQ5MzMzZDg1YjhhMzE1ZDAzMzZlYjJkZjM3ZDhhNzE0Y2EyNGM1MWI4YzYwNzRmMWI1YjkyN2RlYjUxNmMyNCIKICAgIH0KICB9Cn0"

    init {
        on<WorldEvent.Change> {
            currentDetectedEggs.clear()
        }

        on<TickEvent.End> {
            val now = System.currentTimeMillis()

            if (now - lastActionAt >= delay) {
                tickFactoryActions()
                lastActionAt = now
            }

            if (now - lastUpgradeAt >= upgradeDelay) {
                tickUpgrades()
                lastUpgradeAt = now
            }

            if (now - lastEggScanAt >= 3000L) {
                if (eggEsp && Location.currentArea in possibleLocations && currentDetectedEggs.size < 6) {
                    scanForEggs()
                }
                lastEggScanAt = now
            }
        }

        on<ChatEvent.Packet> {
            val match = eggMessage.find(message.noControlCodes) ?: return@on
            val action = match.groupValues.getOrNull(1)
            if (!action.equals("found", true) && !action.equals("collected", true)) return@on

            currentDetectedEggs
                .filterNot(Egg::isFound)
                .minByOrNull { egg -> egg.entity.distanceTo(player) }
                ?.isFound = true
        }

        on<PacketEvent.Received, ClientboundSoundPacket> {
            if (!cancelSound || !isInChocolateFactory()) return@on
            if (packet.sound.registeredName != "minecraft:entity.generic.eat") return@on
            cancel()
        }

        on<RenderEvent.World> {
            if (!eggEsp) return@on

            currentDetectedEggs.forEach { egg ->
                if (egg.isFound) return@forEach

                val renderPos = Vec3(egg.entity.x - 0.5, egg.entity.y + 1.47, egg.entity.z - 0.5)
                val distance = renderPos.distanceTo(player.position())
                val labelPos = renderPos.add(0.5, 1.7 + distance / 30.0, 0.5)
                val textScale = max(1.2f, (distance / 8.0).toFloat())

                ctx.drawWireFrameBox(renderPos.aabb, egg.colour, depth = false)
                ctx.drawText(
                    literal("${egg.renderName} &r&f(&3${distance.toInt()}m&f)"),
                    labelPos,
                    colour = egg.colour,
                    scale = textScale,
                    depth = false
                )
            }
        }
    }

    private fun tickFactoryActions() {
        val screen = mc.screen as? AbstractContainerScreen<*> ?: return
        if (screen.title.string != "Chocolate Factory") return

        if (clickFactory) {
            mc.gameMode?.handleInventoryMouseClick(screen.menu.containerId, 13, 1, ClickType.PICKUP, player)
        }

        if (!claimStrays) return

        val found = screen.menu.slots.firstOrNull { slot ->
            slot.item.hoverName.string.containsOneOf("CLICK ME!", "Golden Rabbit")
        } ?: return

        mc.gameMode?.handleInventoryMouseClick(screen.menu.containerId, found.index, 0, ClickType.PICKUP, player)
    }

    private fun tickUpgrades() {
        val screen = mc.screen as? AbstractContainerScreen<*> ?: return
        if (screen.title.string != "Chocolate Factory") return

        chocolate = screen.menu.getSlot(13)?.item?.hoverName?.string
            ?.replace(Regex("\\D"), "")
            ?.toLongOrNull()
            ?: 0L

        findWorker(screen.menu)
        if (!found) return
        if (chocolate > bestCost && autoUpgrade) {
            mc.gameMode?.handleInventoryMouseClick(screen.menu.containerId, bestWorker, 2, ClickType.CLONE, player)
        }
    }

    private fun findWorker(menu: net.minecraft.world.inventory.AbstractContainerMenu) {
        val workers = mutableListOf<List<String>>()
        repeat(7) { index ->
            workers.add(menu.slots.getOrNull(index + 28)?.item?.lore ?: return)
        }

        found = false
        var maxValue = 0

        repeat(7) { index ->
            val worker = workers[index]
            if (worker.any { it.contains("climbed as far") }) return@repeat

            val costIndex = worker.indexOfFirst { it.contains("Cost") }
                .takeIf { it != -1 }
                ?: return@repeat
            val cost = worker.getOrNull(costIndex + 1)
                ?.noControlCodes
                ?.replace(Regex("\\D"), "")
                ?.toIntOrNull()
                ?: return@repeat
            val value = cost / (index + 1).toFloat()

            if (value < maxValue || !found) {
                bestWorker = 28 + index
                maxValue = value.toInt()
                bestCost = cost
                found = true
            }
        }
    }

    private fun scanForEggs() {
        getEntities<ArmorStand>().forEach { entity ->
            val helmet = entity.getItemBySlot(EquipmentSlot.HEAD).takeUnless { it.isEmpty } ?: return@forEach
            val eggType = ChocolateEgg.entries.find { it.texture == helmet.texture } ?: return@forEach
            currentDetectedEggs.add(Egg(entity, eggType.type, eggType.colour))
        }
    }

    private fun isInChocolateFactory(): Boolean =
        (mc.screen as? AbstractContainerScreen<*>)?.title?.string == "Chocolate Factory"

    private data class Egg(
        val entity: ArmorStand,
        val renderName: String,
        val colour: Colour,
        var isFound: Boolean = false
    )

    private enum class ChocolateEgg(
        val texture: String,
        val type: String,
        val colour: Colour
    ) {
        Breakfast(breakfastEggTexture, "§6Breakfast Egg", Colour.MINECRAFT_GOLD),
        Lunch(lunchEggTexture, "§9Lunch Egg", Colour.MINECRAFT_BLUE),
        Dinner(dinnerEggTexture, "§aDinner Egg", Colour.MINECRAFT_GREEN),
        Brunch(breakfastEggTexture, "§6Brunch Egg", Colour.MINECRAFT_GOLD),
        Dejeuner(lunchEggTexture, "§9Déjeuner Egg", Colour.MINECRAFT_BLUE),
        Supper(dinnerEggTexture, "§aSupper Egg", Colour.MINECRAFT_GREEN);
    }
}
