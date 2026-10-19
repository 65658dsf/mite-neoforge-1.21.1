package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.registry.MiteFoodCatalog;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Common item-stack entry covers special native foods and their animations. @author NingMeng123 */
@Mixin(ItemStack.class)
public abstract class FoodItemStackMixin {
    @Inject(method="getUseDuration",at=@At("RETURN"),cancellable=true)
    private void mite$foodDuration(LivingEntity entity,CallbackInfoReturnable<Integer> cir) {
        if(MiteFoodCatalog.profile((ItemStack)(Object)this)!=null)
            cir.setReturnValue(Math.max(1,io.github.ningmeng123.mite.api.MiteExtensions.policy().foodUseDuration((ItemStack)(Object)this,entity,32)));
    }
    @Inject(method="getUseAnimation",at=@At("RETURN"),cancellable=true)
    private void mite$foodAnimation(CallbackInfoReturnable<UseAnim> cir) {
        var profile=MiteFoodCatalog.profile((ItemStack)(Object)this);
        if(profile!=null) cir.setReturnValue(profile.drink()?UseAnim.DRINK:UseAnim.EAT);
    }
}
