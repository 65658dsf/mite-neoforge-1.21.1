package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.item.EquipmentQuality;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Carry R196 quality identity and repair ability into the retained modern anvil. @author NingMeng123 */
@Mixin(AnvilMenu.class)
public abstract class QualityAnvilMixin extends ItemCombinerMenu {
    protected QualityAnvilMixin(MenuType<?> type, int id, Inventory inventory, ContainerLevelAccess access) {
        super(type, id, inventory, access);
    }

    @Inject(method = "createResult", at = @At("RETURN"))
    private void mite$preserveQuality(CallbackInfo ci) {
        ItemStack first = inputSlots.getItem(0);
        ItemStack second = inputSlots.getItem(1);
        ItemStack result = resultSlots.getItem(0);
        if (!result.isEmpty()) {
            EquipmentQuality.preserve(first, result);
            if (EquipmentQuality.isManagedCombine(first, second))
                EquipmentQuality.combineWithoutBonus(first, second, result);
        }
        if (!EquipmentQuality.maintenanceAllowed(player, this)) {
            resultSlots.setItem(0, ItemStack.EMPTY);
            ((AnvilMenu)(Object)this).setMaximumCost(0);
            broadcastChanges();
            return;
        }
    }

    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void mite$recheckRepairAbility(Player player, boolean hasItem, CallbackInfoReturnable<Boolean> cir) {
        if (!EquipmentQuality.maintenanceAllowed(player, this)) cir.setReturnValue(false);
    }
}
