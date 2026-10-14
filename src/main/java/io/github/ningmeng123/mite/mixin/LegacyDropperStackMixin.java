package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import io.github.ningmeng123.mite.crafting.LegacyStackTransfers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DropperBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Native dropper success and refusal both retain the full pre-existing source quantity. @author NingMeng123 */
@Mixin(DropperBlock.class)
public abstract class LegacyDropperStackMixin {
    @WrapOperation(method = "dispenseFrom", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/entity/DispenserBlockEntity;getItem(I)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack mite$captureLegacySource(DispenserBlockEntity container, int slot,
            Operation<ItemStack> original, @Share("miteLegacySource") LocalRef<ItemStack> snapshot) {
        ItemStack source = original.call(container, slot);
        snapshot.set(source.copy());
        return source;
    }

    @WrapOperation(method = "dispenseFrom", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/entity/DispenserBlockEntity;setItem(ILnet/minecraft/world/item/ItemStack;)V"))
    private void mite$preserveRemainingSource(DispenserBlockEntity container, int slot, ItemStack remainder,
            Operation<Void> original, @Share("miteLegacySource") LocalRef<ItemStack> snapshot) {
        if (!LegacyStackTransfers.preserve(container, slot, remainder, snapshot.get())) original.call(container, slot, remainder);
    }
}
