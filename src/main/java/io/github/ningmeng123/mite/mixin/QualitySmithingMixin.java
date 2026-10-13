package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.item.EquipmentQuality;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Modern upgrades inherit quality while adopting the new item's unscaled durability base. @author NingMeng123 */
@Mixin(SmithingTransformRecipe.class)
public abstract class QualitySmithingMixin {
    @Inject(method = "assemble(Lnet/minecraft/world/item/crafting/SmithingRecipeInput;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;", at = @At("RETURN"))
    private void mite$preserveUpgradeQuality(SmithingRecipeInput input, HolderLookup.Provider registries,
            CallbackInfoReturnable<ItemStack> cir) {
        EquipmentQuality.preserve(input.base(), cir.getReturnValue());
    }
}
