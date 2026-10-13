package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.crafting.TimedCraftingController;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** No cursor, hotbar, throw or double-click path can take an unfinished result. @author NingMeng123 */
@Mixin(Slot.class)
public abstract class TimedCraftingPickupMixin {
    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void mite$denyInstantPickup(Player player, CallbackInfoReturnable<Boolean> cir) {
        Slot slot = (Slot)(Object)this;
        if (slot == player.inventoryMenu.getSlot(0) && TimedCraftingController.isTimed(player, player.inventoryMenu)
                || player.containerMenu instanceof CraftingMenu && slot == player.containerMenu.getSlot(0)
                && TimedCraftingController.isTimed(player, player.containerMenu)) cir.setReturnValue(false);
    }
}
