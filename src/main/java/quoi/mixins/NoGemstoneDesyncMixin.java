package quoi.mixins;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import quoi.module.impl.mining.NoGemstoneDesync;

@Mixin(IronBarsBlock.class)
public abstract class NoGemstoneDesyncMixin {
    @ModifyReturnValue(method = "updateShape", at = @At("RETURN"))
    private BlockState quoi$keepGemstonePaneConnections(BlockState original) {
        if (NoGemstoneDesync.shouldFillDisconnectedPane(original)) {
            return NoGemstoneDesync.fillDisconnectedPane(original);
        }
        return original;
    }
}
