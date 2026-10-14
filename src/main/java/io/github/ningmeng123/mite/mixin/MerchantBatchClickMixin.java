package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.crafting.MerchantBatchTransfers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MerchantMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Wait for server-owned complete trade distribution instead of creating an oversized client cursor. @author NingMeng123 */
@Mixin(AbstractContainerMenu.class)
public abstract class MerchantBatchClickMixin {
    @Inject(method = "doClick", at = @At("HEAD"), cancellable = true)
    private void mite$oneCompleteTrade(int slot, int button, ClickType type, Player player, CallbackInfo ci) {
        if (type == ClickType.QUICK_MOVE) return; // Preserve the native repeat loop and direct quickMoveStack callers.
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        if (!MerchantBatchTransfers.handles(menu, player, slot, type)) return;
        if (player instanceof ServerPlayer serverPlayer) MerchantBatchTransfers.take(serverPlayer, (MerchantMenu) menu, button, type);
        ci.cancel();
    }
}
