package io.github.ningmeng123.mite.block;

import com.mojang.serialization.MapCodec;
import io.github.ningmeng123.mite.rules.DecorativeRules;
import io.github.ningmeng123.mite.world.DecorativePlants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A source Dahlia has no suspicious-stew effect; it remains a decorative plant. @author NingMeng123 */
public final class DahliaBlock extends BushBlock {
    public static final MapCodec<DahliaBlock> CODEC = simpleCodec(DahliaBlock::new);
    public DahliaBlock(Properties properties) { super(properties); }
    @Override public MapCodec<DahliaBlock> codec() { return CODEC; }
    @Override protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return super.canSurvive(state, level, pos) && DecorativePlants.canFlowerSurvive(7, level, pos);
    }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        var offset = state.getOffset(level, pos);
        return Block.box(4.8, 0, 4.8, 11.2, DecorativeRules.flowerHeight(7) * 16, 11.2).move(offset.x, offset.y, offset.z);
    }
    @Override protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.canSurvive(level, pos)) level.destroyBlock(pos, true);
    }
}
