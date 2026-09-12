package quoi.api.customtriggers

import quoi.api.customtriggers.actions.TriggerAction
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import quoi.config.ConfigMap
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Loads and saves grouped [TriggerRule]s.
 * Unsupported entries are preserved as JSON when saving valid rules.
 */
class TriggerStore(private val file: File, private val gson: Gson, private val warn: (String, Exception?) -> Unit) {
    private val groups = linkedMapOf<String, MutableList<TriggerRule>>()
    private val unsupported = linkedMapOf<String, MutableList<JsonElement>>()
    var saveError: String? = null
        private set

    val triggers = ConfigMap(groups, ::save, ::load)

    init { load() }

    fun renameGroup(old: String, new: String) {
        groups[new] = groups.remove(old) ?: return
        unsupported.remove(old)?.let { unsupported[new] = it }
        save()
    }

    fun unsupportedCount(group: String) = unsupported[group]?.size ?: 0

    private fun load() {
        groups.clear()
        unsupported.clear()
        if (!file.exists()) return
        try {
            val root = file.reader().use { JsonParser.parseReader(it).asJsonObject }
            val ids = mutableSetOf<String>()
            root.entrySet().forEach { (group, entries) ->
                require(entries.isJsonArray) { "Group '$group' is not a list." }
                val loaded = groups.getOrPut(group) { mutableListOf() }
                entries.asJsonArray.forEach { entry ->
                    try {
                        val json = entry.asJsonObject
                        require(json["name"]?.isJsonPrimitive == true && json["id"]?.isJsonPrimitive == true)
                        require(json["trigger"]?.isJsonObject == true) { "Rule requires a trigger." }
                        require(json["conditions"]?.isJsonArray == true && json["actions"]?.isJsonArray == true)
                        val trigger = gson.fromJson(entry, TriggerRule::class.java)
                        require(trigger.id.isNotBlank() && ids.add(trigger.id)) { "Missing or duplicate trigger ID." }
                        // Gson can create null entries despite Kotlin's non-null types.
                        require((trigger.conditions as List<*>).none { it == null })
                        require((trigger.actions as List<*>).all { it is TriggerAction })
                        // Loading happens before Minecraft initializes its window. Display strings may
                        // resolve keyboard names through GLFW, so only validate configuration here.
                        require((trigger.trigger as Any?) != null)
                        trigger.trigger.validationError()
                        trigger.conditions.forEach { it.validationError() }
                        trigger.actions.forEach { it.validationError() }
                        loaded.add(trigger)
                    } catch (error: Exception) {
                        unsupported.getOrPut(group) { mutableListOf() }.add(entry.deepCopy())
                        warn("An unsupported trigger in '$group' was preserved but will not run.", error)
                    }
                }
            }
        } catch (error: Exception) {
            // Preserve the original before any future save can replace malformed JSON.
            val backup = File(file.parentFile, "${file.name}.invalid-${System.currentTimeMillis()}")
            Files.copy(file.toPath(), backup.toPath())
            groups.clear()
            unsupported.clear()
            warn("Could not read custom triggers. Original saved as ${backup.name}.", error)
        }
    }

    private fun save() {
        try {
            unsupported.keys.retainAll(groups.keys)
            val root = JsonObject()
            groups.forEach { (name, entries) ->
                val array = JsonArray()
                entries.forEach { array.add(gson.toJsonTree(it)) }
                unsupported[name]?.forEach { array.add(it.deepCopy()) }
                root.add(name, array)
            }
            file.parentFile.mkdirs()
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writer().use { gson.toJson(root, it) }
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            saveError = null
        } catch (error: Exception) {
            saveError = "Could not save custom triggers: ${error.message}"
            warn(saveError!!, error)
        }
    }
}
