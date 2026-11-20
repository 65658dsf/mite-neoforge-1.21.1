package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.ningmeng123.mite.crafting.SmithingTableAccess;
import io.github.ningmeng123.mite.crafting.SmithingTableTier;
import io.github.ningmeng123.mite.crafting.TimedSmithingController;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native previews and transactions use the same material and station requirements. @author NingMeng123 */
@Mixin(SmithingMenu.class)
public abstract class TimedSmithingMenuMixin extends ItemCombinerMenu implements SmithingTableAccess {
    @Unique private final DataSlot mite$smithingTable = DataSlot.standalone();

    protected TimedSmithingMenuMixin(MenuType<?> type, int id, Inventory inventory, ContainerLevelAccess access) {
        super(type, id, inventory, access);
    }

    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V", at = @At("TAIL"))
    private void mite$syncSmithingTable(int id, Inventory inventory, ContainerLevelAccess access, CallbackInfo ci) {
        addDataSlot(mite$smithingTable);
        if (!inventory.player.level().isClientSide()) {
            mite$smithingTable.set(access.evaluate((level, pos) -> SmithingTableTier.idForBlock(level.getBlockState(pos).getBlock()), 0));
        }
    }

    @Override
    public SmithingTableTier mite$smithingTableTier() {
        return SmithingTableTier.fromId(mite$smithingTable.get());
    }

    @Inject(method = "isValidBlock", at = @At("HEAD"), cancellable = true)
    private void mite$keepOpenedSmithingTable(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        SmithingTableTier tier = mite$smithingTableTier();
        if (tier != null) cir.setReturnValue(state.is(tier.block()));
    }

    @Inject(method = "createRecipeInput", at = @At("RETURN"), cancellable = true)
    private void mite$includeInventoryMetal(CallbackInfoReturnable<SmithingRecipeInput> callback) {
        callback.setReturnValue(TimedSmithingController.includeInventory(callback.getReturnValue(), player));
    }

    @Inject(method = "createResult", at = @At("TAIL"))
    private void mite$requireUpgradeStationAndCost(CallbackInfo ci) {
        SmithingMenu menu = (SmithingMenu)(Object)this;
        if (!TimedSmithingController.previewAllowed(menu, player)) menu.getSlot(SmithingMenu.RESULT_SLOT).set(ItemStack.EMPTY);
    }

    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void mite$denyUnfinishedUpgrade(Player player, boolean present, CallbackInfoReturnable<Boolean> cir) {
        SmithingMenu menu = (SmithingMenu)(Object)this;
        if (TimedSmithingController.applies(menu) && !TimedSmithingController.completing(menu)) cir.setReturnValue(false);
    }

    @WrapMethod(method = "onTake")
    private void mite$commitOnlyCompletedUpgrade(Player player, ItemStack result, Operation<Void> original) {
        SmithingMenu menu = (SmithingMenu)(Object)this;
        if (!TimedSmithingController.applies(menu) && !TimedSmithingController.completing(menu)) {
            original.call(player, result);
            return;
        }
        if (!TimedSmithingController.claim(menu, player, result)) return;
        original.call(player, result);
        TimedSmithingController.finish(menu);
    }

    @WrapMethod(method = "shrinkStackInSlot")
    private void mite$consumeExactUpgradeCost(int slot, Operation<Void> original) {
        SmithingMenu menu = (SmithingMenu)(Object)this;
        if (TimedSmithingController.completing(menu)) TimedSmithingController.consume(menu, slot);
        else original.call(slot);
    }
}
