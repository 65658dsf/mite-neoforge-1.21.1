package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.crafting.TimedCraftingController;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
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
            if (type == ClickType.PICKUP && button == 1) TimedCraftingController.cancel(serverPlayer);
            else if ((type == ClickType.PICKUP || type == ClickType.QUICK_MOVE) && button == 0) {
                TimedCraftingController.start(serverPlayer, menu, type == ClickType.QUICK_MOVE);
            }
            menu.broadcastChanges();
        }
        ci.cancel();
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void mite$cancelOnClose(Player player, CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer) TimedCraftingController.cancel(serverPlayer);
    }
}
