package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.api.MiteExtensions;
import io.github.ningmeng123.mite.enchantment.R196Enchantments;
import io.github.ningmeng123.mite.item.FoodProfile;
import io.github.ningmeng123.mite.rules.NutritionRules;
import io.github.ningmeng123.mite.survival.DualFoodData;
import io.github.ningmeng123.mite.survival.NutritionState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Food values, saturation-first consumption and natural recovery. @author NingMeng123 */
@Mixin(FoodData.class)
public abstract class FoodDataMixin implements DualFoodData {
    @Shadow private int foodLevel;
    @Shadow private int lastFoodLevel;
    @Shadow private float saturationLevel;
    @Shadow private float exhaustionLevel;
    @Shadow public abstract void addExhaustion(float amount);
    @Unique private Player mite$player;
    @Unique private int mite$nutrition = 20;
    // Retained for legacy saved-data compatibility.
    @Unique private float mite$nutritionHunger;
    @Unique private float mite$healProgress;
    @Unique private float mite$starveProgress;
    @Unique private boolean mite$initialized;

    @Override public void mite$bindPlayer(Player player) {
        mite$player = player;
        if (!mite$initialized) {
            foodLevel = mite$nutrition = mite$limit();
            saturationLevel = 0F;
            mite$initialized = true;
        }
    }
    @Unique private int mite$limit() {
        return mite$player == null ? 20 : MiteExtensions.foodCapacity(mite$player);
    }
    @Override public void mite$clampToLimit() {
        if (mite$player != null && !mite$player.level().isClientSide) {
            foodLevel = Mth.clamp(foodLevel,0,mite$limit());
            mite$nutrition = Mth.clamp(mite$nutrition,0,mite$limit());
            saturationLevel = Mth.clamp(mite$finite(saturationLevel),0F,(float)foodLevel);
        }
    }
    @Override public int mite$getNutrition() { return mite$nutrition; }
    @Override public void mite$setNutrition(int nutrition) {
        mite$nutrition = Math.max(0,nutrition);
        mite$clampToLimit();
    }
    @Override public void mite$ingest(FoodProfile profile) {
        if (profile == null || mite$player == null || mite$player.level().isClientSide) return;
        NutritionState before = NutritionState.get(mite$player);
        int satiation = NutritionRules.adjustedSatiation(profile.satiation(),profile.sugar(),before.resistanceLevel());
        foodLevel = Math.clamp((long)foodLevel+satiation,0,mite$limit());
        mite$nutrition = Math.clamp((long)mite$nutrition+profile.nutrition(),0,mite$limit());
        // The user's wheat-seed saturation buffer stays distinct from R196 nutrition.
        saturationLevel = Mth.clamp(saturationLevel,0F,(float)foodLevel);
        NutritionState after = before.ingest(profile);
        NutritionState.set(mite$player,after);
        int response = NutritionRules.addedResistance(profile.sugar());
        if (response>0 && after.resistanceLevel()>=0) {
            mite$player.addEffect(new MobEffectInstance(MobEffects.CONFUSION,400,after.resistanceLevel()));
            if (after.resistanceLevel()==2)
                mite$player.addEffect(new MobEffectInstance(MobEffects.POISON,Math.max((int)(response/48F),100),0));
        }
    }
    @ModifyConstant(method="add(IF)V",constant=@Constant(intValue=20),require=1,allow=1)
    private int mite$foodAdditionLimit(int original) { return mite$limit(); }
    @ModifyConstant(method="needsFood()Z",constant=@Constant(intValue=20),require=1,allow=1)
    private int mite$fullFoodLimit(int original) { return mite$limit(); }

    // Other mods may add food without an item. Give the explicit modern value to both reserves.
    @Inject(method="eat(Lnet/minecraft/world/food/FoodProperties;)V",at=@At("TAIL"))
    private void mite$legacyFoodProperties(FoodProperties food,CallbackInfo ci) {
        if (mite$player != null && !mite$player.level().isClientSide)
            mite$setNutrition(Math.clamp((long)mite$nutrition+food.nutrition(),0,mite$limit()));
    }
    @Inject(method="eat(IF)V",at=@At("TAIL"))
    private void mite$legacyIntegerFood(int food,float saturation,CallbackInfo ci) {
        if (mite$player != null && !mite$player.level().isClientSide)
            mite$setNutrition(Math.clamp((long)mite$nutrition+food,0,mite$limit()));
    }

