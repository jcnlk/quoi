package quoi.module.impl.misc.rift

import quoi.api.skyblock.location.Island
import quoi.module.Module
import quoi.module.impl.misc.rift.dreadfarm.DreadFarm
import quoi.module.impl.misc.rift.dreadfarm.impl.WiltedBerberis

@Suppress("unused_expression")
object Rift : Module(
    "Rift",
    desc = "Helpers for the Rift.",
    area = Island.Rift
) {
    init {
        DreadFarm
    }

    override fun onDisable() {
        WiltedBerberis.onDisable()
    }
}
