package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.furnace.ThermalDataSlot;
import io.github.ningmeng123.mite.survival.GrindstoneExperienceAccess;
import io.github.ningmeng123.mite.survival.GrindstoneExperienceEvents;
import io.github.ningmeng123.mite.survival.GrindstoneResultExperienceAccess;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fixes and synchronizes the quoted fee, then checks it before any result transfer. @author NingMeng123 */
@Mixin(GrindstoneMenu.class)
public abstract class GrindstoneExperienceMixin extends AbstractContainerMenu implements GrindstoneExperienceAccess {
    @Shadow private int xp;
    @Unique private Player mite$experienceOwner;
    @Unique private ItemStack mite$quotedTop = ItemStack.EMPTY;
    @Unique private ItemStack mite$quotedBottom = ItemStack.EMPTY;
    @Unique private int mite$quotedReward = -1;
    @Unique private int mite$experienceCost = -1;
    @Unique private final int[] mite$costWords = {0xFFFF, 0xFFFF};

    protected GrindstoneExperienceMixin(MenuType<?> type, int id) { super(type, id); }

    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V", at = @At("RETURN"))
    private void mite$initializeExperience(int id, Inventory inventory, ContainerLevelAccess access, CallbackInfo ci) {
        mite$experienceOwner = inventory.player;
        // Native container data packets carry signed shorts; preserve the entire point cost.
        for (int word = 0; word < 2; word++) {
            final int index = word;
            addDataSlot(new ThermalDataSlot(
                    () -> (mite$experienceCost >>> (index * 16)) & 0xFFFF,
                    value -> {
                        mite$costWords[index] = value & 0xFFFF;
                        mite$experienceCost = mite$costWords[0] | (mite$costWords[1] << 16);
                    }));
        }
    }

    // Quality maintenance can hide an invalid repair. Quote only after that check has run.
    @Inject(method = "createResult", at = @At("RETURN"), order = 1100)
    private void mite$quoteExperience(CallbackInfo ci) {
        if (mite$experienceOwner == null || mite$experienceOwner.level().isClientSide()) return;
        ItemStack top = getSlot(0).getItem(), bottom = getSlot(1).getItem();
        if (!ItemStack.matches(top, mite$quotedTop) || !ItemStack.matches(bottom, mite$quotedBottom)) {
            mite$quotedTop = top.copy();
            mite$quotedBottom = bottom.copy();
            mite$quotedReward = -1;
        }
        if (getSlot(2).hasItem()) {
            if (mite$quotedReward < 0) {
                mite$quotedReward = Math.max(0, ((GrindstoneResultExperienceAccess)getSlot(2))
                        .mite$rollExperience(mite$experienceOwner.level()));
            }
            // createResult resets vanilla's xp field. Restore the quote even on ability refreshes.
            xp = mite$quotedReward;
            mite$experienceCost = GrindstoneExperienceEvents.experienceCost(mite$quotedReward);
        } else {
            mite$experienceCost = -1;
        }
        broadcastChanges();
    }

    @Override public int mite$experienceCost() { return mite$experienceCost; }

    @Override public boolean mite$canTake(Player player) {
        return mite$experienceCost == 0 || mite$experienceCost > 0 && player.totalExperience >= mite$experienceCost;
    }

    // GrindstoneMenu.quickMoveStack does not call Slot.mayPickup itself.
    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true, order = 1100)
    private void mite$checkExperienceBeforeQuickMove(Player player, int slot, CallbackInfoReturnable<ItemStack> cir) {
        if (slot == 2 && !mite$canTake(player)) cir.setReturnValue(ItemStack.EMPTY);
    }
}
