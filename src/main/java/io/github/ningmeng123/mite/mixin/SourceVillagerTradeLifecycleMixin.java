package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.ningmeng123.mite.crafting.SourceVillagerTrades;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native job loss clears offers even when nobody reads them before the same job is acquired again. @author NingMeng123 */
@Mixin(Villager.class)
public abstract class SourceVillagerTradeLifecycleMixin {
    @Unique private boolean mite$loadingSourceStock;

    @WrapMethod(method = "readAdditionalSaveData")
    private void mite$preserveSavedSourceStockDuringLoad(CompoundTag tag, Operation<Void> original) {
        boolean previous = mite$loadingSourceStock;
        mite$loadingSourceStock = true;
        try { original.call(tag); }
        finally { mite$loadingSourceStock = previous; }
    }

    @Inject(method = "setVillagerData", at = @At("HEAD"))
    private void mite$forgetStockWhenTheNativeProfessionChanges(VillagerData next, CallbackInfo ci) {
        Villager self = (Villager)(Object)this;
        if (!self.level().isClientSide && !mite$loadingSourceStock
                && self.getVillagerData().getProfession() != next.getProfession())
            self.getPersistentData().remove(SourceVillagerTrades.STATE);
    }
}
