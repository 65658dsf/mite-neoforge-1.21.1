package io.github.ningmeng123.mite.block;

import com.mojang.serialization.MapCodec;
import io.github.ningmeng123.mite.entity.CubeCropBlight;
import io.github.ningmeng123.mite.registry.MiteFoodPlants;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Farmland crop grown from lemon seeds. @author NingMeng123 */
public final class LemonCropBlock extends CropBlock {
    public static final MapCodec<LemonCropBlock> CODEC = simpleCodec(LemonCropBlock::new);
    public static final IntegerProperty AGE = BlockStateProperties.AGE_3;
    private static final VoxelShape[] SHAPES = {
            Block.box(0, 0, 0, 16, 5, 16),
            Block.box(0, 0, 0, 16, 8, 16),
            Block.box(0, 0, 0, 16, 11, 16),
            Block.box(0, 0, 0, 16, 13, 16)
    };

    public LemonCropBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<LemonCropBlock> codec() {
        return CODEC;
    }

    @Override
    protected IntegerProperty getAgeProperty() {
        return AGE;
    }

    @Override
    public int getMaxAge() {
        return 3;
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return MiteFoodPlants.LEMON_SEEDS.get();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[getAge(state)];
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        // This replaces CropBlock's state definition, including the disease properties its mixin adds.
        builder.add(AGE, CubeCropBlight.BLIGHTED, CubeCropBlight.DEAD);
    }
}
