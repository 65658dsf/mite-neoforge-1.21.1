package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.rules.ProgressionRules;
import io.github.ningmeng123.mite.survival.LeveledFoodData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Level capacity at the shared food mutation and query points. @author NingMeng123 */
@Mixin(FoodData.class)
public abstract class FoodDataMixin implements LeveledFoodData {
    @Shadow private int foodLevel;
    @Shadow private float saturationLevel;
    @Unique private Player mite$player;

    @Override
    public void mite$bindPlayer(Player player) {
        mite$player = player;
    }

    @Unique
    private int mite$limit() {
        return mite$player == null ? 20 : ProgressionRules.foodLimitForLevel(mite$player.experienceLevel);
    }

    @Override
    public void mite$clampToLimit() {
        // The server sends food before XP. Clamping packet setters on the client
        // would permanently discard valid food while the previous level is known.
        if (mite$player != null && !mite$player.level().isClientSide) {
            foodLevel = Mth.clamp(foodLevel, 0, mite$limit());
            saturationLevel = Mth.clamp(saturationLevel, 0.0F, (float) foodLevel);
        }
    }

    @ModifyConstant(method = "add(IF)V", constant = @Constant(intValue = 20), require = 1, allow = 1)
    private int mite$foodAdditionLimit(int original) {
        return mite$limit();
    }

    @ModifyConstant(method = "needsFood()Z", constant = @Constant(intValue = 20), require = 1, allow = 1)
    private int mite$fullFoodLimit(int original) {
        return mite$limit();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void mite$applyCapacityBeforeMetabolism(Player player, CallbackInfo ci) {
        mite$bindPlayer(player);
        mite$clampToLimit();
    }

    // Temporary modern-food behavior: retain vanilla regen timing with thresholds
    // relative to capacity. R196 dual nutrition and metabolism are separate work.
    @ModifyConstant(method = "tick", constant = @Constant(intValue = 20), require = 1, allow = 1)
    private int mite$fullFoodRegeneration(int original) {
        return mite$limit();
    }

    @ModifyConstant(method = "tick", constant = @Constant(intValue = 18), require = 1, allow = 1)
    private int mite$slowRegenerationThreshold(int original) {
        return mite$limit() - 2;
    }

    @Inject(method = "setFoodLevel", at = @At("TAIL"))
    private void mite$clampFoodSetter(int value, CallbackInfo ci) {
        mite$clampToLimit();
    }

    @Inject(method = "setSaturation", at = @At("TAIL"))
    private void mite$clampSaturationSetter(float value, CallbackInfo ci) {
        mite$clampToLimit();
    }
}
