package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.crafting.MerchantBatchTransfers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Every native Shift iteration charges one complete offer and cannot discard a partially stored result. @author NingMeng123 */
@Mixin(MerchantMenu.class)
public abstract class MerchantBatchQuickMoveMixin {
    @Shadow private void playTradeSound() { throw new AssertionError(); }

    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void mite$wholeTradePerShiftIteration(Player player, int slot, CallbackInfoReturnable<ItemStack> cir) {
        MerchantMenu menu = (MerchantMenu) (Object) this;
        if (!MerchantBatchTransfers.handles(menu, player, slot, ClickType.QUICK_MOVE)) return;
        ItemStack result = player instanceof ServerPlayer serverPlayer
                ? MerchantBatchTransfers.take(serverPlayer, menu, 0, ClickType.QUICK_MOVE) : ItemStack.EMPTY;
        if (!result.isEmpty()) playTradeSound();
        cir.setReturnValue(result);
    }
}
