package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.crafting.TimedCraftingController;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Consume the server-snapshotted R196 thread quantity within the result-slot completion transaction. @author NingMeng123 */
@Mixin(ResultSlot.class)
public abstract class QualityRepairConsumptionMixin {
    @WrapOperation(method = "onTake", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/CraftingContainer;removeItem(II)Lnet/minecraft/world/item/ItemStack;"),
            require = 1, allow = 1)
    private ItemStack mite$consumeRepairMaterials(CraftingContainer container, int slot, int count,
                                                 Operation<ItemStack> original) {
        return original.call(container, slot, TimedCraftingController.consumptionFor(container, count));
    }
}
