package quoi.module.impl.render

import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.minecraft.client.gui.components.ImageButton
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.decoration.ArmorStand
import quoi.api.events.GuiEvent
import quoi.api.events.PacketEvent
import quoi.api.events.RenderEvent
import quoi.api.skyblock.Island
import quoi.api.skyblock.Location.currentArea
import quoi.api.skyblock.Location.subarea
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.M7Phases
import quoi.module.Module
import quoi.utils.skyblock.item.ItemUtils.texture
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.textures

object RenderOptimiser : Module(
    "Render Optimiser",
    desc = "Various render optimisation features."
) {
    @JvmStatic val disableTextShadow by switch("Disable text shadow", desc = "Disables text shadows in hud elements.")
    @JvmStatic val containerTextShadow by switch("Container text shadow", desc = "Renders text in containers with shadow.")
    @JvmStatic val disableFog by switch("Disable fog", desc = "Disables fog rendering.")

    private val hideFallingBlocks by switch("Hide falling blocks", desc = "Disables falling blocks rendering.")
    private val hideLightning by switch("Hide lightning", desc = "Disables lightning rendering.")
    private val hideWeaver by switch("Hide soul weaver", desc = "Disables soul weaver skulls rendering.")
    private val hideFairy by switch("Hide healer fairy", desc = "Disables healer fairy rendering.")
    private val hideHealerOrbs by switch("Hide healer orbs", desc = "Hides healer support orbs in dungeons.")
    private val hideRecipeBook by switch("Hide recipe book", desc = "Disables recipe book rendering.")
    private val hideBlindness by switch("Hide blindness", desc = "Disabled blindness effect rendering.")
    private val hideParticles by switch("Hide particles", desc = "Hides particles everywhere except floor 7 phase 5.")
    @JvmStatic val hidePotionBubbles by switch("Hide potion bubbles", desc = "Hides potion effect particles.")
    @JvmStatic val hideFire by switch("Hide fire overlay", desc = "Disables fire overlay rendering.")

    @JvmStatic val fullBright by switch("Full bright", desc = "Makes dark places bright.")

    private const val HEALER_FAIRY_TEXTURE = "ewogICJ0aW1lc3RhbXAiIDogMTcxOTQ2MzA5MTA0NywKICAicHJvZmlsZUlkIiA6ICIyNjRkYzBlYjVlZGI0ZmI3OTgxNWIyZGY1NGY0OTgyNCIsCiAgInByb2ZpbGVOYW1lIiA6ICJxdWludHVwbGV0IiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzJlZWRjZmZjNmExMWEzODM0YTI4ODQ5Y2MzMTZhZjdhMjc1MmEzNzZkNTM2Y2Y4NDAzOWNmNzkxMDhiMTY3YWUiCiAgICB9CiAgfQp9"
    private const val SOUL_WEAVER_TEXTURE = "eyJ0aW1lc3RhbXAiOjE1NTk1ODAzNjI1NTMsInByb2ZpbGVJZCI6ImU3NmYwZDlhZjc4MjQyYzM5NDY2ZDY3MjE3MzBmNDUzIiwicHJvZmlsZU5hbWUiOiJLbGxscmFoIiwic2lnbmF0dXJlUmVxdWlyZWQiOnRydWUsInRleHR1cmVzIjp7IlNLSU4iOnsidXJsIjoiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS8yZjI0ZWQ2ODc1MzA0ZmE0YTFmMGM3ODViMmNiNmE2YTcyNTYzZTlmM2UyNGVhNTVlMTgxNzg0NTIxMTlhYTY2In19fQ=="
    private const val ABILITY_ORB_TEXTURE = "ewogICJ0aW1lc3RhbXAiIDogMTYzODUyNDAzODE5OCwKICAicHJvZmlsZUlkIiA6ICIzOWEzOTMzZWE4MjU0OGU3ODQwNzQ1YzBjNGY3MjU2ZCIsCiAgInByb2ZpbGVOYW1lIiA6ICJkZW1pbmVjcmFmdGVybG9sIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzVlZTRiYjQ4MjFkMGY1ZWQ4NjVjMjEwOTBhODBiNWVlN2Q1MjI2ODQ3NmVlMjVkMzg5NzEwZjdjYzlmMTEwZDYiCiAgICB9CiAgfQp9"
    private const val SUPPORT_ORB_TEXTURE = "ewogICJ0aW1lc3RhbXAiIDogMTYwNTM1NjUyNzQzOSwKICAicHJvZmlsZUlkIiA6ICJhYTZhNDA5NjU4YTk0MDIwYmU3OGQwN2JkMzVlNTg5MyIsCiAgInByb2ZpbGVOYW1lIiA6ICJiejE0IiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzE1NzhiNGFmM2ZkZDkxNTFiODUwYjEzYzY3YzQ1ODAyMjRjN2Y2MDA1MjcxM2YyZDE1MWY3YzE1ZGMwZDdiMzQiCiAgICB9CiAgfQp9"
    private const val DAMAGE_ORB_TEXTURE = "ewogICJ0aW1lc3RhbXAiIDogMTYwNDY4NDIxNTAyMCwKICAicHJvZmlsZUlkIiA6ICI3NzI3ZDM1NjY5Zjk0MTUxODAyM2Q2MmM2ODE3NTkxOCIsCiAgInByb2ZpbGVOYW1lIiA6ICJsaWJyYXJ5ZnJlYWsiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYWI4NmRhMmUyNDNjMDVkYzA4OThiMGNjNWQzZTY0ODc3MTczMTc3ZTBhMjM5NDQyNWNlYzEwMDI1OWNiNDUyNiIKICAgIH0KICB9Cn0="
    private val HEALER_ORB_NAMES = listOf(
        "ABILITY DAMAGE",
        "DAMAGE",
        "DEFENSE",
    )
    private val HEALER_ORB_TEXTURES = setOf(
        ABILITY_ORB_TEXTURE,
        SUPPORT_ORB_TEXTURE,
        DAMAGE_ORB_TEXTURE,
    )

    init {
        on<PacketEvent.Received> {
            if (mc.player == null) return@on
            when(packet) {
                is ClientboundAddEntityPacket -> {
                    if (hideFallingBlocks && packet.type == EntityType.FALLING_BLOCK ||
                        hideLightning && packet.type == EntityType.LIGHTNING_BOLT) cancel()
                }

                is ClientboundUpdateMobEffectPacket -> {
                    if (hideBlindness &&
                        packet.entityId == player.id &&
                        packet.effect == MobEffects.BLINDNESS) cancel()
                }

                is ClientboundLevelParticlesPacket -> {
                    val isGeyserFishingParticle =
                        currentArea.isArea(Island.CrimsonIsle) &&
                        subarea?.contains("Blazing Volcano", ignoreCase = true) == true &&
                        packet.particle.type == ParticleTypes.CLOUD &&
                        packet.count == 15 &&
                        packet.maxSpeed == 0.05f &&
                        packet.xDist == 0.1f &&
                        packet.yDist == 0.6f &&
                        packet.zDist == 0.1f

                    if (hideParticles &&
                        !currentArea.isArea(Island.Garden) &&
                        Dungeon.getF7Phase() != M7Phases.P5 &&
                        !isGeyserFishingParticle
                    ) cancel()
                    else if (hidePotionBubbles && packet.particle.type == ParticleTypes.ENTITY_EFFECT) cancel()
                }

            }
        }

        on<RenderEvent.Entity> {
            if (!Dungeon.inDungeons) return@on
            val armorStand = entity as? ArmorStand ?: return@on
            val headTexture = armorStand.getItemBySlot(EquipmentSlot.HEAD).texture

            if (hideHealerOrbs) {
                val name = armorStand.name.string.noControlCodes
                if (HEALER_ORB_NAMES.any { name.startsWith(it) } || headTexture in HEALER_ORB_TEXTURES) {
                    cancel()
                    return@on
                }
            }

            if (hideFairy && armorStand.getItemBySlot(EquipmentSlot.MAINHAND).texture == HEALER_FAIRY_TEXTURE) {
                cancel()
                return@on
            }

            if (hideWeaver && headTexture == SOUL_WEAVER_TEXTURE) {
                cancel()
            }
        }

        on<GuiEvent.Open.Post> {
            if (!hideRecipeBook) return@on
            Screens.getWidgets(screen)
                .filterIsInstance<ImageButton>()
                .firstOrNull { it.textures == RecipeBookComponent.RECIPE_BUTTON_SPRITES }
                ?.visible = false
        }
    }

    @JvmStatic
    fun should(condition: Boolean): Boolean = this.enabled && condition // idkman
}
