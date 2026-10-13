package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.survival.FoodConsumption;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Final permission check occurs before subclass food effects or consumption. @author NingMeng123 */
@Mixin(LivingEntity.class)
public abstract class CompleteFoodUseMixin {
    @Inject(method="completeUsingItem",at=@At("HEAD"),cancellable=true)
    private void mite$checkBeforeCompleting(CallbackInfo ci) {
        LivingEntity self=(LivingEntity)(Object)this;
        if(self instanceof Player player && !FoodConsumption.canIngest(player,self.getUseItem())) {
            self.stopUsingItem();
            ci.cancel();
        }
    }
}
