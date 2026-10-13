package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.crafting.TimedCraftingController;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Timed recipe remainders must survive a full creative inventory. @author NingMeng123 */
@Mixin(ResultSlot.class)
public abstract class TimedCraftingRemainderMixin {
    @WrapOperation(method = "onTake", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Inventory;add(Lnet/minecraft/world/item/ItemStack;)Z"),
            require = 1, allow = 1)
    private boolean mite$preserveManufacturingRemainder(Inventory inventory, ItemStack remainder,
            Operation<Boolean> original) {
        return TimedCraftingController.isCompletingIn(inventory)
                ? TimedCraftingController.insertWithoutDiscard(inventory, remainder)
                : original.call(inventory, remainder);
    }
}
