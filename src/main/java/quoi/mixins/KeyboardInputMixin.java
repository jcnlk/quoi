package quoi.mixins;

import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import quoi.api.events.KeyEvent;
import quoi.api.input.MutableInput;

@Mixin(KeyboardInput.class)
public class KeyboardInputMixin {
    @Inject(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Input;forward()Z"
            )
    )
    private void onTick(CallbackInfo ci) {
        KeyboardInput instance = (KeyboardInput) (Object) this;
        Input input = instance.keyPresses;
        KeyEvent.Input event = new KeyEvent.Input(input, new MutableInput(input));
        event.post();
        instance.keyPresses = event.getInput().toInput();
    }
}
