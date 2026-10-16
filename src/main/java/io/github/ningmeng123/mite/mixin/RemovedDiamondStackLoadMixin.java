package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.world.DiamondEquipmentRemoval;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Clean saved inventories without making the registry or its item codecs invalid. @author NingMeng123 */
@Mixin(ItemStack.class)
public abstract class RemovedDiamondStackLoadMixin {
    @Inject(method = "parse", at = @At("RETURN"), cancellable = true)
    private static void mite$cleanStoredEquipment(HolderLookup.Provider lookup, Tag tag,
            CallbackInfoReturnable<Optional<ItemStack>> cir) {
        var decoded = cir.getReturnValue();
        if (decoded.isEmpty()) return;
        ItemStack original = decoded.get();
        ItemStack replacement = DiamondEquipmentRemoval.clean(original);
        if (replacement != original) cir.setReturnValue(replacement.isEmpty() ? Optional.empty() : Optional.of(replacement));
    }
}
