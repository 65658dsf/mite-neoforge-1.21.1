package io.github.ningmeng123.mite.world;

import io.github.ningmeng123.mite.registry.MiteResourceBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/** Source anz requires a two-block-high side frame, including paired doors. @author NingMeng123 */
public final class DecorativeDoorFrames {
    private DecorativeDoorFrames() {}
    public static boolean supported(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) pos = pos.below();
        Direction facing = state.getValue(DoorBlock.FACING);
        Direction hinge = state.getValue(DoorBlock.HINGE) == DoorHingeSide.LEFT ? facing.getCounterClockWise() : facing.getClockWise();
        BlockPos support = pos.relative(hinge);
        // A matching door can act as the inner half of a double-door assembly. Its outer frame
        // supplies the hinges; do not permit arbitrarily long unsupported rows of doors.
        BlockState adjacent = level.getBlockState(support);
        if (adjacent.is(state.getBlock()) && adjacent.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
                && adjacent.getValue(DoorBlock.FACING) == facing
                && adjacent.getValue(DoorBlock.HINGE) != state.getValue(DoorBlock.HINGE)) support = support.relative(hinge);
        return suitable(level, support, hinge.getOpposite()) && suitable(level, support.above(), hinge.getOpposite());
    }
    private static boolean suitable(LevelReader level, BlockPos pos, Direction face) {
        BlockState state = level.getBlockState(pos);
        // anz rejects loose soil/sand/clay, leaves, snow, wool, pumpkins, glass and sponge.
        if (state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(BlockTags.LEAVES)
                || state.is(BlockTags.WOOL) || state.is(BlockTags.SNOW) || state.is(Blocks.GRAVEL)
                || state.is(MiteResourceBlocks.NETHER_GRAVEL.get()) || state.is(Blocks.CLAY)
                || state.is(Blocks.PUMPKIN) || state.is(Blocks.CARVED_PUMPKIN) || state.is(Blocks.JACK_O_LANTERN)
                || state.is(Blocks.SPONGE) || state.is(Blocks.WET_SPONGE) || state.is(Blocks.GLASS)
                || state.getBlock() instanceof net.minecraft.world.level.block.StainedGlassBlock) return false;
        return state.isFaceSturdy(level, pos, face);
    }
}
