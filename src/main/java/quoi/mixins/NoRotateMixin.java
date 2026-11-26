package quoi.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import quoi.module.impl.dungeon.autoclear.executor.ClearExecutor;
import quoi.module.impl.dungeon.autoclear.impl.AutoRoutes;

import java.util.concurrent.CopyOnWriteArrayList;

@Pseudo
@Mixin(targets = "com.github.noamm9.features.impl.misc.NoRotate", remap = false)
public abstract class NoRotateMixin {
    @Shadow
    @Final
    private static CopyOnWriteArrayList<?> pendingTeleports;

    @ModifyArg(method = "init", at = @At(
            value = "INVOKE",
            target = "Lcom/github/noamm9/event/EventListener;<init>(Ljava/lang/Class;Lcom/github/noamm9/event/priority/EventPriority;ZLkotlin/jvm/functions/Function1;)V"
    ), index = 3)
    private Function1<Object, Unit> quoi$pauseListener(Function1<Object, Unit> listener) {
        return context -> {
            if (quoi$routing()) {
                pendingTeleports.clear();
                return Unit.INSTANCE;
            }
            return listener.invoke(context);
        };
    }

    @ModifyExpressionValue(method = "*", at = @At(
            value = "FIELD",
            opcode = Opcodes.GETSTATIC,
            target = "Lcom/github/noamm9/features/impl/misc/NoRotate;pendingTeleports:Ljava/util/concurrent/CopyOnWriteArrayList;"
    ))
    private static CopyOnWriteArrayList<?> quoi$clearPredictions(CopyOnWriteArrayList<?> predictions) {
        if (quoi$routing()) predictions.clear();
        return predictions;
    }

    @Unique
    private static boolean quoi$routing() {
        return AutoRoutes.INSTANCE.getRouting() || ClearExecutor.INSTANCE.getRouting();
    }
}
