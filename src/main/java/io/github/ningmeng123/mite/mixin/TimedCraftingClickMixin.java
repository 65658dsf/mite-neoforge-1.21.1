package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.crafting.TimedCraftingController;
import io.github.ningmeng123.mite.crafting.TimedSmithingController;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Client prediction and server intent use the same restricted recipe set. @author NingMeng123 */
@Mixin(AbstractContainerMenu.class)
public abstract class TimedCraftingClickMixin {
    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void mite$startManufacturing(int slot, int button, ClickType type, Player player, CallbackInfo ci) {
        AbstractContainerMenu menu = (AbstractContainerMenu)(Object)this;
        if (slot != 0 || !TimedCraftingController.isTimed(player, menu)) return;
        if (player instanceof ServerPlayer serverPlayer) {
            if (type == ClickType.PICKUP && button == 1) {
                if (TimedCraftingController.isActive(serverPlayer)) TimedCraftingController.cancel(serverPlayer);
                else TimedCraftingController.selectQuality(serverPlayer, menu, 1);
            }
            else if ((type == ClickType.PICKUP || type == ClickType.QUICK_MOVE) && button == 0) {
                TimedCraftingController.start(serverPlayer, menu, type == ClickType.QUICK_MOVE);
            }
            menu.broadcastChanges();
        }
        ci.cancel();
    }

    @WrapOperation(method = "doClick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;canTakeItemForPickAll(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/inventory/Slot;)Z"),
            require = 1, allow = 1)
    private boolean mite$keepActiveInputsDuringBackpackCollect(AbstractContainerMenu menu, ItemStack stack, Slot candidate,
            Operation<Boolean> original, int clickedSlot, int button, ClickType type, Player player) {
        // A double-click scans the whole menu, even when it started in the backpack.
        if (player instanceof ServerPlayer server && menu == player.containerMenu
                && candidate.container != player.getInventory() && menu.getSlot(clickedSlot).container == player.getInventory()
                && (TimedCraftingController.isActive(server) || TimedSmithingController.isActive(server))) return false;
        return original.call(menu, stack, candidate);
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void mite$cancelOnClose(Player player, CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer) TimedCraftingController.forget(serverPlayer);
    }
}
