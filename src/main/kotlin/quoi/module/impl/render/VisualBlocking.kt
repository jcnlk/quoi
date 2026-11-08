package quoi.module.impl.render

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.tags.TagKey
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import quoi.api.commands.internal.GreedyString
import quoi.config.Config
import quoi.module.Module
import quoi.module.settings.Setting
import quoi.utils.ChatUtils.modMessage
import quoi.utils.skyblock.item.ItemUtils.skyblockId

object VisualBlocking : Module(
    "Visual Blocking",
    desc = "Shows a classic blocking pose while holding use. Manage item lists with /quoi blocking."
) {
    private val whitelist = +textInput("Whitelist", "#minecraft:swords", length = 0).hide()
    private val blacklist = +textInput("Blacklist", length = 0).hide()
    val thirdPerson by switch("Third person", true, desc = "Also shows the blocking pose on your own player model.")
    private val whitelistFilter = BlockingItemFilter()
    private val blacklistFilter = BlockingItemFilter()

    private enum class ListAction { ADD, REMOVE, TOGGLE }

    init {
        val blocking = command.sub("blocking").description("Manages Visual Blocking item lists.")
        for (list in listOf(whitelist, blacklist)) {
            val name = list.name.lowercase()
            val listCommand = blocking.sub(name).description("Manages the blocking $name.")
            for (action in ListAction.entries) {
                listCommand.sub(action.name.lowercase()) { type: String, entry: GreedyString? ->
                    editEntry(list, type, entry?.string, action)
                }.description("${action.name.lowercase().replaceFirstChar(Char::uppercase)} a SkyBlock ID or vanilla item name, or use the held item.")
                    .suggests("type", "skyblock", "vanilla")
                    .suggestsCtx("entry") { ctx ->
                        val skyblock = StringArgumentType.getString(ctx, "type").equals("skyblock", true)
                        listEntries(list).filter { it.startsWith("skyblock:") == skyblock }
                            .map { it.removePrefix("skyblock:") }
                    }
            }
            listCommand.sub("list") {
                val contents = listEntries(list).joinToString(", ").ifEmpty { "empty" }
                modMessage("Blocking $name: $contents")
            }.description("Shows the blocking $name.")
            listCommand.sub("clear") {
                saveEntries(list, emptyList())
                modMessage("Cleared the blocking $name.")
            }.description("Clears the blocking $name.")
        }
    }

    private fun listEntries(list: Setting<String>) = list.value
        .split(',', ';', '\n').mapNotNull(BlockingItemFilter::normalizeEntry).distinct().toMutableList()

    private fun saveEntries(list: Setting<String>, entries: List<String>) {
        list.value = entries.joinToString(", ")
        Config.save()
    }

    private fun editEntry(list: Setting<String>, type: String, value: String?, action: ListAction) {
        val skyblock = when (type.lowercase()) {
            "skyblock" -> true
            "vanilla" -> false
            else -> return modMessage("&cChoose skyblock or vanilla.")
        }
        val raw = value ?: mc.player?.mainHandItem?.takeUnless { it.isEmpty }?.let { stack ->
            if (skyblock) stack.skyblockId?.takeIf(String::isNotBlank)
            else BuiltInRegistries.ITEM.getKey(stack.item).toString()
        } ?: return modMessage("&cHold a matching item or provide its ${if (skyblock) "SkyBlock ID" else "vanilla name"}.")
        val id = BlockingItemFilter.normalizeEntry(if (skyblock) "skyblock:$raw" else raw)
            ?.takeIf { it.startsWith("skyblock:") == skyblock }
            ?: return modMessage("&cInvalid $type entry.")
        val entries = listEntries(list)
        val present = id in entries
        val add = when (action) {
            ListAction.ADD -> true
            ListAction.REMOVE -> false
            ListAction.TOGGLE -> !present
        }
        val name = list.name.lowercase()
        if (add == present) return modMessage("$id ${if (present) "is already" else "is not"} in the blocking $name.")
        if (add) entries.add(id) else entries.remove(id)
        saveEntries(list, entries)
        modMessage("${if (add) "Added" else "Removed"} $id ${if (add) "to" else "from"} the blocking $name.")
    }

    @JvmStatic
    fun isBlocking(stack: ItemStack): Boolean {
        if (!enabled) return false
        val player = mc.player ?: return false
        if (mc.gui.screen() != null || !mc.options.keyUse.isDown || player.isSpectator || player.isSleeping ||
            player.isUsingItem || player.isAutoSpinAttack) return false
        if (ItemAnimations.isMap(stack) || !ItemStack.isSameItemSameComponents(stack, player.mainHandItem)) return false
        return whitelistFilter.matches(stack, whitelist.value) && !blacklistFilter.matches(stack, blacklist.value)
    }

    @JvmStatic
    fun applyFirstPerson(pose: PoseStack, stack: ItemStack, context: ItemDisplayContext) {
        val player = mc.player ?: return
        val rightHand = player.mainArm == HumanoidArm.RIGHT
        val mainContext = if (rightHand) ItemDisplayContext.FIRST_PERSON_RIGHT_HAND else ItemDisplayContext.FIRST_PERSON_LEFT_HAND
        if (context != mainContext || !isBlocking(stack)) return

        val side = if (rightHand) 1f else -1f
        pose.translate(-0.12f * side, 0.08f, 0.14f)
        //$ pose_rotation XP '-100f'
        pose.mulPose(Axis.XP.rotationDegrees(-100f))
        //$ pose_rotation YP '15f * side'
        pose.mulPose(Axis.YP.rotationDegrees(15f * side))
        //$ pose_rotation ZP '75f * side'
        pose.mulPose(Axis.ZP.rotationDegrees(75f * side))
    }

    internal class BlockingItemFilter {
        private var source: String? = null
        private val items = mutableSetOf<Identifier>()
        private val skyblockIds = mutableSetOf<String>()
        private val tags = mutableListOf<TagKey<Item>>()

        fun matches(stack: ItemStack, entries: String): Boolean {
            if (stack.isEmpty) return false
            if (source != entries) parse(entries)

            return BuiltInRegistries.ITEM.getKey(stack.item) in items ||
                tags.any { stack.`is`(it) } ||
                (skyblockIds.isNotEmpty() && stack.skyblockId?.uppercase() in skyblockIds)
        }

        private fun parse(entries: String) {
            source = entries
            items.clear()
            skyblockIds.clear()
            tags.clear()

            for (value in entries.split(',', ';', '\n')) {
                val entry = normalizeEntry(value) ?: continue
                when {
                    entry.startsWith("skyblock:") -> skyblockIds.add(entry.substringAfter(':'))
                    entry.startsWith('#') -> tags.add(TagKey.create(Registries.ITEM, Identifier.parse(entry.drop(1))))
                    else -> items.add(Identifier.parse(entry))
                }
            }
        }

        companion object {
            fun normalizeEntry(value: String): String? {
                val entry = value.trim()
                if (entry.startsWith("skyblock:", ignoreCase = true)) {
                    val id = entry.substringAfter(':').trim()
                    if (id.isEmpty() || id.any { it.isWhitespace() || it == ',' || it == ';' }) return null
                    return "skyblock:${id.uppercase()}"
                }
                val tag = entry.startsWith('#')
                val id = Identifier.tryParse(entry.removePrefix("#").lowercase())?.takeUnless { it.path.isEmpty() } ?: return null
                return if (tag) "#$id" else id.toString()
            }
        }
    }
}
