package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Only the verified merchant result is a batch; payment slots retain normal insertion limits. @author NingMeng123 */
@Mixin(MerchantContainer.class)
public abstract class MerchantBatchContainerMixin {
    @Shadow private MerchantOffer activeOffer;

    @WrapOperation(method = "setItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;limitSize(I)V"))
    private void mite$keepFullOfferResult(ItemStack stack, int maximum, Operation<Void> original, int slot, ItemStack incoming) {
        if (slot == 2 && !stack.isEmpty() && stack.getCount() > maximum && activeOffer != null && !activeOffer.isOutOfStock()
                && ItemStack.matches(stack, activeOffer.assemble())) return;
        original.call(stack, maximum);
    }
}
