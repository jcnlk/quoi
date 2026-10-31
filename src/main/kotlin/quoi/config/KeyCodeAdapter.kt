package quoi.config

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import com.mojang.blaze3d.platform.InputConstants
import quoi.api.input.Keybinds

class KeyCodeAdapter : TypeAdapter<Int>() {
    override fun write(out: JsonWriter, key: Int) {
        if (key <= 0) {
            out.value(if (key == 0) Keybinds.KEY_NONE else key)
            return
        }
        out.value(InputConstants.Type.KEYSYM.getOrCreate(key).name)
    }

    override fun read(reader: JsonReader): Int {
        if (reader.peek() == JsonToken.STRING) return InputConstants.getKey(reader.nextString()).value
        return reader.nextInt().also { require(it < 0) { "Keyboard bindings must use named keys." } }
    }
}
