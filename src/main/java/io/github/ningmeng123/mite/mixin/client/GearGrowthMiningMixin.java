package io.github.ningmeng123.mite.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.progression.GearProgression;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Inventory synchronization during growth cannot restart an already active mining operation. @author NingMeng123 */
@Mixin(MultiPlayerGameMode.class)
public abstract class GearGrowthMiningMixin {
    @WrapOperation(method = "sameDestroyTarget", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;shouldCauseBlockBreakReset(Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean mite$growthKeepsMining(ItemStack previous, ItemStack next, Operation<Boolean> original) {
        if (previous.isEmpty() || next.isEmpty() || !previous.is(next.getItem())
                || !GearProgression.growthChanged(previous, next) || !GearProgression.sameIdentity(previous, next))
            return original.call(previous, next);
        ItemStack left = previous.copy(), right = next.copy();
        left.remove(GearProgression.PROGRESS.get());
        right.remove(GearProgression.PROGRESS.get());
        return original.call(left, right);
    }
}
