package quoi.utils

import quoi.QuoiMod

interface Shortcuts {
    val Shortcuts.mc get() = QuoiMod.mc
    val Shortcuts.player get() = quoi.utils.player
    val Shortcuts.level get() = quoi.utils.level
    val Shortcuts.connection get() = quoi.utils.connection
    val Shortcuts.gameMode get() = quoi.utils.gameMode
    val Shortcuts.inGame get() = quoi.utils.inGame
}