package io.github.ningmeng123.mite.mixin.client;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import io.github.ningmeng123.mite.survival.FoodConsumption;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** R196 allows sprinting with food energy, including at level zero. @author NingMeng123 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerFoodMixin {
    @Inject(method = "hasEnoughFoodToStartSprinting", at=@At("HEAD"),cancellable=true)
    private void mite$sprintWhileEnergyRemains(CallbackInfoReturnable<Boolean> cir) {
        LocalPlayer player=(LocalPlayer)(Object)this;
        // This eligibility method is also polled during ordinary forward walking. The shared
        // LivingEntity sprint-toggle hook rejects and reveals a curse only on an actual attempt.
        cir.setReturnValue(player.getAbilities().mayfly || FoodConsumption.hasEnergy(player));
    }
}
