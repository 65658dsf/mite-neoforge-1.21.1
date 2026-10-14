package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.entity.animal.Livestock032;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** rq produces from wellness, never the modern unconditional egg timer. @author NingMeng123 */
@Mixin(Chicken.class)
public abstract class Livestock032ChickenMixin {
    @Inject(method="aiStep",at=@At("HEAD"))
    private void mite$disableUnconditionalEgg(CallbackInfo ci) { ((Chicken)(Object)this).eggTime=Integer.MAX_VALUE; }
    @Inject(method="isFood",at=@At("HEAD"),cancellable=true)
    private void mite$sourceSeed(ItemStack stack,CallbackInfoReturnable<Boolean> cir) { cir.setReturnValue(stack.is(net.minecraft.world.item.Items.WHEAT_SEEDS)); }
}
