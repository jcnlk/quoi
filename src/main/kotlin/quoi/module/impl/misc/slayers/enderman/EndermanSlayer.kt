package quoi.module.impl.misc.slayers.enderman

import quoi.api.skyblock.location.Island
import quoi.module.impl.misc.slayers.ISlayer
import quoi.module.impl.misc.slayers.Slayers
import quoi.module.settings.group.SettingGroup

object EndermanSlayer : SettingGroup(Slayers, "Enderman", area = Island.TheEnd), ISlayer {
    override val features = setOf(BeaconESP)

    override val running: Boolean
        get() = super.running && features.any { it.enabled }
}
