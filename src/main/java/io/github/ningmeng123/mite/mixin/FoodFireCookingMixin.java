package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.survival.FoodCooking;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Preserve the complete stack and entity ownership when fire finishes an uncooked food. @author NingMeng123 */
@Mixin(ItemEntity.class)
public abstract class FoodFireCookingMixin {
    @Shadow private int health;
    @Inject(method="hurt",at=@At(value="INVOKE",target="Lnet/minecraft/world/item/ItemStack;onDestroyed(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/world/damagesource/DamageSource;)V"),cancellable=true)
    private void mite$cookOnFireDestruction(DamageSource source,float damage,CallbackInfoReturnable<Boolean> cir) {
        ItemEntity self=(ItemEntity)(Object)this;
        if(self.level().isClientSide || !source.is(DamageTypeTags.IS_FIRE)) return;
        var cooked=FoodCooking.result(self.getItem());
        if(cooked==null) return;
        self.setItem(new ItemStack(cooked,self.getItem().getCount()));
        health=5;
        cir.setReturnValue(true);
    }
}
