package quoi.utils.skyblock.player

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import quoi.annotations.Init
import quoi.annotations.Internal
import quoi.api.commands.QuoiCommand
import quoi.api.events.ChatEvent
import quoi.api.events.GameEvent
import quoi.api.events.PacketEvent
import quoi.api.events.PetEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.EventListener
import quoi.api.events.core.Priority
import quoi.api.events.core.on
import quoi.api.skyblock.Pet
import quoi.api.skyblock.PetRarity
import quoi.api.skyblock.location.Location
import quoi.module.impl.render.clickgui.impl.Data
import quoi.utils.ChatUtils.modMessage
import quoi.utils.Shortcuts
import quoi.utils.skyblock.item.ItemUtils.extraAttributes
import quoi.utils.skyblock.item.ItemUtils.loreString
import java.util.Optional

@Init
@OptIn(Internal::class)
object PetUtils : EventListener, Shortcuts {
    private val summonPattern = Regex("^You (summoned|despawned) your (.+?)(?: ✦)?!$")
    private val autopetPattern = Regex("^Autopet equipped your \\[Lvl (\\d+)] (.+?)! VIEW RULE$")
    private val levelUpPattern = Regex("^Your (.+?) leveled up to level (\\d+)!$")
    private val petMenuPattern = Regex("""^(?:\(\d+/\d+\) )?Pets$""")
    private val petItemNamePattern = Regex("""^(?:⭐\s*)?\[Lvl\s+(\d+)]\s+(.+?)(?:\s+✦)?$""")

    private val knownPets = mutableMapOf<String, Pet>()
    private val menuPets = mutableMapOf<Int, Pet>()
    private var trackedPet: Pet? = null
    private var clickedPet: Pet? = null
    private var petMenuContainerId: Int? = null

    val currentPet: Pet?
        get() = trackedPet

    init {
        QuoiCommand.devCommand.sub("pet") {
            val pet = currentPet ?: return@sub modMessage("&cNo pet is currently tracked.")
            modMessage(
                listOf(
                    "&eTracked pet",
                    "&7Name: &f${pet.name}",
                    "&7Level: &f${pet.level ?: "unknown"}/${pet.maxLevel}",
                    "&7Rarity: &f${pet.rarity}",
                    "&7UUID: &f${pet.uuid ?: "unknown"}",
                    "&7Held item: &f${pet.heldItem ?: "none"}",
                    "&7Cached pets: &f${knownPets.size}",
                ).joinToString("\n"),
                prefix = "",
            )
        }.description("Shows the currently tracked pet data.")

        on<GameEvent.Load> {
            trackedPet = decodePet(Data.currentPet)
            persistPet(trackedPet)
        }

        on<ChatEvent.Packet> {
            if (!Location.inSkyblock) return@on

            summonPattern.matchEntire(unformatted)?.destructured?.let { (action, name) ->
                val pet = if (action == "summoned") {
                    val rarity = text.petRarity(name)
                    clickedPet
                        ?.takeIf { it.matches(name) }
                        ?.merge(Pet(Pet.cleanName(name), rarity = rarity))
                        ?: resolvePet(name, rarity = rarity)
                } else {
                    null
                }
                clickedPet = null
                changePet(pet, if (pet == null) PetEvent.Cause.DESPAWN else PetEvent.Cause.SUMMON)
                return@on
            }

            autopetPattern.matchEntire(unformatted)?.destructured?.let { (level, name) ->
                clickedPet = null
                changePet(resolvePet(name, level.toInt(), text.petRarity(name)), PetEvent.Cause.AUTOPET)
                return@on
            }

            levelUpPattern.matchEntire(unformatted)?.destructured?.let { (name, level) ->
                levelUpPet(name, level.toInt())
            }
        }

        on<PacketEvent.Received, ClientboundOpenScreenPacket>(Priority.HIGHEST) {
            menuPets.clear()
            clickedPet = null
            petMenuContainerId = packet.containerId.takeIf {
                Location.inSkyblock && petMenuPattern.matches(packet.title.string)
            }
        }

        on<PacketEvent.ReceivedPost, ClientboundContainerSetContentPacket> {
            if (packet.containerId != petMenuContainerId) return@on
            cachePetMenu(packet.containerId)
        }

        on<PacketEvent.ReceivedPost, ClientboundContainerSetSlotPacket> {
            if (packet.containerId != petMenuContainerId || !isPetSlot(packet.slot)) return@on
            cachePetSlot(packet.containerId, packet.slot)
        }

        on<PacketEvent.Sent, ServerboundContainerClickPacket>(Priority.HIGHEST) {
            val slot = packet.slotNum.toInt()
            if (packet.containerId != petMenuContainerId ||
                packet.containerInput() != ContainerInput.PICKUP ||
                packet.buttonNum.toInt() != 0 ||
                !isPetSlot(slot)
            ) return@on

            clickedPet = menuPets[slot]
        }

        on<WorldEvent.Change> {
            knownPets.clear()
            menuPets.clear()
            clickedPet = null
            petMenuContainerId = null
        }
    }

