package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.block.MaterialWorkbenchBlock;
import io.github.ningmeng123.mite.crafting.WorkbenchAccess;
import io.github.ningmeng123.mite.crafting.WorkbenchRules;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Covers placed benches and direct Shift-click callers, including stale outputs. @author NingMeng123 */
@Mixin(CraftingMenu.class)
public abstract class WorkbenchTierMixin implements WorkbenchAccess {
    @Shadow @Final private ContainerLevelAccess access;
    @Override public float mite$workbenchCapacity() {
        return access.evaluate((level, pos) -> WorkbenchRules.capacity(level.getBlockState(pos)), 0F);
    }
    @Inject(method = "stillValid", at = @At("HEAD"), cancellable = true)
    private void mite$validMaterialTable(Player player, CallbackInfoReturnable<Boolean> cir) {
        access.execute((level, pos) -> {
            if (level.getBlockState(pos).getBlock() instanceof MaterialWorkbenchBlock) {
                cir.setReturnValue(player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= 64);
            }
        });
    }
    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void mite$checkTier(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
        CraftingMenu menu = (CraftingMenu)(Object)this;
        if (index == 0 && !WorkbenchRules.allows(menu, menu.getSlot(0).getItem())) cir.setReturnValue(ItemStack.EMPTY);
    }
}
