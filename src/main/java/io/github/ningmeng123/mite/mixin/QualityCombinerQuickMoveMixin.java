package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.item.EquipmentQuality;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** ItemCombinerMenu.quickMoveStack can be called directly without Slot.mayPickup. @author NingMeng123 */
@Mixin(ItemCombinerMenu.class)
public abstract class QualityCombinerQuickMoveMixin {
    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void mite$recheckAnvil(Player player, int slot, CallbackInfoReturnable<ItemStack> cir) {
        ItemCombinerMenu menu = (ItemCombinerMenu)(Object)this;
        if (menu instanceof AnvilMenu && slot == menu.getResultSlot()) {
            EquipmentQuality.refreshMaintenance(menu);
            if (!EquipmentQuality.maintenanceAllowed(player, menu) || !menu.getSlot(slot).mayPickup(player))
                cir.setReturnValue(ItemStack.EMPTY);
        }
    }
}
