package quoi.api.customtriggers

/**
 * Event values and placeholders shared by the conditions and actions of one rule.
 * Each matching rule and suspended sequence receives a [snapshot].
 */
sealed class TriggerContext(val kind: Kind) {
    enum class Kind { PREVIEW, CHAT, SOUND, KEY, COMMAND, AREA, DUNGEON, PARTY_MESSAGE, CONTAINER }

    val data = mutableMapOf<String, String>()

    class Preview : TriggerContext(Kind.PREVIEW)
    class Chat(val message: String, var cancelled: Boolean = false) : TriggerContext(Kind.CHAT)
    class Sound(val name: String, val volume: Float, val pitch: Float) : TriggerContext(Kind.SOUND)
    class Key(val key: Int) : TriggerContext(Kind.KEY)
    class Command(val command: String, var cancelled: Boolean = false) : TriggerContext(Kind.COMMAND)
    class Area(val island: quoi.api.skyblock.location.Island?, val subarea: String?) : TriggerContext(Kind.AREA)
    class Dungeon(
        val event: Event,
        val floor: quoi.api.skyblock.dungeon.enums.Floor? = null,
        val phase: quoi.api.skyblock.dungeon.enums.Phase? = null,
        val stage: quoi.api.skyblock.dungeon.enums.Stage? = null
    ) : TriggerContext(Kind.DUNGEON) {
        enum class Event { ENTER, START, PHASE_COMPLETE, STAGE_COMPLETE }
    }
    class PartyMessage(val sender: String, val message: String) : TriggerContext(Kind.PARTY_MESSAGE)
    class Container(val title: String) : TriggerContext(Kind.CONTAINER)

    /**
     * Copies event values and placeholders for an independent execution context.
     */
    fun snapshot(): TriggerContext = when (this) {
        is Preview -> Preview()
        is Chat -> Chat(message, cancelled)
        is Sound -> Sound(name, volume, pitch)
        is Key -> Key(key)
        is Command -> Command(command, cancelled)
        is Area -> Area(island, subarea)
        is Dungeon -> Dungeon(event, floor, phase, stage)
        is PartyMessage -> PartyMessage(sender, message)
        is Container -> Container(title)
    }.also { it.data.putAll(data) }

    /**
     * Replaces known placeholders in [template] once.
     * Unknown placeholders and placeholders inside captured values are left unchanged.
     */
    fun expand(template: String): String = PLACEHOLDER.replace(template) { data[it.value] ?: it.value }

    private companion object {
        val PLACEHOLDER = Regex("%[A-Za-z0-9_]+%")
    }
}