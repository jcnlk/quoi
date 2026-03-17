package quoi.module.impl.player

import quoi.api.skyblock.Location.inSkyblock
import quoi.api.skyblock.Location.onModernIsland
import quoi.module.Module
import quoi.module.settings.UISetting.Companion.childOf
import quoi.module.settings.impl.BooleanSetting
import quoi.module.settings.impl.TextSetting

object Tweaks : Module(
    name = "Tweaks",
    desc = "Various player tweaks."
) {
    @JvmStatic val fixDoubleSneak by BooleanSetting("Fix double sneak", desc = "Fixes a bug where your camera can bounce when you quickly sneak and unsneak.") // kinda a rendering thing rite? :grin:
    @JvmStatic val instantSneak by BooleanSetting("Instant sneak", desc = "Instantly moves your camera when sneaking.")

    private val skyblockOnly by TextSetting("Skyblock only", desc = "Hypixel skyblock only features")
    @JvmStatic val legacySneakHeight by BooleanSetting("Legacy sneak height", desc = "Reverts sneak height to pre 1.13 height.").childOf(skyblockOnly)
    @JvmStatic val disableCrawling by BooleanSetting("Disable crawling", desc = "Disables crawling animation (does not disable swimming).").childOf(skyblockOnly)
    @JvmStatic val disableItemCooldowns by BooleanSetting("Disable item cooldowns", desc = "Disables item cooldowns such as ender pearls.").childOf(skyblockOnly)
    @JvmStatic val fixInteract by BooleanSetting("Fix interaction", desc = "Fixes a bug where you can't interact when SA jumps the player.").childOf(skyblockOnly) // todo move to no interact module

    @JvmStatic
    fun should(condition: Boolean): Boolean = this.enabled && condition // idkman

    @JvmStatic
    fun shouldSb(condition: Boolean): Boolean = this.enabled && inSkyblock && !onModernIsland && condition
}