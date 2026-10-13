package io.github.ningmeng123.mite.block;

import com.mojang.serialization.MapCodec;
import io.github.ningmeng123.mite.rules.OreBlockRules;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/** Natural ores preserve R196's fortune eligibility until placed by a player. @author NingMeng123 */
public final class R196OreBlock extends Block {
    public static final MapCodec<R196OreBlock> CODEC = simpleCodec(R196OreBlock::new);
    public R196OreBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(OreBlockRules.PLACED, false));
    }
    @Override public MapCodec<R196OreBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OreBlockRules.PLACED);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(OreBlockRules.PLACED, true);
    }
}
