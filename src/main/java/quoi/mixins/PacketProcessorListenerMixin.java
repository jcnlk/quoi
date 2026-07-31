package quoi.mixins;

import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import quoi.api.events.PacketEvent;

// based on https://github.com/Noamm9/NoammAddons/blob/1.1.9/src/main/java/com/github/noamm9/mixin/MixinPacketProcessorListener.java
@Mixin(targets = "net.minecraft.network.PacketProcessor$ListenerAndPacket")
public abstract class PacketProcessorListenerMixin {
    @Shadow
    @Final
    private Packet<?> packet;

    @Inject(
            method = "handle",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/Packet;handle(Lnet/minecraft/network/PacketListener;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void quoi$afterPacketHandled(CallbackInfo ci) {
        new PacketEvent.ReceivedPost(packet).post();
    }
}
