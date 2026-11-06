package io.github.ningmeng123.mite.mixin.client;

import io.github.ningmeng123.mite.client.PlacementClickTiming;
import io.github.ningmeng123.mite.world.PlacementDelay;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Failed placement can retry on the next input tick. @author NingMeng123 */
@Mixin(value = PlacementDelay.class, remap = false)
public abstract class PlacementCooldownAttemptMixin {
    @Inject(method = "placementBlocked", at = @At("RETURN"))
    private static void mite$recordPlacementAttempt(Player player, CallbackInfoReturnable<Boolean> callback) {
        if (player != null && player.level().isClientSide) PlacementClickTiming.attempted();
    }
}
