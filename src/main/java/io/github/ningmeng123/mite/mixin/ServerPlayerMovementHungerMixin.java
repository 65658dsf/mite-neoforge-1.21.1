package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.survival.MovementHunger;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep modern swim/crouch/elytra statistics, replacing only their duplicated food fees. @author NingMeng123 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMovementHungerMixin {
    @Inject(method = "checkMovementStatistics", at = @At("HEAD"))
    private void mite$sourceMovement(double x, double y, double z, CallbackInfo ci) {
        MovementHunger.travel((ServerPlayer)(Object)this, x, y, z);
    }
    @Redirect(method = "checkMovementStatistics", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;causeFoodExhaustion(F)V"), require = 6)
    private void mite$noDuplicateMovementFee(ServerPlayer player, float amount) {}
}
