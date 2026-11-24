package quoi.module

import io.github.classgraph.ClassGraph
import quoi.QuoiMod.logger
import quoi.api.events.GuiEvent
import quoi.api.events.KeyEvent
import quoi.api.events.MouseEvent
import quoi.api.events.core.EventListener
import quoi.api.events.core.on
import quoi.api.input.Keybinds
import quoi.module.settings.impl.KeybindComponent

object ModuleManager : EventListener {
    val modules = mutableListOf<Module>()

    fun initialise() {
        val classLoader = Module::class.java.classLoader
        ClassGraph()
            .enableClassInfo()
            .acceptPackages("quoi.module.impl")
            .enableClassLoaders(classLoader)
            .scan()
            .use { scan ->
                scan.getAllSubclasses(Module::class.java.name)
                    .filter { !it.isAbstract }
                    .sortedBy { it.name }
                    .forEach { classInfo ->
                        val clazz = Class.forName(classInfo.name, true, classLoader).asSubclass(Module::class.java)
                        modules += clazz.getField("INSTANCE").get(null) as Module
                    }
            }

        modules.forEach { module ->
            module.keybinding.let {
                module.register(KeybindComponent("Key bind", it, desc = "Toggles the module"))
            }
        }

        logger.info("Loaded ${modules.size} modules.")

        on<KeyEvent.Press> { invokeKeybind(key, true) }
        on<KeyEvent.Release> { invokeKeybind(key, false) }
        on<MouseEvent.Click> { invokeKeybind(button - 100, state) }

        on<GuiEvent.Key.Press> { invokeKeybind(key, true) }
        on<GuiEvent.Key.Release> { invokeKeybind(key, false) }
        on<GuiEvent.Click> { invokeKeybind(button - 100, state) }
    }

    private fun invokeKeybind(key: Int, pressed: Boolean) {
        if (key == Keybinds.KEY_NONE) return

        modules.forEach { module ->
            module.settings.filterIsInstance<KeybindComponent>()
                .forEach { component -> component.value.handleInput(key, pressed) }
        }
    }

    fun getModuleByName(name: String?): Module? = modules.firstOrNull { it.name.equals(name, true) }
}
