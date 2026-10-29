package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.crafting.TimedSmithingController;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.SmithingMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** All result-click modes are intent only until the authoritative upgrade completes. @author NingMeng123 */
@Mixin(AbstractContainerMenu.class)
public abstract class TimedSmithingClickMixin {
    @Inject(method = "broadcastChanges", at = @At("HEAD"))
    private void mite$refreshInventoryFundedUpgrade(CallbackInfo ci) {
        AbstractContainerMenu menu = (AbstractContainerMenu)(Object)this;
        if (TimedSmithingController.applies(menu) && !TimedSmithingController.completing(menu)) ((SmithingMenu)menu).createResult();
    }

    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void mite$timeUpgrade(int slot, int button, ClickType type, Player player, CallbackInfo ci) {
        AbstractContainerMenu menu = (AbstractContainerMenu)(Object)this;
        if (slot != SmithingMenu.RESULT_SLOT || !TimedSmithingController.applies(menu)) return;
        if (player instanceof ServerPlayer server) {
            if (type == ClickType.PICKUP && button == 1) TimedSmithingController.cancel(server);
            else if ((type == ClickType.PICKUP || type == ClickType.QUICK_MOVE) && button == 0)
                TimedSmithingController.start(server, menu, type == ClickType.QUICK_MOVE);
            menu.broadcastChanges();
        }
        ci.cancel();
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void mite$cancelClosedUpgrade(Player player, CallbackInfo ci) {
        if (player instanceof ServerPlayer server) TimedSmithingController.cancel(server);
    }
}
