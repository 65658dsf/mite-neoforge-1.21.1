package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.ningmeng123.mite.crafting.LegacyStackTransfers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.neoforged.neoforge.items.VanillaInventoryCodeHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** NeoForge's pinned capability lambdas restore old source slots without applying a fresh insertion clamp. @author NingMeng123 */
@Mixin(value = VanillaInventoryCodeHooks.class, remap = false)
public abstract class LegacyCapabilityStackMixin {
    @WrapOperation(method = "lambda$insertHook$2", at = @At(value = "INVOKE", remap = false,
            target = "Lnet/minecraft/world/level/block/entity/HopperBlockEntity;setItem(ILnet/minecraft/world/item/ItemStack;)V"), remap = false)
    private static void mite$restoreRejectedHopperSource(HopperBlockEntity container, int slot, ItemStack originalSnapshot,
            Operation<Void> original) {
        if (!LegacyStackTransfers.preserve(container, slot, originalSnapshot, originalSnapshot)) original.call(container, slot, originalSnapshot);
    }

    @WrapOperation(method = "lambda$dropperInsertHook$1", at = @At(value = "INVOKE", remap = false,
            target = "Lnet/minecraft/world/level/block/entity/DispenserBlockEntity;setItem(ILnet/minecraft/world/item/ItemStack;)V"), remap = false)
    private static void mite$retainCapabilityDropperSource(DispenserBlockEntity container, int slot, ItemStack remainder,
            Operation<Void> original, @Local(argsOnly = true) ItemStack source) {
        if (!LegacyStackTransfers.preserve(container, slot, remainder, source)) original.call(container, slot, remainder);
    }
}
