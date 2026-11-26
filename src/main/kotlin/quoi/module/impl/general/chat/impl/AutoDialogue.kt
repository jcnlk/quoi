package quoi.module.impl.general.chat.impl

import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket
import quoi.api.events.ChatEvent
import quoi.api.events.core.on
import quoi.module.impl.general.chat.Chat
import quoi.module.settings.group.ToggleableGroup

object AutoDialogue : ToggleableGroup(Chat, "Auto dialogue", desc = "Automatically continues dialogues with NPCs.") {
    init {
        on<ChatEvent.Packet> {
            if (!unformatted.startsWith("Select an option: ") || "[BARBARIANS] [MAGES]" in unformatted) return@on
            val click = text.siblings.getOrNull(0)?.style?.clickEvent as? ClickEvent.Custom ?: return@on
            connection.send(ServerboundCustomClickActionPacket(click.id, click.payload))
        }
    }
}
