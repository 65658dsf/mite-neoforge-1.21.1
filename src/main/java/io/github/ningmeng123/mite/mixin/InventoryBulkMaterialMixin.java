package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.crafting.BulkMaterialTransfers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Complete one batch transfer before vanilla consumes the input once. @author NingMeng123 */
@Mixin(InventoryMenu.class)
public abstract class InventoryBulkMaterialMixin {
    @WrapOperation(method = "quickMoveStack", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/world/inventory/InventoryMenu;moveItemStackTo(Lnet/minecraft/world/item/ItemStack;IIZ)Z"), require = 1, allow = 1)
    private boolean mite$moveWholeMaterialBatch(InventoryMenu menu, ItemStack stack, int start, int end, boolean reverse,
            Operation<Boolean> original, Player player, int clickedSlot) {
        boolean bulk = clickedSlot == 0 && BulkMaterialTransfers.isBulkResult(menu, player, stack);
        boolean moved = original.call(menu, stack, start, end, reverse);
        if (bulk) {
            while (!stack.isEmpty()) {
                int before = stack.getCount();
                moved |= original.call(menu, stack, start, end, reverse);
                if (stack.getCount() >= before) break;
            }
        }
        // If no more space exists, vanilla retains its normal no-move or remainder-drop behavior.
        return moved;
    }
}
