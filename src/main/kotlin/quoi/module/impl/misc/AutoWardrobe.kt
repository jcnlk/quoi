package quoi.module.impl.misc

import quoi.api.events.core.on

import net.minecraft.client.KeyMapping
import quoi.api.abobaui.dsl.px
import quoi.api.abobaui.elements.impl.Text.Companion.shadow
import quoi.api.abobaui.elements.impl.Text.Companion.textSupplied
import quoi.api.colour.Colour
import quoi.api.events.KeyEvent
import quoi.api.events.MouseEvent
import quoi.api.events.TickEvent
import quoi.api.input.CatKeys
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.impl.KeybindComponent
import quoi.utils.skyblock.player.MovementUtils.stop
import quoi.utils.skyblock.player.WardrobeUtils
import quoi.utils.ui.hud.impl.TextHud

object AutoWardrobe : Module(
    "Auto Wardrobe",
    desc = "Automatically equips wardrobe slots through /quoi wardrobe."
) {
    private val disableUnequip by switch("Disable unequip", desc = "Prevents clicking the currently equipped wardrobe slot.")
    private val preventMoving by switch("Prevent moving", desc = "Stops your movement while a wardrobe equip is in progress.")
    private val blockInputs by switch("Block inputs", desc = "Blocks keyboard and mouse input while a wardrobe equip is in progress.")
    private val keybinds by text("Keybinds")
    private val wardrobeKeys = (1..9).map { i ->
        register(
            KeybindComponent("Slot $i", CatKeys.KEY_NONE, "Equips wardrobe slot $i.")
                .childOf(::keybinds)
                .onPress { onWardrobeKey(i) }
        )
    }
    private val hud by textHud("Wardrobe hud", Colour.WHITE, font = TextHud.HudFont.Minecraft) {
        visibleIf { this@AutoWardrobe.enabled && (preview || WardrobeUtils.isBusy()) }
        column {
            textSupplied(
                supplier = { WardrobeUtils.equippingSlot?.let { "Equipping §7[§c$it§7]" } ?: "Equipping §7[§c1§7]" },
                colour = colour,
                font = font,
                size = 18.px,
            ).shadow = shadow
        }
    }.setting()

    private var blockingGameInput = false

    override fun onDisable() {
        stopInputBlock()
        super.onDisable()
    }

    init {
        on<TickEvent.Start> {
            if ((preventMoving || blockInputs) && blockingGameInput) player.stop()
        }

        on<KeyEvent.Press> {
            if (blockInputs && blockingGameInput) cancel()
        }

        on<KeyEvent.Release> {
            if (blockInputs && blockingGameInput) cancel()
        }

        on<MouseEvent.Click> {
            if (blockInputs && blockingGameInput) cancel()
        }

        on<MouseEvent.Scroll> {
            if (blockInputs && blockingGameInput) cancel()
        }

        on<MouseEvent.Move> {
            if (blockInputs && blockingGameInput) cancel()
        }
    }

    private fun onWardrobeKey(slot: Int) {
        if (!enabled || mc.screen != null) return
        WardrobeUtils.equip(
            slot,
            preventMove = false,
            disableUnequip = disableUnequip,
            onMenuOpen = { if (enabled) blockingGameInput = true },
            onMenuClose = ::stopInputBlock,
        )
    }

    private fun stopInputBlock() {
        if (!blockingGameInput) return

        blockingGameInput = false
        KeyMapping.setAll()
    }
}
