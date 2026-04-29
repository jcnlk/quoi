package quoi.module

import quoi.api.events.GuiEvent
import quoi.api.events.KeyEvent
import quoi.api.events.MouseEvent
import quoi.api.events.core.EventBus
import quoi.api.input.CatKeys
import quoi.module.impl.dungeon.*
import quoi.module.impl.dungeon.puzzlesolvers.PuzzleSolvers
import quoi.module.impl.mining.*
import quoi.module.impl.misc.*
import quoi.module.impl.misc.riftsolvers.MirrorverseSolvers
import quoi.module.impl.player.*
import quoi.module.impl.render.*
import quoi.module.settings.impl.KeybindComponent

object ModuleManager {
    val modules = mutableListOf<Module>()

    fun initialise() {
        modules += listOf(
            // DUNGEONS
            ShadowAssassinAlert,
            LeapMenu,
            ArrowAlign,
            AutoLeap,
            AutoCloseChest,
            FullBlockHitboxes,
            DungeonAbilities,
            InventoryWalk,
            CancelInteract,
            SecretTriggerBot,
            InvincibilityTimer,
            SimonSays,
            DungeonBreaker,
            NecronPlatformHighlight,
//            DungeonMap,
            TerminalAura,
            AutoInvincibility,
            BarrierBoom,
            TickTimers,
            DungeonESP,
            FuckDiorite,
            AutoGFS,
            Splits,
            Secrets,
            AutoBloodRush,
            AutoRoutesLegacy,
            SecretAura,
            PuzzleSolvers,
            AutoClear,
            DungeonMap,
            AutoRoutes,

            // MISC
            Test,
            Chat,
            AutoJoinSkyBlock,
            ChatReplacements, // todo remove/replace
            CatMode,
            AutoSell,
            AutoWardrobe,
            PetKeybinds,
            Titles,
            WardrobeKeybinds,
            AntiNick,
            AutoClicker,
            ChocolateFactory,
            Inventory,
            ItemAnimations,
            WitherCloak,
//            CustomTriggers,
            MirrorverseSolvers,
            AutoCarnival,

            // PLAYER
            AutoSprint,
            PlayerDisplay,
            Tweaks,

            // RENDER
            NameTags,
            RenderOptimiser,
            NickHider,
            ClickGui,
            PlayerESP,
            Trajectories,
            EtherwarpOverlay,

            // MINING
            CrystalHollowsMap,
            CrystalHollowsScanner,
            CommissionDisplay,
            MineshaftESP,
            GrieferTracker,
        )

        modules.forEach { module ->
            module.keybinding.let {
                module.register(KeybindComponent("Key bind", it, desc = "Toggles the module"))
            }
        }

//        EventBus.on<AreaEvent.Main> { modules.forEach { it.onToggle(it.enabled) } }
//        EventBus.on<AreaEvent.Sub> { modules.forEach { it.onToggle(it.enabled) } }

        EventBus.on<KeyEvent.Press> { invokeKeybind(key, true) }
        EventBus.on<KeyEvent.Release> { invokeKeybind(key, false) }
        EventBus.on<MouseEvent.Click> { invokeKeybind(button - 100, state) }

        EventBus.on<GuiEvent.Key.Press> { invokeKeybind(key, true) }
        EventBus.on<GuiEvent.Key.Release> { invokeKeybind(key, false) }
        EventBus.on<GuiEvent.Click> { invokeKeybind(button - 100, state) }
    }

    private fun invokeKeybind(key: Int, pressed: Boolean) {
        if (key == CatKeys.KEY_NONE) return

        modules.forEach { module ->
            module.settings.filterIsInstance<KeybindComponent>()
                .filter { it.value.key == key && it.value.isModifierDown() }
                .forEach { component ->
                    if (pressed) component.value.onPress?.invoke()
                    else component.value.onRelease?.invoke()
                }
        }
    }

    fun getModuleByName(name: String?): Module? = modules.firstOrNull { it.name.equals(name, true) }
}
