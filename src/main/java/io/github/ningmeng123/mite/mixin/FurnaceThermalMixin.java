package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.furnace.FurnaceThermalAccess;
import io.github.ningmeng123.mite.furnace.FurnaceThermalController;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** One server state machine for the source furnaces and modern furnace variants. @author NingMeng123 */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class FurnaceThermalMixin implements FurnaceThermalAccess {
    @Shadow int litTime;
    @Shadow int litDuration;
    @Shadow int cookingProgress;
    @Shadow int cookingTotalTime;
    @Shadow @Final private RecipeType<? extends AbstractCookingRecipe> recipeType;
    @Shadow @Final private RecipeManager.CachedCheck<SingleRecipeInput, ? extends AbstractCookingRecipe> quickCheck;
    @Unique private int mite$heat;
    public int mite$getHeat() { return mite$heat; }
    public void mite$setHeat(int value) { mite$heat = value; }
    public int mite$getBurn() { return litTime; }
    public void mite$setBurn(int value) { litTime = value; }
    public int mite$getDuration() { return litDuration; }
    public void mite$setDuration(int value) { litDuration = value; }
    public int mite$getProgress() { return cookingProgress; }
    public void mite$setProgress(int value) { cookingProgress = value; }
    public void mite$setTotal(int value) { cookingTotalTime = value; }
    public RecipeType<? extends AbstractCookingRecipe> mite$recipeType() { return recipeType; }
    public RecipeHolder<? extends AbstractCookingRecipe> mite$recipe(Level level, ItemStack input) {
        return quickCheck.getRecipeFor(new SingleRecipeInput(input), level).orElse(null);
    }
    @WrapMethod(method = "setItem")
    private void mite$sourceInputProgress(int slot, ItemStack stack, Operation<Void> original) {
        var profile = FurnaceThermalController.profile(((AbstractFurnaceBlockEntity)(Object)this).getBlockState());
        int progress = cookingProgress;
        original.call(slot, stack);
        if (profile != null && slot == 0) { cookingProgress = progress; cookingTotalTime = profile.cycleTicks(); }
    }
    @Inject(method = "serverTick", at = @At("HEAD"), cancellable = true)
    private static void mite$tick(Level level, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity furnace, CallbackInfo ci) {
        if (FurnaceThermalController.tick(level, pos, state, furnace)) ci.cancel();
    }
    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void mite$save(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        var furnace = (AbstractFurnaceBlockEntity)(Object)this;
        if (FurnaceThermalController.profile(furnace.getBlockState()) == null) return;
        tag.putInt("BurnTime", litTime); tag.putInt("CookTime", cookingProgress);
        tag.putInt("CurrentItemBurnTime", litDuration);
        tag.putInt("mite:heat_level", mite$heat); tag.putInt("mite:furnace_version", 1);
    }
    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void mite$load(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        var furnace = (AbstractFurnaceBlockEntity)(Object)this;
        var profile = FurnaceThermalController.profile(furnace.getBlockState());
        if (profile == null) return;
        if (tag.getInt("mite:furnace_version") == 1) {
            litTime = Math.max(0, tag.getInt("BurnTime"));
            litDuration = Math.max(0, tag.getInt("CurrentItemBurnTime"));
            cookingProgress = Math.clamp(tag.getInt("CookTime"), 0, profile.cycleTicks() - 1);
            mite$heat = Math.clamp(tag.getInt("mite:heat_level"), 0, profile.maxHeat());
            if (mite$heat == 0 || litDuration == 0 || litTime > litDuration) { litTime = 0; cookingProgress = 0; }
        } else { // Old vanilla saves do not identify the spent fuel. Preserve items, discard unverified fire/progress.
            litTime = 0; litDuration = 0; cookingProgress = 0; mite$heat = 0;
        }
        cookingTotalTime = profile.cycleTicks();
    }
    @Inject(method = "canPlaceItem", at = @At("HEAD"), cancellable = true)
    private void mite$place(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        var furnace = (AbstractFurnaceBlockEntity)(Object)this;
        var profile = FurnaceThermalController.profile(furnace.getBlockState());
        if (profile == null) return;
        cir.setReturnValue(slot == 0 ? furnace.getLevel() != null && FurnaceThermalController.canInput(furnace.getLevel(), recipeType, profile, stack)
                : slot == 1 && FurnaceThermalController.canInsertFuel(profile, stack, furnace.getItem(0)));
    }
    @Inject(method = "canTakeItemThroughFace", at = @At("HEAD"), cancellable = true)
    private void mite$take(int slot, ItemStack stack, Direction face, CallbackInfoReturnable<Boolean> cir) {
        var furnace = (AbstractFurnaceBlockEntity)(Object)this;
        if (FurnaceThermalController.profile(furnace.getBlockState()) != null && face == Direction.DOWN && slot == 1)
            cir.setReturnValue(FurnaceThermalController.emptyBucket(stack));
    }
}