    val ItemStack.pet: Pet?
        get() {
            val attributes = extraAttributes ?: return null
            val rawPetInfo = attributes.getString("petInfo").orElse(null) ?: return null
            val petInfo = runCatching { Json.parseToJsonElement(rawPetInfo).jsonObject }.getOrNull() ?: return null
            val displayMatch = petItemNamePattern.matchEntire(hoverName.string.trim())
            val petType = petInfo.string("type")
            val name = displayMatch?.groupValues?.get(2)?.trim()
                ?: petType?.replace('_', ' ')
                    ?.lowercase()
                    ?.split(' ')
                    ?.joinToString(" ") { word -> word.replaceFirstChar(Char::titlecase) }
                ?: return null

            return Pet(
                name = name,
                level = displayMatch?.groupValues?.get(1)?.toIntOrNull(),
                rarity = PetRarity.fromName(petInfo.string("tier")),
                uuid = attributes.getString("uuid").orElse(null) ?: petInfo.string("uuid"),
                heldItem = petInfo.string("heldItem")?.takeUnless { it.isBlank() || it == "null" || it == "NONE" },
            )
        }

    private fun cachePetMenu(containerId: Int) {
        val menu = player.containerMenu.takeIf { it.containerId == containerId } ?: return
        menuPets.clear()
        var equippedPet: Pet? = null
        menu.slots.forEachIndexed { index, slot ->
            if (!isPetSlot(index)) return@forEachIndexed
            val stack = slot.item
            val pet = cacheMenuPet(index, stack) ?: return@forEachIndexed
            if (stack.loreString?.contains("Click to despawn!") == true) equippedPet = pet
        }

        equippedPet?.let(::reconcileMenuPet)
    }

    private fun cachePetSlot(containerId: Int, slot: Int) {
        val menu = player.containerMenu.takeIf { it.containerId == containerId } ?: return
        val stack = menu.slots.getOrNull(slot)?.item ?: return
        val pet = cacheMenuPet(slot, stack) ?: return
        if (stack.loreString?.contains("Click to despawn!") == true) reconcileMenuPet(pet)
    }

    private fun cacheMenuPet(slot: Int, stack: ItemStack): Pet? {
        val pet = stack.pet
        if (pet == null) {
            menuPets.remove(slot)
            return null
        }

        menuPets[slot] = pet
        cachePet(pet)
        return pet
    }

    private fun reconcileMenuPet(equipped: Pet) {
        if (equipped.uuid == null) return
        if (clickedPet?.uuid == equipped.uuid) return

        val current = trackedPet
        when {
            current == null -> changePet(equipped, PetEvent.Cause.MENU)
            current.uuid == null && current.matches(equipped.name) -> updatePet(current.merge(equipped))
            current.uuid != equipped.uuid -> changePet(equipped, PetEvent.Cause.MENU)
            else -> updatePet(current.merge(equipped))
        }
    }

    private fun isPetSlot(slot: Int): Boolean = slot in 10..43 && slot % 9 in 1..7

    private fun resolvePet(
        name: String,
        level: Int? = null,
        rarity: PetRarity = PetRarity.UNKNOWN,
    ): Pet {
        val base = knownPets.values.singleOrNull { it.matches(name, rarity) }

        return Pet(
            name = base?.name ?: Pet.cleanName(name),
            level = level ?: base?.level,
            rarity = rarity.takeUnless { it == PetRarity.UNKNOWN } ?: base?.rarity ?: PetRarity.UNKNOWN,
            uuid = base?.uuid,
            heldItem = base?.heldItem,
        )
    }

