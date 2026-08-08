package quoi.module.impl.misc

import quoi.api.events.core.on
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import quoi.api.colour.Colour
import quoi.api.events.ChatEvent
import quoi.api.events.EntityEvent
import quoi.api.events.PacketEvent
import quoi.api.events.RenderEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.skyblock.location.Island
import quoi.api.skyblock.location.Location
import quoi.module.Module
import quoi.utils.ChatUtils.literal
import quoi.utils.StringUtils.containsOneOf
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.romanToInt
import quoi.utils.aabb
import quoi.utils.render.drawText
import quoi.utils.render.drawWireFrameBox
import quoi.utils.skyblock.item.ItemUtils.lore
import quoi.utils.skyblock.item.ItemUtils.texture
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
    private val autoTimeTower by switch("Auto Time Tower", desc = "Automatically activate the Time Tower when it has charges and is inactive.")
    private val autoUpgrade by switch("Auto Upgrade", desc = "Automatically buy the most efficient Chocolate Factory upgrade.")
    private val delay by slider("Delay", 150, 50, 1500, 5, unit = "ms", desc = "Delay between actions.")
    private val upgradeDelay by slider("Upgrade delay", 500, 300, 2000, 100, unit = "ms", desc = "Delay between upgrades.")
    private val claimStrays by switch("Claim Strays", desc = "Claim stray rabbits in the Chocolate Factory menu.")
    private val cancelSound by switch("Cancel Sound", desc = "Cancels the eating sound in the Chocolate Factory.")
    private val eggEsp by switch("Egg ESP", desc = "Shows the location of the egg.")

    private var chocolate = 0L
    private val rabbitSlotGains = mapOf(28 to 1, 29 to 2, 30 to 3, 31 to 4, 32 to 5, 33 to 6, 34 to 7)
    private var lastActionAt = 0L
    private var lastUpgradeAt = 0L
    private val eggs = mutableListOf<Egg>()
    private val eggCandidates = mutableMapOf<Int, EggCandidate>()
    private val pendingEggs = mutableMapOf<ChocolateEgg, Int?>()
    private val chocolatePerSecondPattern = Regex("([\\d.,]+)\\s+per second")
    private val totalMultiplierPattern = Regex("Total Multiplier:\\s+([\\d.]+)x")
    private val timeTowerStatusPattern = Regex("Status:\\s+(ACTIVE|INACTIVE)")
    private val timeTowerChargesPattern = Regex("Charges:\\s*(\\d+)\\s*/\\s*(\\d+)")

    private val possibleLocations = setOf(
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
        Island.BackwaterBayou,
        Island.Galatea,
        Island.LotusAtoll,
        Island.TorrhusCanyon,
    )
    private val eggSpawnedPattern = Regex("A Chocolate (Breakfast|Lunch|Dinner|Brunch|Déjeuner|Supper) Egg has appeared!")
    private val eggFoundPattern = Regex("(?:found a|collected this) Chocolate (Breakfast|Lunch|Dinner|Brunch|Déjeuner|Supper) Egg")

    private const val DINNER_EGG_TEXTURE =
        "ewogICJ0aW1lc3RhbXAiIDogMTcxMTQ2MjY0OTcwMSwKICAicHJvZmlsZUlkIiA6ICI3NGEwMzQxNWY1OTI0ZTA4YjMyMGM2MmU1NGE3ZjJhYiIsCiAgInByb2ZpbGVOYW1lIiA6ICJNZXp6aXIiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTVlMzYxNjU4MTlmZDI4NTBmOTg1NTJlZGNkNzYzZmY5ODYzMTMxMTkyODNjMTI2YWNlMGM0Y2M0OTVlNzZhOCIKICAgIH0KICB9Cn0"
    private const val LUNCH_EGG_TEXTURE =
        "ewogICJ0aW1lc3RhbXAiIDogMTcxMTQ2MjU2ODExMiwKICAicHJvZmlsZUlkIiA6ICI3NzUwYzFhNTM5M2Q0ZWQ0Yjc2NmQ4ZGUwOWY4MjU0NiIsCiAgInByb2ZpbGVOYW1lIiA6ICJSZWVkcmVsIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzdhZTZkMmQzMWQ4MTY3YmNhZjk1MjkzYjY4YTRhY2Q4NzJkNjZlNzUxZGI1YTM0ZjJjYmM2NzY2YTAzNTZkMGEiCiAgICB9CiAgfQp9"
    private const val BREAKFAST_EGG_TEXTURE =
        "ewogICJ0aW1lc3RhbXAiIDogMTcxMTQ2MjY3MzE0OSwKICAicHJvZmlsZUlkIiA6ICJiN2I4ZTlhZjEwZGE0NjFmOTY2YTQxM2RmOWJiM2U4OCIsCiAgInByb2ZpbGVOYW1lIiA6ICJBbmFiYW5hbmFZZzciLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTQ5MzMzZDg1YjhhMzE1ZDAzMzZlYjJkZjM3ZDhhNzE0Y2EyNGM1MWI4YzYwNzRmMWI1YjkyN2RlYjUxNmMyNCIKICAgIH0KICB9Cn0"

    init {
        on<WorldEvent.Change> {
            eggs.clear()
            eggCandidates.clear()
            pendingEggs.clear()
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
        }

        on<ChatEvent.Packet> {
            val spawnedMatch = eggSpawnedPattern.find(unformatted)
            if (spawnedMatch != null) {
                val eggType = ChocolateEgg.fromMealName(spawnedMatch.groupValues.getOrNull(1)) ?: return@on
                val previousEgg = eggs.firstOrNull { it.type == eggType }
                pendingEggs[eggType] = previousEgg?.entity?.id
                previousEgg?.let(eggs::remove)
                reconcileEggs()
                return@on
            }

            val match = eggFoundPattern.find(unformatted) ?: return@on
            val eggType = ChocolateEgg.fromMealName(match.groupValues.getOrNull(1)) ?: return@on
            val foundEgg = eggs
                .filter { !it.isClaimed && it.type.texture == eggType.texture }
                .minByOrNull { it.entity.distanceTo(player) }
                ?: return@on

            if (foundEgg.type != eggType) {
                eggs.firstOrNull { it.type == eggType }?.type = foundEgg.type
                foundEgg.type = eggType
            }
            foundEgg.isClaimed = true
        }

        on<PacketEvent.Received, ClientboundSoundPacket> {
            if (!cancelSound || !isInChocolateFactory()) return@on
            if (packet.sound.registeredName != "minecraft:entity.generic.eat") return@on
            cancel()
        }

        on<EntityEvent.ArmorStandHeadEquipmentUpdate> {
            val texture = entity.getItemBySlot(EquipmentSlot.HEAD).texture ?: return@on
            if (texture !in ChocolateEgg.textures) return@on

            eggCandidates[entity.id] = EggCandidate(entity, texture)
            reconcileEggs()
        }

        on<RenderEvent.World> {
            if (!eggEsp || Location.currentArea !in possibleLocations) return@on

            eggs.forEach { egg ->
                if (egg.isClaimed) return@forEach

                val renderPos = Vec3(egg.entity.x - 0.5, egg.entity.y + 1.47, egg.entity.z - 0.5)
                val distance = renderPos.distanceTo(player.position())
                val labelPos = renderPos.add(0.5, 1.7 + distance / 30.0, 0.5)
                val textScale = max(1.2f, (distance / 8.0).toFloat())

                ctx.drawWireFrameBox(renderPos.aabb, egg.type.colour, depth = false)
                ctx.drawText(
                    literal("${egg.type.renderName} &r&f(&3${distance.toInt()}m&f)"),
                    labelPos,
                    scale = textScale,
                    depth = false
                )
            }
        }
    }

    private fun tickFactoryActions() {
        val screen = mc.gui.screen() as? AbstractContainerScreen<*> ?: return
        if (screen.title.string != "Chocolate Factory") return

        if (autoTimeTower && shouldActivateTimeTower(screen.menu.getSlot(39).item)) {
            gameMode.handleContainerInput(screen.menu.containerId, 39, 1, ContainerInput.PICKUP, player)
            return
        }

        if (clickFactory) {
            gameMode.handleContainerInput(screen.menu.containerId, 13, 1, ContainerInput.PICKUP, player)
        }

        if (!claimStrays) return

        val found = screen.menu.slots.firstOrNull { slot ->
            slot.item.hoverName.string.containsOneOf("CLICK ME!", "Golden Rabbit")
        } ?: return

        gameMode.handleContainerInput(screen.menu.containerId, found.index, 0, ContainerInput.PICKUP, player)
    }

    private fun tickUpgrades() {
        val screen = mc.gui.screen() as? AbstractContainerScreen<*> ?: return
        if (screen.title.string != "Chocolate Factory") return

        chocolate = screen.menu.getSlot(13).item.hoverName.string
            .replace(Regex("\\D"), "")
            .toLongOrNull()
            ?: 0L

        val bestUpgrade = findBestUpgrade(screen.menu) ?: return
        if (autoUpgrade && chocolate >= bestUpgrade.cost) {
            gameMode.handleContainerInput(screen.menu.containerId, bestUpgrade.slot, 2, ContainerInput.CLONE, player)
        }
    }

    private fun findBestUpgrade(menu: net.minecraft.world.inventory.AbstractContainerMenu): UpgradeCandidate? {
        val chocolatePerSecond = parseChocolatePerSecond(menu.getSlot(13).item) ?: return null
        val totalMultiplier = parseTotalMultiplier(menu.getSlot(45).item) ?: return null
        if (chocolatePerSecond <= 0.0 || totalMultiplier <= 0.0) return null

        val rawChocolatePerSecond = chocolatePerSecond / totalMultiplier
        val timeTowerItem = menu.getSlot(39).item
        val timeTowerLevel = parseUpgradeTier(timeTowerItem)
        val timeTowerActive = isTimeTowerActive(timeTowerItem)
        val baseMultiplier = (totalMultiplier - if (timeTowerActive) timeTowerLevel * 0.1 else 0.0).coerceAtLeast(0.0)
        if (rawChocolatePerSecond <= 0.0 || baseMultiplier <= 0.0) return null

        val averageChocolate = averageChocolatePerSecond(rawChocolatePerSecond, baseMultiplier)

        val candidates = buildList {
            rabbitSlotGains.forEach { (slot, gain) ->
                val item = menu.slots.getOrNull(slot)?.item ?: return@forEach
                val cost = parseUpgradeCost(item) ?: return@forEach
                val newAverageChocolate = averageChocolatePerSecond(
                    rawChocolatePerSecond = rawChocolatePerSecond + gain,
                    baseMultiplier = baseMultiplier
                )
                val effectiveCost = effectiveUpgradeCost(cost, averageChocolate, newAverageChocolate)
                if (effectiveCost != null) {
                    add(UpgradeCandidate(slot, cost, effectiveCost))
                }
            }

            val timeTowerCost = parseUpgradeCost(timeTowerItem)
            if (timeTowerCost != null) {
                val newAverageChocolate = averageChocolatePerSecond(
                    rawChocolatePerSecond = rawChocolatePerSecond,
                    baseMultiplier = baseMultiplier,
                    includeTimeTower = true
                )
                val effectiveCost = effectiveUpgradeCost(timeTowerCost, averageChocolate, newAverageChocolate)
                if (effectiveCost != null) {
                    add(UpgradeCandidate(39, timeTowerCost, effectiveCost))
                }
            }

            val coachRabbitItem = menu.getSlot(42)?.item
            val coachRabbitCost = parseUpgradeCost(coachRabbitItem)
            if (coachRabbitCost != null) {
                val newAverageChocolate = averageChocolatePerSecond(
                    rawChocolatePerSecond = rawChocolatePerSecond,
                    baseMultiplier = baseMultiplier + 0.01
                )
                val effectiveCost = effectiveUpgradeCost(coachRabbitCost, averageChocolate, newAverageChocolate)
                if (effectiveCost != null) {
                    add(UpgradeCandidate(42, coachRabbitCost, effectiveCost))
                }
            }
        }

        return candidates.minByOrNull(UpgradeCandidate::effectiveCost)
    }

    private fun averageChocolatePerSecond(
        rawChocolatePerSecond: Double,
        baseMultiplier: Double,
        includeTimeTower: Boolean = false
    ): Double {
        val basePerSecond = rawChocolatePerSecond * baseMultiplier
        if (!includeTimeTower) return basePerSecond

        return basePerSecond + rawChocolatePerSecond * 0.1 / 8.0
    }

    private fun effectiveUpgradeCost(cost: Long, averageChocolate: Double, newAverageChocolate: Double): Double? {
        val extraPerSecond = newAverageChocolate - averageChocolate
        if (extraPerSecond <= 0.0) return null
        return cost / extraPerSecond
    }

    private fun parseUpgradeCost(item: ItemStack?): Long? {
        val lore = item?.lore ?: return null
        val costIndex = lore.indexOfFirst { it.noControlCodes.contains("Cost") }
            .takeIf { it != -1 }
            ?: return null
        return lore.getOrNull(costIndex + 1)
            ?.noControlCodes
            ?.replace(Regex("\\D"), "")
            ?.toLongOrNull()
    }

    private fun parseChocolatePerSecond(item: ItemStack): Double? {
        return item.lore
            ?.asSequence()
            ?.map { it.noControlCodes }
            ?.firstNotNullOfOrNull { line ->
                chocolatePerSecondPattern.find(line)?.groupValues?.getOrNull(1)
                    ?.replace(",", "")
                    ?.toDoubleOrNull()
            }
    }

    private fun parseTotalMultiplier(item: ItemStack): Double? {
        return item.lore
            ?.asSequence()
            ?.map { it.noControlCodes }
            ?.firstNotNullOfOrNull { line ->
                totalMultiplierPattern.find(line)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
            }
    }

    private fun parseUpgradeTier(item: ItemStack?): Int {
        val cleanName = item?.hoverName?.string?.noControlCodes ?: return 0
        val tier = cleanName.substringAfterLast(' ', "").takeIf(String::isNotBlank) ?: return 0
        return runCatching { romanToInt(tier) }.getOrDefault(0)
    }

    private fun isTimeTowerActive(item: ItemStack?): Boolean {
        return (item?.lore
            ?.asSequence()
            ?.map { it.noControlCodes }
            ?.firstNotNullOfOrNull { line -> timeTowerStatusPattern.find(line)?.groupValues?.getOrNull(1) }) == "ACTIVE"
    }

    private fun shouldActivateTimeTower(item: ItemStack?): Boolean {
        val charges = parseTimeTowerCurrentCharges(item) ?: return false
        return charges > 0 && !isTimeTowerActive(item)
    }

    private fun parseTimeTowerCurrentCharges(item: ItemStack?): Int? {
        return item?.lore
            ?.asSequence()
            ?.map { it.noControlCodes }
            ?.firstNotNullOfOrNull { line ->
                timeTowerChargesPattern.find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()
            }
    }

    private fun reconcileEggs() {
        val assignedEntityIds = eggs.mapTo(mutableSetOf()) { it.entity.id }

        pendingEggs.keys.toList().forEach { type ->
            val replacement = eggCandidates.values.firstOrNull { candidate ->
                candidate.texture == type.texture &&
                    candidate.entity.id !in assignedEntityIds &&
                    candidate.entity.id != pendingEggs[type]
            } ?: return@forEach

            eggs.add(Egg(replacement.entity, type))
            assignedEntityIds.add(replacement.entity.id)
            pendingEggs.remove(type)?.let(eggCandidates::remove)
        }

        ChocolateEgg.entries
            .filter { type -> type !in pendingEggs && eggs.none { it.type == type } }
            .forEach { type ->
                val candidate = eggCandidates.values.firstOrNull { candidate ->
                    candidate.texture == type.texture && candidate.entity.id !in assignedEntityIds
                } ?: return@forEach

                eggs.add(Egg(candidate.entity, type))
                assignedEntityIds.add(candidate.entity.id)
            }
    }

    private fun isInChocolateFactory(): Boolean =
        (mc.gui.screen() as? AbstractContainerScreen<*>)?.title?.string == "Chocolate Factory"

    private data class Egg(
        val entity: ArmorStand,
        var type: ChocolateEgg,
        var isClaimed: Boolean = false
    )

    private data class EggCandidate(val entity: ArmorStand, val texture: String)

    private data class UpgradeCandidate(
        val slot: Int,
        val cost: Long,
        val effectiveCost: Double
    )

    private enum class ChocolateEgg(
        val texture: String,
        val mealName: String,
        val renderName: String,
        val colour: Colour
    ) {
        Breakfast(BREAKFAST_EGG_TEXTURE, "Breakfast", "§6Breakfast Egg", Colour.MINECRAFT_GOLD),
        Lunch(LUNCH_EGG_TEXTURE, "Lunch", "§9Lunch Egg", Colour.MINECRAFT_BLUE),
        Dinner(DINNER_EGG_TEXTURE, "Dinner", "§aDinner Egg", Colour.MINECRAFT_GREEN),
        Brunch(BREAKFAST_EGG_TEXTURE, "Brunch", "§6Brunch Egg", Colour.MINECRAFT_GOLD),
        Dejeuner(LUNCH_EGG_TEXTURE, "Déjeuner", "§9Déjeuner Egg", Colour.MINECRAFT_BLUE),
        Supper(DINNER_EGG_TEXTURE, "Supper", "§aSupper Egg", Colour.MINECRAFT_GREEN);

        companion object {
            val textures = entries.mapTo(mutableSetOf(), ChocolateEgg::texture)

            fun fromMealName(name: String?): ChocolateEgg? = entries.find { it.mealName == name }
        }
    }
}
