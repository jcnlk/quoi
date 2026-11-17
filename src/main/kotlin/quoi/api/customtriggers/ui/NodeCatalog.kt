package quoi.api.customtriggers.ui

import quoi.api.customtriggers.TriggerContext

/**
 * Node names, descriptions and placeholders keyed by type name.
 */
object NodeCatalog {
    class Info(val name: String, val description: String, val placeholders: List<String> = emptyList())

    private val entries = mapOf(
        // triggers
        "chat_received" to Info("Chat message", "Starts when a chat message matches your text.", listOf("%message%")),
        "command_sent" to Info("Command sent", "Starts when you send a matching command.", listOf("%command%", "%args%")),
        "key_pressed" to Info("Key pressed", "Starts when you press a key or mouse button in game."),
        "sound_played" to Info("Sound played", "Starts when the server plays a matching sound."),
        "area_changed" to Info("Area changed", "Starts when you enter an island or sub-area.", listOf("%island%", "%subarea%")),
        "party_message" to Info("Party message", "Starts when someone writes a matching party chat message.", listOf("%sender%", "%message%")),
        "container_opened" to Info("Menu opened", "Starts when a chest or menu with a matching title opens.", listOf("%title%")),
        "dungeon_entered" to Info("Dungeon entered", "Starts when you join a dungeon run.", listOf("%floor%")),
        "dungeon_started" to Info("Dungeon started", "Starts when the dungeon run begins.", listOf("%floor%")),
        "dungeon_phase_completed" to Info("Boss phase done", "Starts when a Floor 7 boss phase is completed.", listOf("%phase%")),
        "dungeon_stage_completed" to Info("Stage done", "Starts when a dungeon stage is completed.", listOf("%stage%")),
        // conditions
        "location" to Info("Location", "Passes when you are on an island or in a sub-area."),
        "player_position" to Info("Position", "Passes when you stand inside a box of coordinates."),
        "health" to Info("Health", "Passes when your health compares to a percentage."),
        "party" to Info("Party", "Passes when your party matches a state or member."),
        "holding_item" to Info("Held item", "Passes when the item in your hand matches."),
        "dungeon_floor" to Info("Dungeon floor", "Passes on a specific dungeon floor."),
        "dungeon_class" to Info("Dungeon class", "Passes when you play a specific class."),
        "dungeon_phase" to Info("Boss phase", "Passes during a Floor 7 boss phase."),
        "dungeon_stage" to Info("Dungeon stage", "Passes during a dungeon stage."),
        "dungeon_state" to Info("Dungeon state", "Passes in dungeon, clear, boss, terminal or when dead."),
        // actions
        "send_message" to Info("Send message", "Shows a message to you or sends it to chat."),
        "command" to Info("Run command", "Runs a client or server command."),
        "notification" to Info("Show title", "Shows a title and subtitle on screen."),
        "play_sound" to Info("Play sound", "Plays a sound only you can hear."),
        "delay" to Info("Wait", "Pauses before the next action."),
        "hide_message" to Info("Hide message", "Hides the chat message that started this trigger."),
        "replace_message" to Info("Replace message", "Replaces the chat message that started this trigger."),
        "leap" to Info("Leap", "Leaps to a teammate by name or class."),
        "swap_item" to Info("Swap item", "Selects a hotbar item by SkyBlock ID, name, lore or slot (1-9)."),
        "use_item" to Info("Use item", "Uses the held item's right-click ability."),
        "module" to Info("Toggle module", "Enables, disables or toggles a module."),
        "equip" to Info("Equip item", "Equips an item from your inventory."),
        "pet" to Info("Equip pet", "Summons a pet by name."),
        "loadout" to Info("Equip loadout", "Switches to a SkyBlock loadout slot."),
        "wardrobe" to Info("Equip wardrobe", "Switches to a wardrobe slot."),
    )

    private val eventNames = mapOf(
        TriggerContext.Kind.CHAT to "Chat message",
        TriggerContext.Kind.COMMAND to "Command sent",
        TriggerContext.Kind.KEY to "Key pressed",
        TriggerContext.Kind.SOUND to "Sound played",
        TriggerContext.Kind.AREA to "Area changed",
        TriggerContext.Kind.DUNGEON to "Dungeon",
        TriggerContext.Kind.PARTY_MESSAGE to "Party message",
        TriggerContext.Kind.CONTAINER to "Menu opened",
    )

    fun info(type: String) = entries[type] ?: Info(humanize(type), "")

    fun eventNameFor(kind: TriggerContext.Kind?) = eventNames[kind] ?: "matching"

    /** Matches name prefixes first, then names, then descriptions. */
    fun search(types: List<String>, query: String): List<String> {
        val q = query.trim()
        if (q.isEmpty()) return types.sortedBy { info(it).name }
        return types.mapNotNull { type ->
            val info = info(type)
            val rank = when {
                info.name.startsWith(q, true) -> 0
                info.name.contains(q, true) -> 1
                info.description.contains(q, true) -> 2
                else -> return@mapNotNull null
            }
            rank to type
        }.sortedWith(compareBy({ it.first }, { info(it.second).name })).map { it.second }
    }

    private fun humanize(type: String) = type.split('_').joinToString(" ") { it.replaceFirstChar(Char::titlecase) }
}
