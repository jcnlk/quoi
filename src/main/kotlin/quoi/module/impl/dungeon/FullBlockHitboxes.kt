package quoi.module.impl.dungeon

import quoi.api.skyblock.dungeon.Dungeon.inDungeons
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.childOf

// Kyleen
object FullBlockHitboxes : Module(
    "Full Block Hitboxes",
    desc = "Expands the hitboxes of buttons, chests, levers, mushrooms, and skulls."
) {
    private val buttons by switch("Buttons")
    private val buttonHitbox by selector(
        "Button hitbox",
        ButtonHitbox.Expanded,
        desc = "Chooses between the directional expanded hitbox and a full-block hitbox."
    ).childOf(::buttons)
    private val chests by switch("Chests")
    private val levers by switch("Levers")
    private val mushrooms by switch("Mushrooms")
    private val skulls by switch("Skulls")
    private val dungeonsOnly by switch("Dungeons only", desc = "Only expands hitboxes while in dungeons.")

    @JvmStatic
    fun shouldExpandHitbox(blockType: BlockType): Boolean =
        enabled && blockType.isEnabled && (!dungeonsOnly || inDungeons)

    @JvmStatic
    fun shouldUseFullBlockButtonHitbox(): Boolean = buttonHitbox.selected == ButtonHitbox.FullBlock

    private val BlockType.isEnabled: Boolean
        get() = when (this) {
            BlockType.Buttons -> buttons
            BlockType.Chests -> chests
            BlockType.Levers -> levers
            BlockType.Mushrooms -> mushrooms
            BlockType.Skulls -> skulls
        }

    private enum class ButtonHitbox {
        Expanded,
        FullBlock,
    }

    enum class BlockType {
        Buttons,
        Chests,
        Levers,
        Mushrooms,
        Skulls,
    }
}