    @Inject(method="tick",at=@At("HEAD"),cancellable=true)
    private void mite$sourceMetabolism(Player player,CallbackInfo ci) {
        ci.cancel();
        mite$bindPlayer(player);
        mite$clampToLimit();
        if (player.level().isClientSide || !player.isAlive() || player.isSpectator()) return;
        lastFoodLevel = foodLevel;
        NutritionState state = NutritionState.get(player);
        if (!player.isCreative()) {
            state = state.tick(player.isSprinting());
            NutritionState.set(player,state);
        }
        if (!player.getAbilities().invulnerable && !player.isCreative()) {
            var pos = player.blockPosition();
            int immersion = player.level().getFluidState(pos.above()).is(FluidTags.WATER) ? 2
                    : player.level().getFluidState(pos).is(FluidTags.WATER) ? 1 : 0;
            float multiplier = NutritionRules.metabolicMultiplier(player.level().isRainingAt(pos),
                    player.level().isThundering(),immersion,
                    player.level().getBiome(pos).value().getBaseTemperature(),state.malnourished());
            addExhaustion(.002F*multiplier);
        }
        if (exhaustionLevel >= 4F) {
            exhaustionLevel -= 4F;
            if (saturationLevel>0F) {
                // Modern saturation absorbs exhaustion, without enabling modern fast healing.
                saturationLevel=Math.max(0F,saturationLevel-1F);
            } else {
                foodLevel = Math.max(0, foodLevel - 1);
                if (mite$nutrition > 0) {
                    mite$nutrition--;
                    mite$nutritionHunger = 0F;
                }
            }
        }
        boolean hasEnergy = foodLevel > 0 || mite$nutrition > 0;
        if (!hasEnergy) {
            mite$starveProgress += .002F;
            if (mite$starveProgress>=1F) {
                if (NutritionRules.starvationCanDamage(player.getHealth(),player.level().getDifficulty().getId()))
                    player.hurt(player.damageSources().starve(),1F);
                mite$starveProgress-=1F;
                mite$nutritionHunger=0F;
            }
        } else {
            mite$starveProgress=0F;
        }
        if (!hasEnergy) {
            mite$healProgress = 0F;
            return;
        }
        int regenerationLevel = R196Enchantments.equipped(player, "regeneration");
        float increment = NutritionRules.regenerationIncrement(mite$nutrition, saturationLevel,
                state.malnourished(), player.isSleeping(), regenerationLevel);
        mite$healProgress += MiteExtensions.policy().naturalRegeneration(player, increment);
        if (!player.level().getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION) || !player.isHurt()) {
            mite$healProgress = 0F;
        } else if (mite$healProgress >= 1F) {
            player.heal(1F);
            if (!player.getAbilities().invulnerable) addExhaustion(1F);
            mite$healProgress -= 1F;
        }
    }
    // Apply the requested 15% increase once, including metabolism and regeneration.
    @ModifyVariable(method="addExhaustion(F)V",at=@At("HEAD"),argsOnly=true,ordinal=0,require=1,allow=1)
    private float mite$increaseHungerRate(float amount) { return amount*1.15F; }
    @Inject(method="addExhaustion",at=@At("HEAD"),cancellable=true)
    private void mite$ignoreInvulnerableHunger(float amount,CallbackInfo ci) {
        if(mite$player!=null && (mite$player.isCreative() || mite$player.isSpectator() || mite$player.getAbilities().invulnerable))
            ci.cancel();
    }
    @Inject(method="setFoodLevel",at=@At("TAIL"))
    private void mite$clampFoodSetter(int value,CallbackInfo ci) { mite$clampToLimit(); }
    @Inject(method="setSaturation",at=@At("TAIL"))
    private void mite$clampSaturationSetter(float value,CallbackInfo ci) { mite$clampToLimit(); }
    @Inject(method="addAdditionalSaveData",at=@At("TAIL"))
    private void mite$saveNutrition(CompoundTag tag,CallbackInfo ci) {
        tag.putInt("mite_food_version",1);
        tag.putInt("mite_nutrition",mite$nutrition);
        tag.putFloat("mite_nutrition_hunger",mite$nutritionHunger);
        tag.putFloat("mite_heal_progress",mite$healProgress);
        tag.putFloat("mite_starve_progress",mite$starveProgress);
    }
    @Inject(method="readAdditionalSaveData",at=@At("TAIL"))
    private void mite$loadNutrition(CompoundTag tag,CallbackInfo ci) {
        // No clamp before XP loading. Legacy nutrition starts from saved food, not saturation.
        mite$initialized=true;
        mite$nutrition=Math.max(0,tag.contains("mite_nutrition",99)?tag.getInt("mite_nutrition"):foodLevel);
        mite$nutritionHunger=mite$finite(tag.getFloat("mite_nutrition_hunger"));
        mite$healProgress=mite$finite(tag.getFloat("mite_heal_progress"));
        mite$starveProgress=mite$finite(tag.getFloat("mite_starve_progress"));
        exhaustionLevel=mite$finite(exhaustionLevel);
    }
    @Unique private static float mite$finite(float value) {
        return Float.isFinite(value)?Math.clamp(value,0F,40F):0F;
    }
}
