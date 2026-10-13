package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.survival.EggThrowControl;
import io.github.ningmeng123.mite.survival.FoodConsumption;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.EggItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Eat by default; only the validated Ctrl request reaches native egg throwing. @author NingMeng123 */
@Mixin(EggItem.class)
public abstract class EggFoodMixin {
    @Inject(method="use",at=@At("HEAD"),cancellable=true)
    private void mite$eatEgg(Level level,Player player,InteractionHand hand,CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        if(EggThrowControl.isThrowing(player)) return;
        ItemStack stack=player.getItemInHand(hand);
        if(!FoodConsumption.canIngest(player,stack)) cir.setReturnValue(InteractionResultHolder.fail(stack));
        else {
            player.startUsingItem(hand);
            cir.setReturnValue(InteractionResultHolder.consume(stack));
        }
    }
}
