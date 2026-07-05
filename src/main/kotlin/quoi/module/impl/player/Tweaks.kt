package quoi.module.impl.player

import quoi.api.events.TickEvent
import quoi.api.events.core.on
import quoi.api.skyblock.location.Island
import quoi.api.skyblock.location.Location.currentArea
import quoi.api.skyblock.location.Location.inSkyblock
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.childOf
import net.minecraft.sounds.SoundSource

object Tweaks : Module(
    name = "Tweaks",
    desc = "Various player tweaks."
) {
    @JvmStatic val fixDoubleSneak by switch("Fix double sneak", desc = "Fixes a bug where your camera can bounce when you quickly sneak and unsneak.") // kinda a rendering thing rite? :grin:
    @JvmStatic val instantSneak by switch("Instant sneak", desc = "Instantly moves your camera when sneaking.")
    @JvmStatic val muteSounds by switch("Mute sounds", desc = "Mutes in-game sounds while Minecraft is unfocused.")

    private val skyblockOnly by text("Skyblock only", desc = "Hypixel skyblock only features")
    @JvmStatic val disableItemCooldowns by switch("Disable item cooldowns", desc = "Disables item cooldowns such as ender pearls.").childOf(::skyblockOnly)
    @JvmStatic val fixInteract by switch("Fix interaction", desc = "Fixes a bug where you can't interact when SA jumps the player.").childOf(::skyblockOnly) // todo move to no interact module
    @JvmStatic val fixCrimsonIsleFog by switch("Fix Crimson Isle fog", desc = "Removes the Night Vision fog effect on Crimson Isle.").childOf(::skyblockOnly)

    /**
     * from OdinFabric (BSD 3-Clause)
     * copyright (c) 2025-2026 odtheking
     * original: https://github.com/odtheking/Odin/blob/main/src/main/kotlin/com/odtheking/odin/features/impl/skyblock/NoCursorReset.kt
     */
    private val noCursorReset by switch("No cursor reset").childOf(::skyblockOnly)

    private var time = System.currentTimeMillis()
    private var wasNotNull = false
    private var wasMuted = false

    init {
        on<TickEvent.End> {
            val muted = should(muteSounds) && !mc.isWindowActive
            if (muted != wasMuted) {
                wasMuted = muted
                SoundSource.entries.forEach(mc.soundManager::updateSourceVolume)
            }

            if (mc.screen != null) {
                wasNotNull = true
                time = System.currentTimeMillis()
            } else if (wasNotNull && mc.screen == null) {
                wasNotNull = false
                time = System.currentTimeMillis()

            }
        }
    }

    @JvmStatic
    fun should(condition: Boolean): Boolean = this.enabled && condition // idkman

    @JvmStatic
    fun shouldSb(condition: Boolean): Boolean = this.enabled && inSkyblock && condition

    @JvmStatic
    fun shouldFixCrimsonIsleFog(): Boolean = shouldSb(fixCrimsonIsleFog) && currentArea.isArea(Island.CrimsonIsle)

    @JvmStatic
    fun shouldHookMouse(): Boolean =
        System.currentTimeMillis() - time < 150 && shouldSb(noCursorReset)
}
