package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.item.EquipmentQuality;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevent quality laundering and double-scaled durability through the modern grindstone. @author NingMeng123 */
@Mixin(GrindstoneMenu.class)
public abstract class QualityGrindstoneMixin extends AbstractContainerMenu {
    @Unique private Player mite$owner;
    protected QualityGrindstoneMixin(MenuType<?> type, int id) { super(type, id); }

    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V", at = @At("RETURN"))
    private void mite$rememberOwner(int id, Inventory inventory, ContainerLevelAccess access, CallbackInfo ci) {
        mite$owner = inventory.player;
    }

    @Inject(method = "mergeItems", at = @At("HEAD"), cancellable = true)
    private void mite$matchingQuality(ItemStack first, ItemStack second, CallbackInfoReturnable<ItemStack> cir) {
        if (EquipmentQuality.isManagedCombine(first, second)
                && (!EquipmentQuality.sameQuality(first, second) || !EquipmentQuality.sameColor(first, second)))
            cir.setReturnValue(ItemStack.EMPTY);
    }

    @Inject(method = "mergeItems", at = @At("RETURN"))
    private void mite$unscaledDurability(ItemStack first, ItemStack second, CallbackInfoReturnable<ItemStack> cir) {
        if (!cir.getReturnValue().isEmpty() && EquipmentQuality.isManagedCombine(first, second))
            EquipmentQuality.combineWithoutBonus(first, second, cir.getReturnValue());
    }

    @Inject(method = "createResult", at = @At("RETURN"))
    private void mite$repairAbility(CallbackInfo ci) {
        if (mite$owner != null && !EquipmentQuality.maintenanceAllowed(mite$owner, this)) {
            getSlot(2).set(ItemStack.EMPTY);
            broadcastChanges();
        }
    }

    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void mite$quickMoveAbility(Player player, int slot, CallbackInfoReturnable<ItemStack> cir) {
        if (slot == 2) {
            EquipmentQuality.refreshMaintenance(this);
            if (!EquipmentQuality.maintenanceAllowed(player, this)) cir.setReturnValue(ItemStack.EMPTY);
        }
    }
}
