package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.survival.CowFoodState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Modern mushroom soups retained at one source milk volume per bowl, with wellness/adult gates. @author NingMeng123 */
@Mixin(MushroomCow.class)
public abstract class Livestock032MooshroomMixin {
    @Inject(method="mobInteract",at=@At("HEAD"),cancellable=true)
    private void mite$boundedStew(Player player,InteractionHand hand,CallbackInfoReturnable<InteractionResult> cir) {
        MushroomCow cow=(MushroomCow)(Object)this;
        if(!player.getItemInHand(hand).is(Items.BOWL)) return;
        CowFoodState state=CowFoodState.get(cow);
        if(cow.isBaby() || !state.well() || cow.getHealth()<cow.getMaxHealth() || state.milk()<25) { cir.setReturnValue(InteractionResult.FAIL); return; }
        if(!cow.level().isClientSide) CowFoodState.set(cow,state.milk(state.milk()-25));
    }
}
