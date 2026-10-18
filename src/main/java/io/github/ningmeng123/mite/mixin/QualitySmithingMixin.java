package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.crafting.MaterialEquipmentUpgrade;
import io.github.ningmeng123.mite.item.EquipmentQuality;
import io.github.ningmeng123.mite.registry.MiteUpgradeTemplates;
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
    @Inject(method = "assemble(Lnet/minecraft/world/item/crafting/SmithingRecipeInput;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;", at = @At("RETURN"), cancellable = true)
    private void mite$preserveUpgradeQuality(SmithingRecipeInput input, HolderLookup.Provider registries,
            CallbackInfoReturnable<ItemStack> cir) {
        ItemStack result = cir.getReturnValue();
        if (!result.isEmpty() && input.template().is(MiteUpgradeTemplates.UPGRADE_TEMPLATE.get())) {
            cir.setReturnValue(MaterialEquipmentUpgrade.assemble(input.base(), result.getItem()));
            return;
        }
        EquipmentQuality.preserve(input.base(), result);
    }
}
