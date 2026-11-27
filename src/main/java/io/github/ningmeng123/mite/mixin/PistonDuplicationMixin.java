package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** Transfers TNT and carpets from the live piston source exactly once. @author NingMeng123 */
@Mixin(PistonBaseBlock.class)
public abstract class PistonDuplicationMixin {
    @WrapOperation(method = "moveBlocks", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;setBlockEntity(Lnet/minecraft/world/level/block/entity/BlockEntity;)V"))
    private void mite$transferLiveSource(Level level, BlockEntity blockEntity, Operation<Void> original) {
        if (level.isClientSide || !(blockEntity instanceof PistonMovingBlockEntity moving)
                || !mite$requiresLiveSource(moving.getMovedState())) {
            original.call(level, blockEntity);
            return;
        }

        BlockState movedState = moving.getMovedState();
        BlockPos destination = moving.getBlockPos();
        BlockPos source = destination.relative(moving.getMovementDirection().getOpposite());
        BlockState liveState = level.getBlockState(source);
        BlockState air = Blocks.AIR.defaultBlockState();
        int movementFlags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_MOVE_BY_PISTON;

        // Neighbor updates can consume TNT or drop carpet after the piston snapshots the push list.
        if (!liveState.is(movedState.getBlock()) || !level.setBlock(source, air, movementFlags)) {
            if (level.getBlockState(destination) == moving.getBlockState()) {
                level.setBlock(destination, air, movementFlags);
            }
            return;
        }

        if (liveState != movedState) {
            blockEntity = MovingPistonBlock.newMovingBlockEntity(destination, moving.getBlockState(),
                    liveState, moving.getDirection(), moving.isExtending(), moving.isSourcePiston());
        }
        original.call(level, blockEntity);
    }

    @Unique
    private static boolean mite$requiresLiveSource(BlockState state) {
        return state.is(Blocks.TNT) || state.getBlock() instanceof CarpetBlock;
    }
}
