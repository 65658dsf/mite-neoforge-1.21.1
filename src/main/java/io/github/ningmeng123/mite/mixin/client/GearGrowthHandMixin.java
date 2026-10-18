package io.github.ningmeng123.mite.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.progression.GearProgression;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.ClientHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Growth updates retain the current hand pose without masking slot or other component changes. @author NingMeng123 */
@Mixin(ClientHooks.class)
public abstract class GearGrowthHandMixin {
    @WrapOperation(method = "shouldCauseReequipAnimation", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/Item;shouldCauseReequipAnimation(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;Z)Z"))
    private static boolean mite$growthKeepsHand(Item item, ItemStack previous, ItemStack next, boolean changed,
            Operation<Boolean> original) {
        if (!changed && GearProgression.growthChanged(previous, next) && previous.getCount() == next.getCount()
                && GearProgression.sameIgnoringGrowth(previous, next)) return false;
        return original.call(item, previous, next, changed);
    }
}
