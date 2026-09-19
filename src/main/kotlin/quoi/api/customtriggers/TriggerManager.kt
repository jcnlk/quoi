package quoi.api.customtriggers

import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import quoi.QuoiMod.logger
import quoi.QuoiMod.mc
import quoi.api.customtriggers.actions.TriggerAction
import quoi.api.customtriggers.conditions.TriggerCondition
import quoi.api.customtriggers.triggers.Trigger
import quoi.api.skyblock.location.Location
import quoi.api.events.*
import quoi.api.events.core.EventListener
import quoi.api.events.core.on
import quoi.config.ConfigMap
import quoi.config.ConfigSystem
import quoi.config.configPath
import java.io.File
import quoi.config.typedEntries
import quoi.module.impl.misc.CustomTriggers
import quoi.utils.ChatUtils.modMessage
import quoi.utils.StringUtils.noControlCodes

/**
 * Connects game events to [TriggerEngine] and manages saved rules and editor previews.
 */
object TriggerManager : EventListener {
    private val store = TriggerStore(File(configPath, "custom_triggers.json"), ConfigSystem.gson) { message, error ->
        logger.warn(message, error)
    }
    val triggers: ConfigMap<String, MutableList<TriggerRule>> get() = store.triggers
    val triggerEntries by lazy { typedEntries<Trigger>().sortedBy { it.first } }
    val conditionEntries by lazy { typedEntries<TriggerCondition>().sortedBy { it.first } }
    val actionEntries by lazy { typedEntries<TriggerAction>().sortedBy { it.first } }

    private val engine = TriggerEngine(
        triggers = { triggers.values.flatten() },
        isActive = { canRun },
        onError = { trigger, error ->
            logger.error("Custom trigger '${trigger.name}' failed", error)
            modMessage("&cCustom trigger '${trigger.name}' stopped: ${error.message}. Reopen the editor to retry.")
        }
    )
    private var previewTrigger: TriggerRule? = null
    private val previewEngine = TriggerEngine(
        triggers = { listOfNotNull(previewTrigger) },
        isActive = { editing && canRunInWorld },
        onError = { _, error ->
            logger.error("Custom trigger preview failed", error)
            modMessage("&cTrigger preview failed: ${error.message}")
        }
    )
    private var initialized = false
    @Volatile private var generation = 0L
    var editing = false
        set(value) {
            field = value
            reset()
        }

    private val canRunInWorld get() = CustomTriggers.enabled && Location.inSkyblock && mc.player != null && mc.level != null
    private val canRun get() = canRunInWorld && !editing

    fun init() {
        if (initialized) return
        initialized = true
        if (triggers.isEmpty()) triggers["General"] = mutableListOf()

        on<TickEvent.End> {
            if (editing) previewEngine.tick()
            else if (!canRun) reset()
            else engine.tick()
        }
        on<TickEvent.Server> {
            // Server ticks originate on the network thread.
            val level = mc.level
            val started = generation
            mc.execute {
                if (mc.level === level && generation == started) {
                    if (editing) previewEngine.tick(server = true)
                    else if (canRun) engine.tick(server = true)
                }
            }
        }
        on<ChatEvent.Receive> {
            if (!canRun) return@on
            val context = TriggerContext.Chat(message.noControlCodes)
            engine.handle(context)
            if (context.cancelled) cancel()
        }
        on<ChatEvent.Sent> {
            if (!canRun || !isCommand) return@on
            val context = TriggerContext.Command(message.removePrefix("/"))
            engine.handle(context)
            if (context.cancelled) cancel()
        }
        on<KeyEvent.Press> {
            if (canRun && mc.gui.screen() == null) engine.handle(TriggerContext.Key(key))
        }
        on<MouseEvent.Click> {
            if (canRun && mc.gui.screen() == null && state) engine.handle(TriggerContext.Key(button - 100))
        }
        on<PacketEvent.ReceivedPost> {
            if (!canRun) return@on
            val context = when (val sound = packet) {
                is ClientboundSoundPacket -> TriggerContext.Sound(sound.sound.registeredName, sound.volume, sound.pitch)
                is ClientboundSoundEntityPacket -> TriggerContext.Sound(sound.sound.registeredName, sound.volume, sound.pitch)
                else -> return@on
            }
            engine.handle(context)
        }
        on<WorldEvent.Change> { reset() }
        on<ServerEvent.Disconnect> { reset() }
    }

    fun reset() {
        generation++
        engine.reset()
        previewEngine.reset()
        previewTrigger = null
    }

    /**
     * Tests the actions of a rule while the editor is open.
     * Event-editing actions require a real event and cannot be previewed.
     *
     * @return an error message if the preview cannot start, otherwise `null`
     */
    fun test(trigger: TriggerRule): String? {
        if (!CustomTriggers.enabled) return "Enable Custom Triggers before testing actions."
        if (!editing || mc.player == null || mc.level == null) return "Open the editor in a world to test actions."
        if (!Location.inSkyblock) return "Custom Triggers only run in SkyBlock."
        trigger.validationError()?.let { return it }
        if (trigger.actions.any { it.requiredEvent != null }) return "Hide/replace actions need a real incoming event and cannot be previewed."
        val snapshot = ConfigSystem.gson.fromJson(ConfigSystem.gson.toJson(trigger), TriggerRule::class.java)
            .copy(enabled = true)
        reset()
        previewTrigger = snapshot
        previewEngine.preview(snapshot)
        return null
    }

    fun addTrigger(group: String, trigger: TriggerRule) {
        triggers.getOrPut(group) { mutableListOf() }.add(trigger)
        triggers.save()
    }

    val saveError get() = store.saveError

    fun unsupportedCount(group: String) = store.unsupportedCount(group)

    fun addGroup(): String {
        val name = generateSequence(1) { it + 1 }.map { "Group $it" }.first { it !in triggers }
        triggers[name] = mutableListOf()
        return name
    }

    fun deleteGroup(name: String) {
        reset()
        triggers.remove(name)
        triggers.save()
    }

    fun renameGroup(old: String, name: String): Boolean {
        val new = name.trim()
        if (new.isEmpty() || new.length > 40 || new in triggers) return false
        if (old !in triggers) return false
        store.renameGroup(old, new)
        return true
    }
}
