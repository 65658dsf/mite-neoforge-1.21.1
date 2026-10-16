package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.world.DiamondEquipmentRemoval;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Clear command-inserted or old container items before interaction and synchronization. @author NingMeng123 */
@Mixin(AbstractContainerMenu.class)
public abstract class RemovedDiamondMenuMixin {
    @Unique private boolean mite$cleaningRetiredEquipment;
    @Inject(method = {"clicked", "broadcastChanges", "sendAllDataToRemote"}, at = @At("HEAD"))
    private void mite$clearRetiredEquipment(CallbackInfo ci) {
        if (mite$cleaningRetiredEquipment) return;
        mite$cleaningRetiredEquipment = true;
        try { DiamondEquipmentRemoval.cleanMenu((AbstractContainerMenu)(Object)this); }
        finally { mite$cleaningRetiredEquipment = false; }
    }
}
