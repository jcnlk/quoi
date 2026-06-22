package quoi.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import quoi.api.events.KeyEvent;
import quoi.api.input.MutableInput;
import quoi.utils.skyblock.player.RotationUtils;

@Mixin(KeyboardInput.class)
public class KeyboardInputMixin {
    @WrapOperation(
            method = "tick",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/player/KeyboardInput;keyPresses:Lnet/minecraft/world/entity/player/Input;",
                    opcode = Opcodes.PUTFIELD
            )
    )
    private void onTick(KeyboardInput instance, Input input, Operation<Void> original) {
        MutableInput mutInput = new MutableInput(input);
        RotationUtils.adjustInputFromDirection(mutInput);
        KeyEvent.Input event = new KeyEvent.Input(input, mutInput);
        event.post();
        // Preserve downstream KeyboardInput mixins instead of short-circuiting the field write.
        original.call(instance, event.getInput().toInput());
    }
}
