package quoi.api.events

import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import quoi.api.events.core.CancellableEvent
import quoi.api.events.core.Event

abstract class PlayerEvent {
    /**
     * modified NoammAddons (CC0-1.0)
     * original: https://github.com/Noamm9/NoammAddons/blob/3fe8feff94494d48a8d2fa76ee8b6eff1f57e70c/src/main/kotlin/com/github/noamm9/event/impl/PlayerInteractEvent.kt
     */
    abstract class Interact(val item: ItemStack, val hand: InteractionHand, val hitResult: HitResult?) :
        CancellableEvent() {
        class LeftClick(item: ItemStack, hitResult: HitResult?) : Interact(item, InteractionHand.MAIN_HAND, hitResult)
        class RightClick(item: ItemStack, hand: InteractionHand, hitResult: HitResult?) :
            Interact(item, hand, hitResult)
    }

    abstract class UseBlock {
        class Post(
            val hand: InteractionHand,
            val hitResult: BlockHitResult,
            val interactionResult: InteractionResult
        ) : Event()
    }
}
