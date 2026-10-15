package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.worldgen.NaturalTrialBlocks;
import io.github.ningmeng123.mite.worldgen.TrialBlockDrops;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Saved moving-piston provenance survives native completion and early sticky finalization. @author NingMeng123 */
@Mixin(PistonMovingBlockEntity.class)
public abstract class TrialBlockPistonFinishMixin {
    @WrapOperation(method = {"tick", "finalTick"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private static boolean mite$keepFinalBlockSource(Level level, BlockPos pos, BlockState state, int flags, Operation<Boolean> original) {
        boolean natural = level.getBlockState(pos).is(Blocks.MOVING_PISTON) && NaturalTrialBlocks.isNatural(level, pos);
        boolean changed;
        // A finishing moving block can lose support in native onPlace/neighbor callbacks.
        // Retain its source until those immediate destruction callbacks have completed too.
        try (var ignored = TrialBlockDrops.destruction(level, pos)) {
            changed = original.call(level, pos, state, flags);
        }
        if (natural && changed && !state.isAir() && level.getBlockState(pos).is(state.getBlock())) {
            NaturalTrialBlocks.record(level.getChunkAt(pos), pos);
        }
        return changed;
    }
}
