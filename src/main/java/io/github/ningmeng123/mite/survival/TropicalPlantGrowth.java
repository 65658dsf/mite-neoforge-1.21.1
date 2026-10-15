package io.github.ningmeng123.mite.survival;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Cocoa must hang under naturally grown jungle leaves, possibly below another pod. @author NingMeng123 */
public final class TropicalPlantGrowth {
    private TropicalPlantGrowth() {}
    public static boolean naturalJungleLeaves(BlockState state) {
        return state.is(Blocks.JUNGLE_LEAVES) && !state.getValue(LeavesBlock.PERSISTENT);
    }
    public static boolean cocoaPlacement(LevelReader level,BlockPos pos) {
        BlockState above=level.getBlockState(pos.above());
        if(!above.is(Blocks.COCOA)&&!naturalJungleLeaves(above)) return false;
        for(int height=1;height<4;height++) if(naturalJungleLeaves(level.getBlockState(pos.above(height)))) return true;
        return false;
    }
}
