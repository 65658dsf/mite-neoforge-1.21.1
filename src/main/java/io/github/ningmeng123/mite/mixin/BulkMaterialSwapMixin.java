package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.crafting.BulkMaterialTransfers;
import io.github.ningmeng123.mite.crafting.TimedCraftingController;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Native result-slot hotbar/offhand swaps must not persist an oversized batch. @author NingMeng123 */
@Mixin(AbstractContainerMenu.class)
public abstract class BulkMaterialSwapMixin {
    @WrapOperation(method = "doClick", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/world/entity/player/Inventory;setItem(ILnet/minecraft/world/item/ItemStack;)V"), require = 1, allow = 1)
    private void mite$splitMaterialBatchOnSwap(Inventory inventory, int targetSlot, ItemStack result, Operation<Void> original,
            int clickedSlot, int button, ClickType clickType, Player player) {
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        if (clickType != ClickType.SWAP || clickedSlot != 0 || !inventory.getItem(targetSlot).isEmpty()
                || !BulkMaterialTransfers.isBulkResult(menu, player, result)) {
            original.call(inventory, targetSlot, result);
            return;
        }
        int firstCount = Math.min(result.getCount(), result.getMaxStackSize());
        original.call(inventory, targetSlot, result.copyWithCount(firstCount));
        ItemStack remainder = result.copyWithCount(result.getCount() - firstCount);
        TimedCraftingController.insertWithoutDiscard(inventory, remainder);
        while (!remainder.isEmpty()) player.drop(remainder.split(Math.min(remainder.getCount(), remainder.getMaxStackSize())), false);
        // Keep the complete result/count for vanilla's one onTake and its actual batch statistics.
    }
}
