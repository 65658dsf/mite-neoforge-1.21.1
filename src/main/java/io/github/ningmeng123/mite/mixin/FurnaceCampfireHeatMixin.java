package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.campfire.CampfireController;
import io.github.ningmeng123.mite.campfire.CampfireFuelAccess;
import io.github.ningmeng123.mite.campfire.CampfireRules;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Preserve native visuals while making campfire fuel and cooking server authoritative. @author NingMeng123 */
@Mixin(CampfireBlockEntity.class)
public abstract class FurnaceCampfireHeatMixin implements CampfireFuelAccess {
    @Shadow @Final private int[] cookingProgress;
    @Shadow @Final private int[] cookingTime;
    @Unique private int mite$fuel;
    @Unique private int mite$remaining;
    @Unique private int mite$cooked;

    @Override public int mite$fuelPoints() { return mite$fuel; }
    @Override public int mite$fuelTicks() { return mite$remaining; }
    @Override public int mite$cookedMeat() { return mite$cooked; }
    @Override public void mite$setFuelPoints(int value) { mite$fuel = value; }
    @Override public void mite$setFuelTicks(int value) { mite$remaining = value; }
    @Override public void mite$setCookedMeat(int value) { mite$cooked = value; }
    @Override public int[] mite$cookingProgress() { return cookingProgress; }
    @Override public int[] mite$cookingTime() { return cookingTime; }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void mite$initialFuel(BlockPos pos, BlockState state, CallbackInfo ci) {
        // Natural and preexisting fires receive one initial reserve. Item placement starts unlit.
        mite$fuel = state.getValue(CampfireBlock.LIT) ? CampfireRules.MAX_FUEL : 0;
        mite$remaining = mite$fuel > 0 ? CampfireRules.FUEL_TICKS : 0;
    }

    @Inject(method = "getCookableRecipe", at = @At("HEAD"), cancellable = true)
    private void mite$meatRecipe(ItemStack input, CallbackInfoReturnable<Optional<RecipeHolder<CampfireCookingRecipe>>> cir) {
        cir.setReturnValue(CampfireController.recipe((CampfireBlockEntity) (Object) this, input));
    }

    @Inject(method = "placeFood", at = @At("HEAD"), cancellable = true)
    private void mite$insertMeat(LivingEntity entity, ItemStack input, int time, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(CampfireController.placeFood((CampfireBlockEntity) (Object) this, entity, input));
    }

    @Inject(method = {"cookTick", "cooldownTick"}, at = @At("HEAD"), cancellable = true)
    private static void mite$tickFuel(Level level, BlockPos pos, BlockState state, CampfireBlockEntity fire, CallbackInfo ci) {
        CampfireController.tick(level, pos, state, fire);
        ci.cancel();
    }

    @Inject(method = "dowse", at = @At("HEAD"))
    private void mite$returnRawFood(CallbackInfo ci) {
        CampfireController.extinguish((CampfireBlockEntity) (Object) this);
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void mite$saveFuel(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) { mite$writeFuel(tag); }

    @Inject(method = "getUpdateTag", at = @At("RETURN"))
    private void mite$syncFuel(HolderLookup.Provider registries, CallbackInfoReturnable<CompoundTag> cir) { mite$writeFuel(cir.getReturnValue()); }

    @Unique private void mite$writeFuel(CompoundTag tag) {
        CompoundTag data = new CompoundTag();
        data.putInt("Version", 1);
        data.putInt("Points", mite$fuel);
        data.putInt("Ticks", mite$remaining);
        data.putInt("Cooked", mite$cooked);
        tag.put("MiteCampfire", data);
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void mite$loadFuel(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        if (!tag.contains("MiteCampfire", CompoundTag.TAG_COMPOUND)) return;
        CompoundTag data = tag.getCompound("MiteCampfire");
        mite$fuel = Mth.clamp(data.getInt("Points"), 0, CampfireRules.MAX_FUEL);
        mite$remaining = mite$fuel > 0 ? Mth.clamp(data.getInt("Ticks"), 1, CampfireRules.FUEL_TICKS) : 0;
        mite$cooked = mite$fuel > 0 ? Mth.clamp(data.getInt("Cooked"), 0, CampfireRules.MEAT_PER_FUEL - 1) : 0;
    }
}
