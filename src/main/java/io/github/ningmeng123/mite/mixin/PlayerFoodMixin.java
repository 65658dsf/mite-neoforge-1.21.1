package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.survival.LeveledFoodData;
import io.github.ningmeng123.mite.survival.DualFoodData;
import io.github.ningmeng123.mite.survival.FoodConsumption;
import io.github.ningmeng123.mite.registry.MiteFoodCatalog;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Binds both logical sides without changing data during NBT loading. @author NingMeng123 */
@Mixin(Player.class)
public abstract class PlayerFoodMixin {
    @Shadow protected FoodData foodData;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void mite$bindInitialFoodData(CallbackInfo ci) {
        ((LeveledFoodData) foodData).mite$bindPlayer((Player) (Object) this);
    }

    @Inject(method = "getFoodData", at = @At("RETURN"))
    private void mite$bindCurrentFoodData(CallbackInfoReturnable<FoodData> cir) {
        // ServerPlayer.restoreFrom may reuse the previous player's FoodData.
        ((LeveledFoodData) cir.getReturnValue()).mite$bindPlayer((Player) (Object) this);
    }

    @Inject(method="eat",at=@At("HEAD"),cancellable=true)
    private void mite$validateEating(Level level,ItemStack stack,FoodProperties food,CallbackInfoReturnable<ItemStack> cir) {
        if(!FoodConsumption.canIngest((Player)(Object)this,stack)) cir.setReturnValue(stack);
    }

    @Redirect(method="eat",at=@At(value="INVOKE",target="Lnet/minecraft/world/food/FoodData;eat(Lnet/minecraft/world/food/FoodProperties;)V"))
    private void mite$ingestSourceValues(FoodData data,FoodProperties food,Level level,ItemStack stack,FoodProperties properties) {
        var profile = MiteFoodCatalog.profile(stack);
        if(profile==null) { data.eat(food); return; }
        ((DualFoodData)data).mite$ingest(profile);
        if(!level.isClientSide && stack.is(Items.WHEAT_SEEDS)) {
            data.setSaturation(data.getSaturationLevel()+food.saturation());
        }
    }
}
