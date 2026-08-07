package quoi.module

import quoi.api.events.GuiEvent
import quoi.api.events.KeyEvent
import quoi.api.events.MouseEvent
import quoi.api.events.core.EventListener
import quoi.api.events.core.on
import quoi.api.input.CatKeys
import quoi.module.impl.dungeon.*
import quoi.module.impl.dungeon.autoclear.impl.*
import quoi.module.impl.dungeon.puzzlesolvers.PuzzleSolvers
import quoi.module.impl.dungeon.secrets.Secrets
import quoi.module.impl.floor7.*
import quoi.module.impl.general.*
import quoi.module.impl.mining.*
import quoi.module.impl.misc.*
import quoi.module.impl.misc.catmode.CatMode
import quoi.module.impl.general.chat.Chat
import quoi.module.impl.misc.dojo.Dojo
import quoi.module.impl.general.inventory.Inventory
import quoi.module.impl.mining.glacitetunnels.GlaciteTunnels
import quoi.module.impl.misc.riftsolvers.MirrorverseSolvers
import quoi.module.impl.misc.slayers.Slayers
import quoi.module.impl.render.*
import quoi.module.impl.render.clickgui.ClickGui
import quoi.module.settings.impl.KeybindComponent

object ModuleManager : EventListener {
    val modules = mutableListOf<Module>()

    fun initialise() {
        modules += listOf(
            ClickGui,
            // DUNGEON
            ShadowAssassinAlert,
            LeapMenu,
            AutoDoorOpener,
            DungeonAbilities,
            DungeonBreaker,
            FireFreeze,
            DungeonESP,
            Splits,
            Secrets,
            PuzzleSolvers,
            InteractiveMap,
            DungeonMap,
            AutoRoutes,
            BloodCamp,
            WarpCooldown,
            AutoCroesus,
            AutoPotions,

            // FLOOR 7
            ArrowAlign,
            AutoLeap,
            BarrierBoom,
            FuckDiorite,
            LavaBounce,
            LightsDevice,
            P4PlatformHighlight,
            SimonSays,
            TerminalAura,
            TickTimers,
            AutoInvincibility,
            InvincibilityTimer,
            WitherCloak,

            // MISC
            Test,
            CatMode,
            ChocolateFactory,
//            CustomTriggers,
            MirrorverseSolvers,
            AutoCarnival,
            Slayers,
            Dojo,

            // GENERAL
            AutoSprint,
            PlayerDisplay,
            Tweaks,
            AntiNick,
            AutoBookCombine,
            AutoClicker,
            AutoGFS,
            AutoHotbar,
            AutoJoinSkyBlock,
            AutoKick,
            AutoLoadout,
            AutoSell,
            AutoWardrobe,
            Chat,
            EscrowFix,
            Inventory,
            PetKeybinds,
            Titles,
            WardrobeKeybinds,

            // RENDER
            NameTags,
            RenderOptimiser,
            CustomMainMenu,
            NickHider,
            RevertMasterStars,
            HidePlayers,
            InfoHud,
            PlayerESP,
            Trajectories,
            EtherwarpOverlay,
            Waypoints,
            ItemAnimations,

            // MINING
            CrystalHollowsMap,
            CrystalHollowsScanner,
            CommissionDisplay,
            GlaciteTunnels,
            MineshaftESP,
            GrieferTracker,
            NoBreakReset,
            NoGemstoneDesync,
            GhostESP,
            AbilityAlert,
        )

        modules.filter { it.alwaysActive }.forEach { it.onEnable() }

        modules.forEach { module ->
            module.keybinding.let {
                module.register(KeybindComponent("Key bind", it, desc = "Toggles the module"))
            }
        }

        on<KeyEvent.Press> { invokeKeybind(key, true) }
        on<KeyEvent.Release> { invokeKeybind(key, false) }
        on<MouseEvent.Click> { invokeKeybind(button - 100, state) }

        on<GuiEvent.Key.Press> { invokeKeybind(key, true) }
        on<GuiEvent.Key.Release> { invokeKeybind(key, false) }
        on<GuiEvent.Click> { invokeKeybind(button - 100, state) }
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
