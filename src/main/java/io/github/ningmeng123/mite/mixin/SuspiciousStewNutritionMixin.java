package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.survival.FoodConsumption;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SuspiciousStewItem;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Consume the accepted serving before its own effects can change ingestion eligibility. @author NingMeng123 */
@Mixin(SuspiciousStewItem.class)
public abstract class SuspiciousStewNutritionMixin {
    @Inject(method="finishUsingItem",at=@At("HEAD"),cancellable=true)
    private void mite$foodBeforeEffects(ItemStack stack,Level level,LivingEntity entity,CallbackInfoReturnable<ItemStack> cir) {
        if(entity instanceof Player player && !FoodConsumption.canIngest(player,stack)) {
            cir.setReturnValue(stack);
            return;
        }
        var food=stack.getFoodProperties(entity);
        if(food==null) { cir.setReturnValue(stack); return; }
        // Capture the component while it is still on the food, before a last serving becomes a bowl.
        var effects=stack.getOrDefault(DataComponents.SUSPICIOUS_STEW_EFFECTS,SuspiciousStewEffects.EMPTY);
        ItemStack result=entity.eat(level,stack,food);
        if(!level.isClientSide) for(var entry:effects.effects()) entity.addEffect(entry.createEffectInstance());
        cir.setReturnValue(result);
    }
}
