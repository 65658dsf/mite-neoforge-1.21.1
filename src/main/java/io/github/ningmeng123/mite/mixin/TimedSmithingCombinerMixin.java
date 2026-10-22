package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.crafting.TimedSmithingController;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Direct combiner transfers cannot bypass timing, and every input edit invalidates progress. @author NingMeng123 */
@Mixin(ItemCombinerMenu.class)
public abstract class TimedSmithingCombinerMixin {
    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void mite$timeDirectUpgradeTransfer(Player player, int slot, CallbackInfoReturnable<ItemStack> cir) {
        AbstractContainerMenu menu = (AbstractContainerMenu)(Object)this;
        if (slot != SmithingMenu.RESULT_SLOT || !TimedSmithingController.applies(menu)) return;
        if (player instanceof ServerPlayer server) TimedSmithingController.start(server, menu, true);
        cir.setReturnValue(ItemStack.EMPTY);
    }

    @Inject(method = "slotsChanged", at = @At("HEAD"))
    private void mite$cancelChangedUpgrade(Container container, CallbackInfo ci) {
        TimedSmithingController.inputsChanged((AbstractContainerMenu)(Object)this);
    }
}
