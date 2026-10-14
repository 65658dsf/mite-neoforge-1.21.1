package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import io.github.ningmeng123.mite.crafting.LegacyStackTransfers;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** One native dispense cannot truncate the remaining contents of a saved overfull source slot. @author NingMeng123 */
@Mixin(DispenserBlock.class)
public abstract class LegacyDispenserStackMixin {
    @WrapOperation(method = "dispenseFrom", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/core/dispenser/DispenseItemBehavior;dispense(Lnet/minecraft/core/dispenser/BlockSource;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack mite$captureLegacySource(DispenseItemBehavior behavior, BlockSource source, ItemStack stack,
            Operation<ItemStack> original, @Share("miteLegacySource") LocalRef<ItemStack> snapshot) {
        snapshot.set(stack.copy());
        return original.call(behavior, source, stack);
    }

    @WrapOperation(method = "dispenseFrom", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/entity/DispenserBlockEntity;setItem(ILnet/minecraft/world/item/ItemStack;)V"))
    private void mite$preserveRemainingSource(DispenserBlockEntity container, int slot, ItemStack remainder,
            Operation<Void> original, @Share("miteLegacySource") LocalRef<ItemStack> snapshot) {
        if (!LegacyStackTransfers.preserve(container, slot, remainder, snapshot.get())) original.call(container, slot, remainder);
    }
}
