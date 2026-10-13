package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.crafting.TimedCraftingController;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Direct quick-move callers and recipe-book ingredient edits share the server session. @author NingMeng123 */
@Mixin({CraftingMenu.class, InventoryMenu.class})
public abstract class TimedCraftingMenuMixin {
    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void mite$timeDirectQuickMove(Player player, int slot, CallbackInfoReturnable<ItemStack> cir) {
        AbstractContainerMenu menu = (AbstractContainerMenu)(Object)this;
        if (slot == 0 && TimedCraftingController.isTimed(player, menu)) {
            if (player instanceof ServerPlayer serverPlayer) TimedCraftingController.start(serverPlayer, menu);
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }

    @Inject(method = "slotsChanged", at = @At("HEAD"))
    private void mite$cancelChangedInputs(Container container, CallbackInfo ci) {
        TimedCraftingController.inputsChanged((AbstractContainerMenu)(Object)this);
    }
}
