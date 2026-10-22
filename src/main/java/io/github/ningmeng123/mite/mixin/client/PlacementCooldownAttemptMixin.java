package io.github.ningmeng123.mite.mixin.client;

import io.github.ningmeng123.mite.client.PlacementClickTiming;
import io.github.ningmeng123.mite.world.PlacementDelay;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Records rejected local placement gates without intercepting other uses. @author NingMeng123 */
@Mixin(value = PlacementDelay.class, remap = false)
public abstract class PlacementCooldownAttemptMixin {
    @Inject(method = "blocked", at = @At("RETURN"))
    private static void mite$recordBlockedPlacement(Player player, CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValueZ() && player != null && player.level().isClientSide) PlacementClickTiming.blocked();
    }
}
