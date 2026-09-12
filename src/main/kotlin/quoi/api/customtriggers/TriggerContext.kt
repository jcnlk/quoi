package quoi.api.customtriggers

/**
 * Event values and placeholders shared by the conditions and actions of one rule.
 * Each matching rule and suspended sequence receives a [snapshot].
 */
sealed class TriggerContext(val kind: Kind) {
    enum class Kind { TICK, CHAT, SOUND, KEY, COMMAND }

    val data = mutableMapOf<String, String>()

    class Tick : TriggerContext(Kind.TICK)
    class Chat(val message: String, var cancelled: Boolean = false) : TriggerContext(Kind.CHAT)
    class Sound(val name: String, val volume: Float, val pitch: Float) : TriggerContext(Kind.SOUND)
    class Key(val key: Int) : TriggerContext(Kind.KEY)
    class Command(val command: String, var cancelled: Boolean = false) : TriggerContext(Kind.COMMAND)

    /**
     * Copies event values and placeholders for an independent execution context.
     */
    fun snapshot(): TriggerContext = when (this) {
        is Tick -> Tick()
        is Chat -> Chat(message, cancelled)
        is Sound -> Sound(name, volume, pitch)
        is Key -> Key(key)
        is Command -> Command(command, cancelled)
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