    private fun changePet(pet: Pet?, cause: PetEvent.Cause) {
        trackedPet = pet
        pet?.let(::cachePet)
        persistPet(pet)
        PetEvent.Change(pet, cause).post()
    }

    private fun updatePet(pet: Pet) {
        if (trackedPet == pet) return

        trackedPet = pet
        cachePet(pet)
        persistPet(pet)
    }

    private fun levelUpPet(name: String, level: Int) {
        val current = trackedPet?.takeIf { it.matches(name) } ?: return
        if (current.level == level) return

        val pet = current.copy(level = level)
        updatePet(pet)
        PetEvent.LevelUp(pet).post()
    }

    private fun cachePet(pet: Pet) {
        val uuid = pet.uuid ?: return
        knownPets[uuid] = knownPets[uuid]?.merge(pet) ?: pet
    }

    private fun Pet.merge(other: Pet): Pet = Pet(
        name = other.name,
        level = other.level ?: level,
        rarity = other.rarity.takeUnless { it == PetRarity.UNKNOWN } ?: rarity,
        uuid = other.uuid ?: uuid,
        heldItem = other.heldItem ?: heldItem,
    )

    private fun Component.petRarity(name: String): PetRarity {
        val normalizedName = Pet.normalizeName(name)
        var bestMatchLength = 0
        var matchedRarity = PetRarity.UNKNOWN

        visit<Unit>({ style, content ->
            val normalizedContent = Pet.normalizeName(content)
            val rarity = style.petRarity
            if (rarity != PetRarity.UNKNOWN &&
                normalizedContent.isNotEmpty() &&
                normalizedName.contains(normalizedContent) &&
                normalizedContent.length > bestMatchLength
            ) {
                bestMatchLength = normalizedContent.length
                matchedRarity = rarity
            }
            Optional.empty()
        }, Style.EMPTY)

        return matchedRarity
    }

    private val Style.petRarity: PetRarity
        get() = when (color?.value) {
            TextColor.fromLegacyFormat(ChatFormatting.WHITE)?.getValue() -> PetRarity.COMMON
            TextColor.fromLegacyFormat(ChatFormatting.GREEN)?.getValue() -> PetRarity.UNCOMMON
            TextColor.fromLegacyFormat(ChatFormatting.BLUE)?.getValue() -> PetRarity.RARE
            TextColor.fromLegacyFormat(ChatFormatting.DARK_PURPLE)?.getValue() -> PetRarity.EPIC
            TextColor.fromLegacyFormat(ChatFormatting.GOLD)?.getValue() -> PetRarity.LEGENDARY
            TextColor.fromLegacyFormat(ChatFormatting.LIGHT_PURPLE)?.getValue() -> PetRarity.MYTHIC
            else -> PetRarity.UNKNOWN
        }

    private fun persistPet(pet: Pet?) {
        Data.currentPet = pet?.let(::encodePet).orEmpty()
    }

    private fun encodePet(pet: Pet): String = buildJsonObject {
        put("name", pet.name)
        pet.level?.let { put("level", it) }
        put("rarity", pet.rarity.name)
        pet.uuid?.let { put("uuid", it) }
        pet.heldItem?.let { put("heldItem", it) }
    }.toString()

    private fun decodePet(value: String): Pet? {
        if (value.isBlank()) return null
        if (!value.trimStart().startsWith('{')) return Pet(Pet.cleanName(value))

        return runCatching {
            val json = Json.parseToJsonElement(value).jsonObject
            val name = json.string("name")?.takeIf(String::isNotBlank) ?: return@runCatching null
            Pet(
                name = Pet.cleanName(name),
                level = json["level"]?.jsonPrimitive?.intOrNull,
                rarity = PetRarity.fromName(json.string("rarity")),
                uuid = json.string("uuid"),
                heldItem = json.string("heldItem"),
            )
        }.getOrNull()
    }

    private fun kotlinx.serialization.json.JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull
}
