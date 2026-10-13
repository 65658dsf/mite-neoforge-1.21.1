package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.survival.SourceCookingExperience;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Snapshot removeCount before vanilla clears it; a second quick-move callback has count zero. @author NingMeng123 */
@Mixin(FurnaceResultSlot.class)
public abstract class FoodFurnaceResultExperienceMixin extends Slot {
    @Shadow @Final private Player player;
    @Shadow private int removeCount;

    protected FoodFurnaceResultExperienceMixin(Container container, int slot, int x, int y) {
        super(container, slot, x, y);
    }

    /** The hotbar swap path moves the original stack without calling remove or onQuickCraft. */
    @Override protected void onSwapCraft(int count) {
        if (SourceCookingExperience.isSourceResult(getItem())) removeCount += count;
    }

    @Inject(method = "checkTakeAchievements", at = @At("HEAD"))
    private void mite$rewardActuallyTakenFood(ItemStack result, CallbackInfo ci) {
        SourceCookingExperience.awardTaken(player, result, removeCount);
    }
}
