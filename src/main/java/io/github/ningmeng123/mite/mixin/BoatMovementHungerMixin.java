package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.survival.MovementHunger;
import net.minecraft.world.entity.vehicle.Boat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** One rowing charge per server boat tick, including forward and reverse input. @author NingMeng123 */
@Mixin(Boat.class)
public abstract class BoatMovementHungerMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void mite$rowingEffort(CallbackInfo ci) { MovementHunger.rowing((Boat)(Object)this); }
}
