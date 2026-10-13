package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.survival.FoodConsumption;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ChorusFruitItem;
import net.minecraft.world.item.HoneyBottleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Modern subclass side effects must not happen after a rejected food use. @author NingMeng123 */
@Mixin({ChorusFruitItem.class,HoneyBottleItem.class})
public abstract class SpecialFoodFinishMixin {
    @Inject(method="finishUsingItem",at=@At("HEAD"),cancellable=true)
    private void mite$checkSpecialFood(ItemStack stack,Level level,LivingEntity entity,CallbackInfoReturnable<ItemStack> cir) {
        if(entity instanceof Player player && !FoodConsumption.canIngest(player,stack)) cir.setReturnValue(stack);
    }
}
