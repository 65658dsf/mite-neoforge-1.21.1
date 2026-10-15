package io.github.ningmeng123.mite.block;

import com.mojang.serialization.MapCodec;
import io.github.ningmeng123.mite.registry.MiteFoods;
import io.github.ningmeng123.mite.survival.CropLifecycle;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Onion identity and existing save properties; shared crops own growth, disease, treatment and harvest. @author NingMeng123 */
public final class OnionCropBlock extends CropBlock {
    public static final MapCodec<CropBlock> CODEC=simpleCodec(OnionCropBlock::new);
    public static final BooleanProperty BLIGHTED=BooleanProperty.create("blighted");
    public static final BooleanProperty DEAD=BooleanProperty.create("dead");
    public OnionCropBlock(BlockBehaviour.Properties properties) {
        super(properties);registerDefaultState(defaultBlockState().setValue(BLIGHTED,false).setValue(DEAD,false));
    }
    @Override public MapCodec<CropBlock> codec() { return CODEC; }
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {
        return Block.box(0,0,0,16,4,16);
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) {
        super.createBlockStateDefinition(builder);builder.add(BLIGHTED,DEAD);
    }
    @Override protected ItemLike getBaseSeedId() { return MiteFoods.get("onion"); }
    public static float growthRate(BlockState state,ServerLevel level,BlockPos pos) {
        return CropLifecycle.growthRate(state,level,pos);
    }
}
