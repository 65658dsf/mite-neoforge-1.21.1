package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.loot.ReducedTrialLoot;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.vault.VaultBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A deliberately empty balanced roll still spends its key and records its recipient. @author NingMeng123 */
@Mixin(VaultBlockEntity.Server.class)
public abstract class ReducedTrialVaultRewardMixin {
    @WrapOperation(method = "tryInsertKey", at = @At(value = "INVOKE", target = "Ljava/util/List;isEmpty()Z"))
    private static boolean mite$settleReducedTrialReward(List<ItemStack> loot, Operation<Boolean> original) {
        return !(loot instanceof ReducedTrialLoot) && original.call(loot);
    }
}
