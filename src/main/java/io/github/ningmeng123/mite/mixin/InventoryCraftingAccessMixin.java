package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.crafting.CraftingAccessRules;
import io.github.ningmeng123.mite.crafting.WorkbenchRules;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Direct quick-move callers must also obey equipment requirements. @author NingMeng123 */
@Mixin(InventoryMenu.class)
public abstract class InventoryCraftingAccessMixin {
    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void mite$checkQuickMove(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
        InventoryMenu menu = (InventoryMenu) (Object) this;
        if (index == 0 && (!CraftingAccessRules.canCraft(menu.getCraftSlots(), menu.getSlot(0).getItem())
                || !WorkbenchRules.allows(menu, menu.getSlot(0).getItem(), player.level()))) {
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }
}
