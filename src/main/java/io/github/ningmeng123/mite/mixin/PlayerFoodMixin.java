package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.survival.LeveledFoodData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
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
}
