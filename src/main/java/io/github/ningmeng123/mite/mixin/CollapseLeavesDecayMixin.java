package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.world.CollapseSecondaryTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.LeavesBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** aoz.k: only the leaf-decay removal, not unrelated block removal or world generation. @author NingMeng123 */
@Mixin(LeavesBlock.class)
public abstract class CollapseLeavesDecayMixin {
    @WrapOperation(method = "randomTick", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/server/level/ServerLevel;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z"))
    private boolean mite$decayingLeafSupport(ServerLevel level, BlockPos pos, boolean moving,
            Operation<Boolean> original) {
        boolean removed = original.call(level, pos, moving);
        if (removed) CollapseSecondaryTriggers.removedSupport(level, pos);
        return removed;
    }
}
