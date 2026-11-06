package io.github.ningmeng123.mite.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.client.PlacementClickTiming;
import io.github.ningmeng123.mite.world.PlacementDelay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** R196 gates both discrete and held use input at the same real-time deadline. @author NingMeng123 */
@Mixin(Minecraft.class)
public abstract class PlacementCooldownMinecraftMixin {
    @Shadow public LocalPlayer player;
    @Shadow private int rightClickDelay;
    @Unique private LocalPlayer mite$placementRepeatPlayer;

    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void mite$pollPlacementRepeat(CallbackInfo callback) {
        if (PlacementDelay.remainingMillis(player) > 0L) {
            mite$placementRepeatPlayer = player;
            rightClickDelay = 1;
        } else {
            if (player != null && mite$placementRepeatPlayer == player) rightClickDelay = 0;
            mite$placementRepeatPlayer = null;
        }
    }

    @WrapOperation(method = "startUseItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/InteractionResult;consumesAction()Z"))
    private boolean mite$clearAttemptAfterConsumedUse(InteractionResult result, Operation<Boolean> original) {
        boolean consumed = original.call(result);
        // A successful offhand action completes an earlier main-hand placement failure.
        if (consumed) PlacementClickTiming.actionConsumed();
        return consumed;
    }

    @WrapMethod(method = "startUseItem")
    private void mite$synchronizePlacementRepeat(Operation<Void> original) {
        // Native discrete clicks bypass rightClickDelay, so gate the entry point too.
        if (PlacementDelay.blocked(player)) {
            mite$placementRepeatPlayer = player;
            rightClickDelay = 1;
            return;
        }
        mite$placementRepeatPlayer = null;
        PlacementClickTiming.begin();
        try {
            original.call();
        } finally {
            boolean attempted = PlacementClickTiming.consumeAttempted();
            long remaining = PlacementDelay.remainingMillis(player);
            if (remaining > 0L) mite$placementRepeatPlayer = player;
            rightClickDelay = PlacementClickTiming.rightClickDelay(rightClickDelay, remaining, attempted,
                    player != null && player.isUsingItem());
        }
    }
}
