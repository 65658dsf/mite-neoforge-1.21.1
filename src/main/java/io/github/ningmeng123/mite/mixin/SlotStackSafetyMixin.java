package io.github.ningmeng123.mite.mixin;

import java.util.Optional;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Legacy overfull stacks may be withdrawn, but cannot turn insertion into a negative transfer. @author NingMeng123 */
@Mixin(Slot.class)
public abstract class SlotStackSafetyMixin {
    @Inject(method = "safeInsert(Lnet/minecraft/world/item/ItemStack;I)Lnet/minecraft/world/item/ItemStack;",
            at = @At("HEAD"), cancellable = true)
    private void mite$noNegativeInsertion(ItemStack incoming, int amount, CallbackInfoReturnable<ItemStack> cir) {
        Slot slot = (Slot) (Object) this;
        if (amount <= 0 || slot.getItem().getCount() >= slot.getMaxStackSize(incoming)) cir.setReturnValue(incoming);
    }

    @Inject(method = "tryRemove", at = @At("HEAD"), cancellable = true)
    private void mite$noNegativeWithdrawal(int amount, int maximum, Player player,
            CallbackInfoReturnable<Optional<ItemStack>> cir) {
        if (amount <= 0 || maximum <= 0) cir.setReturnValue(Optional.empty());
    }
}
