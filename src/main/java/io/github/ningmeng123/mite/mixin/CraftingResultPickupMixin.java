package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.crafting.CraftingAccessRules;
import io.github.ningmeng123.mite.crafting.WorkbenchRules;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Reject stale inventory results before any item reaches the cursor. @author NingMeng123 */
@Mixin(Slot.class)
public abstract class CraftingResultPickupMixin {
    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void mite$checkInventoryResult(Player player, CallbackInfoReturnable<Boolean> cir) {
        Slot slot = (Slot) (Object) this;
        if ((slot == player.inventoryMenu.getSlot(0)
                || player.containerMenu instanceof CraftingMenu && slot == player.containerMenu.getSlot(0))
                && !WorkbenchRules.allows(slot == player.inventoryMenu.getSlot(0) ? player.inventoryMenu : player.containerMenu,
                        slot.getItem(), player.level())) {
            cir.setReturnValue(false);
            return;
        }
        if (slot == player.inventoryMenu.getSlot(0)
                && !CraftingAccessRules.canCraft(player.inventoryMenu.getCraftSlots(), slot.getItem())) {
            cir.setReturnValue(false);
        }
    }
}
