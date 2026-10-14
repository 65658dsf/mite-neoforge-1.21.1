package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.crafting.StackBatchTransfers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Client prediction waits for the same server-owned complete instant-batch distribution. @author NingMeng123 */
@Mixin(AbstractContainerMenu.class)
public abstract class StackBatchPickupMixin {
    @Inject(method = "doClick", at = @At("HEAD"), cancellable = true)
    private void mite$oneCompleteBatch(int slot, int button, ClickType type, Player player, CallbackInfo ci) {
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        if (!StackBatchTransfers.handles(menu, player, slot, type)) return;
        if (player instanceof ServerPlayer serverPlayer) StackBatchTransfers.take(serverPlayer, menu, button, type);
        ci.cancel();
    }
}
