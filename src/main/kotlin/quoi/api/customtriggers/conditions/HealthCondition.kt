package quoi.api.customtriggers.conditions

import quoi.api.abobaui.elements.ElementScope
import quoi.api.customtriggers.*
import quoi.api.skyblock.SkyblockPlayer
import quoi.config.TypeName

@TypeName("health")
class HealthCondition(var comparison: Comparison = Comparison.BELOW, var percent: Int = 50) : TriggerCondition {
    enum class Comparison(val label: String) { BELOW("At or below"), ABOVE("At or above") }
    override fun validationError() = if (percent !in 0..100) "Health percentage must be between 0 and 100." else null
    override fun matches(ctx: TriggerContext): Boolean {
        if (SkyblockPlayer.maxHealth <= 0) return false
        val current = SkyblockPlayer.health * 100.0 / SkyblockPlayer.maxHealth
        return if (comparison == Comparison.BELOW) current <= percent else current >= percent
    }
    override fun displayString() = "Health ${comparison.label.lowercase()} $percent%"
    override fun ElementScope<*>.draw() = settingRow(
        { choiceField("Compare", { comparison }, Comparison.entries, { it.label }) { comparison = it } },
        { intField("Percent", { percent }, 0, 100) { percent = it } }
    )
}
