package quoi.api.events

import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.phys.BlockHitResult
import quoi.api.events.core.Event

class UseItemOnPostEvent(
    val hand: InteractionHand,
    val hitResult: BlockHitResult,
    val interactionResult: InteractionResult
) : Event()
