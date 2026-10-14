package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Preserve an oversized pickup's remainder when the native empty-slot path clamps its accepted portion. @author NingMeng123 */
@Mixin(HopperBlockEntity.class)
public abstract class HopperStackTransferMixin {
    @WrapOperation(method = "tryMoveInItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;getMaxStackSize()I"))
    private static int mite$respectOccupiedContainerCapacity(ItemStack incoming, Operation<Integer> original,
            @Local(argsOnly = true, ordinal = 1) Container destination, @Local(argsOnly = true) int slot) {
        // The native subtraction must never become negative for a retained legacy stack.
        return Math.max(destination.getItem(slot).getCount(), destination.getMaxStackSize(incoming));
    }

    @WrapOperation(method = "tryMoveInItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/Container;setItem(ILnet/minecraft/world/item/ItemStack;)V"))
    private static void mite$splitEmptySlotTransfer(Container destination, int slot, ItemStack incoming,
            Operation<Void> original, @Share("miteOverflow") LocalRef<ItemStack> overflow) {
        int capacity = Math.max(0, destination.getMaxStackSize(incoming));
        if (incoming.getCount() > capacity) {
            ItemStack accepted = incoming.split(capacity);
            overflow.set(incoming);
            original.call(destination, slot, accepted);
        } else original.call(destination, slot, incoming);
    }

    @ModifyReturnValue(method = "tryMoveInItem", at = @At("RETURN"))
    private static ItemStack mite$keepUnacceptedItems(ItemStack nativeRemainder,
            @Share("miteOverflow") LocalRef<ItemStack> overflow) {
        return overflow.get() == null ? nativeRemainder : overflow.get();
    }
}
