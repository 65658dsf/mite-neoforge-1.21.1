package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.world.CollapseFallingBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.BaseTorchBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Torches cannot hold up sand or gravel before their native falling tick. @author NingMeng123 */
@Mixin(FallingBlock.class)
public abstract class FallingBlockTorchSupportMixin {
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/block/FallingBlock;isFree(Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean mite$torchCannotSupportLooseBlocks(BlockState support, Operation<Boolean> original,
            BlockState falling, ServerLevel level, BlockPos pos, RandomSource random) {
        return original.call(support) || CollapseFallingBlocks.crushesTorches(falling)
                && support.getBlock() instanceof BaseTorchBlock;
    }
}
