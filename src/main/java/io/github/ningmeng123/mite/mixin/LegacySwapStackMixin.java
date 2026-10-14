package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import io.github.ningmeng123.mite.crafting.TimedCraftingController;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Swapping a retained old hotbar stack cannot invoke creative inventory's discard-overflow shortcut. @author NingMeng123 */
@Mixin(AbstractContainerMenu.class)
public abstract class LegacySwapStackMixin {
    @Inject(method = "doClick", at = @At("HEAD"))
    private void mite$captureOverfullSwap(int slot, int button, ClickType type, Player player, CallbackInfo ci,
            @Share("miteLegacySwap") LocalBooleanRef legacy) {
        if (type == ClickType.SWAP && (button >= 0 && button < 9 || button == 40)) {
            ItemStack source = player.getInventory().getItem(button);
            legacy.set(!source.isEmpty() && source.getCount() > source.getMaxStackSize());
        }
    }

    @WrapOperation(method = "doClick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Inventory;add(Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean mite$keepDisplacedSwapItems(Inventory inventory, ItemStack displaced, Operation<Boolean> original,
            int slot, int button, ClickType type, Player player, @Share("miteLegacySwap") LocalBooleanRef legacy) {
        if (!legacy.get()) return original.call(inventory, displaced);
        TimedCraftingController.insertWithoutDiscard(inventory, displaced);
        while (!displaced.isEmpty()) player.drop(displaced.split(Math.min(displaced.getCount(), displaced.getMaxStackSize())), true);
        return true; // Native code must not also drop the already preserved overflow.
    }
}
