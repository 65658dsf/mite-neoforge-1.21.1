package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.registry.MiteItemStacking;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Snow depth and stack-local overrides must obey the same confirmed upper bound. @author NingMeng123 */
@Mixin(ItemStack.class)
public abstract class SourceStackLimitMixin {
    @Inject(method = "getMaxStackSize", at = @At("RETURN"), cancellable = true)
    private void mite$sourceMaximum(CallbackInfoReturnable<Integer> cir) {
        ItemStack stack = (ItemStack) (Object) this;
        if (stack.isEmpty()) return;
        int limit = MiteItemStacking.limit(stack);
        if (limit > 0 && cir.getReturnValue() > limit) cir.setReturnValue(limit);
    }
}
