package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.registry.MiteFoods;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hotbar bait doubles modern wait countdown and is consumed once after an approved catch. @author NingMeng123 */
@Mixin(FishingHook.class)
public abstract class FoodFishingBaitMixin {
    @Shadow public abstract Player getPlayerOwner();
    @Unique private boolean mite$paidBait;
    @Unique private int mite$baitSlot() {
        Player player = getPlayerOwner();
        if (player != null) for (int slot = 0; slot < 9; slot++)
            if (player.getInventory().getItem(slot).is(MiteFoods.get("raw_worm"))) return slot;
        return -1;
    }
    // In pinned 1.21.1 catchingFish, local slot 3 is weather-adjusted countdown
    // speed. Its second LOAD is the waiting phase; do not accelerate the bite window.
    @ModifyVariable(method = "catchingFish", at = @At(value = "LOAD", ordinal = 1), index = 3, require = 1, allow = 1)
    private int mite$baitWaitSpeed(int original) { return mite$baitSlot() >= 0 ? original * 2 : original; }
    @Inject(method = "retrieve", at = @At("HEAD"))
    private void mite$resetCatchBait(ItemStack rod, CallbackInfoReturnable<Integer> cir) { mite$paidBait = false; }
    @Inject(method = "retrieve", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/item/ItemEntity;<init>(Lnet/minecraft/world/level/Level;DDDLnet/minecraft/world/item/ItemStack;)V"))
    private void mite$consumeCatchBait(ItemStack rod, CallbackInfoReturnable<Integer> cir) {
        if (mite$paidBait) return;
        mite$paidBait = true;
        int slot = mite$baitSlot();
        if (slot >= 0) {
            // R196 consumes bait even in creative, independently of food qualification.
            getPlayerOwner().getInventory().getItem(slot).shrink(1);
            getPlayerOwner().getInventory().setChanged();
        }
    }
}
