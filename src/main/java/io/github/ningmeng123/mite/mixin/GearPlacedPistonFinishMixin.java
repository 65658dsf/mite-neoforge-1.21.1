package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.progression.PlayerPlacedBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A saved moving piston restores its player marker at the final native block position. @author NingMeng123 */
@Mixin(PistonMovingBlockEntity.class)
public abstract class GearPlacedPistonFinishMixin {
    @WrapOperation(method = {"tick", "finalTick"}, at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private static boolean mite$finishPlayerBlock(Level level, BlockPos pos, BlockState state, int flags, Operation<Boolean> original) {
        boolean placed = level.getBlockState(pos).is(Blocks.MOVING_PISTON) && PlayerPlacedBlocks.isPlaced(level, pos);
        // Mark before callbacks: an immediately falling destination must carry the same origin.
        if (placed && !state.isAir()) PlayerPlacedBlocks.set(level, pos, true);
        boolean changed = original.call(level, pos, state, flags);
        if (placed && changed && !level.getBlockState(pos).isAir()) PlayerPlacedBlocks.set(level, pos, true);
        return changed;
    }
}
