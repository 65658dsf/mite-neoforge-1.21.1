package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.item.EquipmentQuality;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.GrindstoneMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Refresh repair ability before every result extraction, including number-key and shift clicks. @author NingMeng123 */
@Mixin(AbstractContainerMenu.class)
public abstract class QualityMaintenanceClickMixin {
    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void mite$refreshMaintenance(int slot, int button, ClickType type, Player player, CallbackInfo ci) {
        AbstractContainerMenu menu = (AbstractContainerMenu)(Object)this;
        if (slot != 2 || !(menu instanceof AnvilMenu || menu instanceof GrindstoneMenu)) return;
        EquipmentQuality.refreshMaintenance(menu);
        if (!EquipmentQuality.maintenanceAllowed(player, menu)) ci.cancel();
    }
}
