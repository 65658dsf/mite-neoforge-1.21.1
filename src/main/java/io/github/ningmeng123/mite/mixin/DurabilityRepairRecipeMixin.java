package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules;
import io.github.ningmeng123.mite.crafting.RangedRodRepair;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RepairItemRecipe;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Preserve quality through modern combine repair without multiplying its maximum twice. @author NingMeng123 */
@Mixin(RepairItemRecipe.class)
public abstract class DurabilityRepairRecipeMixin {
    @Inject(method = "matches(Lnet/minecraft/world/item/crafting/CraftingInput;Lnet/minecraft/world/level/Level;)Z", at = @At("HEAD"), cancellable = true)
    private void mite$equalQualityOnly(CraftingInput input, Level level, CallbackInfoReturnable<Boolean> cir) {
        if (invalidManagedCombine(input)) cir.setReturnValue(false);
    }

    @Inject(method = "assemble(Lnet/minecraft/world/item/crafting/CraftingInput;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;", at = @At("RETURN"), cancellable = true)
    private void mite$preserveRepairQuality(CraftingInput input, HolderLookup.Provider registries,
            CallbackInfoReturnable<ItemStack> cir) {
        if (invalidManagedCombine(input)) {
            cir.setReturnValue(ItemStack.EMPTY);
            return;
        }
        ItemStack output = cir.getReturnValue();
        if (output.isEmpty()) return;
        if (RangedRodRepair.applies(output)) {
            ItemStack first = ItemStack.EMPTY;
            for (ItemStack source : input.items()) {
                if (source.isEmpty()) continue;
                if (first.isEmpty()) first = source;
                else {
                    // aaf creates a new item, copies only dyed color, and does not propagate arbitrary NBT.
                    ItemStack result = new ItemStack(first.getItem());
                    result.setDamageValue(EquipmentDurabilityRules.combinedRepairDamage(first.getDamageValue(), source.getDamageValue(), first.getMaxDamage()));
                    var color = first.get(DataComponents.DYED_COLOR);
                    if (color != null) result.set(DataComponents.DYED_COLOR, color);
                    cir.setReturnValue(result);
                    return;
                }
            }
            return;
        }
        if (!EquipmentDurability.isManaged(output)) return;
        ItemStack first = ItemStack.EMPTY;
        for (ItemStack source : input.items()) {
            if (source.isEmpty()) continue;
            if (!first.isEmpty()) {
                output.setDamageValue(EquipmentDurabilityRules.combinedRepairDamage(first.getDamageValue(),
                        source.getDamageValue(), first.getMaxDamage()));
                break;
            }
            // Vanilla stores the already-adjusted maximum on the result. Restore its unscaled base.
            output.set(DataComponents.MAX_DAMAGE, source.getOrDefault(DataComponents.MAX_DAMAGE, 1));
            EquipmentDurability.setQuality(output, EquipmentDurability.quality(source));
            var color = source.get(DataComponents.DYED_COLOR);
            if (color != null) output.set(DataComponents.DYED_COLOR, color);
            first = source;
        }
    }

    private static boolean invalidManagedCombine(CraftingInput input) {
        ItemStack first = ItemStack.EMPTY;
        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) continue;
            if (first.isEmpty()) first = stack;
            else if (EquipmentDurability.isManaged(first) && EquipmentDurability.isManaged(stack)
                    || RangedRodRepair.applies(first) && RangedRodRepair.applies(stack)) {
                if (!first.isDamaged() || !stack.isDamaged() || first.isEnchanted() || stack.isEnchanted()
                        || EquipmentDurability.isManaged(first) && EquipmentDurability.quality(first) != EquipmentDurability.quality(stack)) return true;
                var firstColor = first.get(DataComponents.DYED_COLOR);
                var secondColor = stack.get(DataComponents.DYED_COLOR);
                if ((firstColor == null) != (secondColor == null)
                        || firstColor != null && firstColor.rgb() != secondColor.rgb()) return true;
            }
        }
        return false;
    }
}
