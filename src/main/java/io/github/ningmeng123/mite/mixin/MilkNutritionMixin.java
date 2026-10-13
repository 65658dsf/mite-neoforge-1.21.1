package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.registry.MiteFoodCatalog;
import io.github.ningmeng123.mite.survival.DualFoodData;
import io.github.ningmeng123.mite.survival.FoodConsumption;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MilkBucketItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Milk clears effects before nutrition, while vanilla handles the sole empty bucket. @author NingMeng123 */
@Mixin(MilkBucketItem.class)
public abstract class MilkNutritionMixin {
    @Inject(method="finishUsingItem",at=@At("HEAD"),cancellable=true)
    private void mite$milkNutrition(ItemStack stack,Level level,LivingEntity entity,CallbackInfoReturnable<ItemStack> cir) {
        if(!(entity instanceof Player player)) return;
        if(!FoodConsumption.canIngest(player,stack)) { cir.setReturnValue(stack); return; }
        if(!level.isClientSide) {
            player.removeAllEffects();
            ((DualFoodData)player.getFoodData()).mite$ingest(MiteFoodCatalog.profile(stack));
        }
    }
}
