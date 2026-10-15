package io.github.ningmeng123.mite.block;

import com.mojang.serialization.MapCodec;
import io.github.ningmeng123.mite.registry.MiteFoods;
import io.github.ningmeng123.mite.survival.FoodAgriculture;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Source onion climate, slow growth, drought and blight; no instant bone-meal growth. @author NingMeng123 */
public final class OnionCropBlock extends CropBlock {
    public static final MapCodec<CropBlock> CODEC = simpleCodec(OnionCropBlock::new);
    public static final BooleanProperty BLIGHTED = BooleanProperty.create("blighted");
    public static final BooleanProperty DEAD = BooleanProperty.create("dead");
    private static final ThreadLocal<DroughtDrop> DROUGHT_DROP = new ThreadLocal<>();
    private record DroughtDrop(ServerLevel level, BlockPos pos) {}
    public OnionCropBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(BLIGHTED, false).setValue(DEAD, false));
    }
    @Override public MapCodec<CropBlock> codec() { return CODEC; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Block.box(0D, 0D, 0D, 16D, 4D, 16D);
    }
    @Override protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (getAge(state) == 0) return 1F;
        float progress = super.getDestroyProgress(state, player, level, pos);
        return isMaxAge(state) ? progress * .5F : progress;
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder); builder.add(BLIGHTED, DEAD);
    }
    @Override protected ItemLike getBaseSeedId() { return MiteFoods.get("onion"); }
    @Override protected boolean isRandomlyTicking(BlockState state) { return !state.getValue(DEAD); }
    @Override protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(DEAD) || !level.isAreaLoaded(pos, 1)) return;
        float growth = growthRate(state, level, pos);
        if (growth == 0F && !FoodAgriculture.hasWater(level, pos.below())) {
            if (random.nextFloat() < .05F) {
                if (isMaxAge(state)) { dropFromDrought(state, level, pos); level.removeBlock(pos, false); }
                else level.setBlock(pos, state.setValue(DEAD, true), 3);
            }
            return;
        }
        if (state.getValue(BLIGHTED)) {
            if (random.nextInt(64) == 0) level.setBlock(pos, state.setValue(DEAD, true).setValue(AGE, Math.min(6, getAge(state))), 3);
            else if (!random.nextBoolean()) {
                BlockPos neighbor = pos.offset(random.nextInt(3) - 1, random.nextInt(3) - 1, random.nextInt(3) - 1);
                BlockState other = level.getBlockState(neighbor);
                if (!neighbor.equals(pos) && other.getBlock() instanceof OnionCropBlock && !other.getValue(DEAD))
                    level.setBlock(neighbor, other.setValue(BLIGHTED, true), 2);
            }
            return;
        }
        if (io.github.ningmeng123.mite.world.LunarCropEffects.infectOnRandomTick(level,pos,state,random)) return;
        int light = level.getMaxLocalRawBrightness(pos.above());
        float temperature = level.getBiome(pos).value().getBaseTemperature();
        float delta = temperature < 1F ? 1F - temperature : Math.max(0F, temperature - 1.2F);
        float humidity = FoodAgriculture.humid(level, pos) ? 1.5F : 1F;
        if (random.nextFloat() < .0005F * Math.max(1F - delta, 0F) * humidity * (1F - light / 16F)) {
            level.setBlock(pos, state.setValue(BLIGHTED, true), 2);
        } else if (light == 15 && growth > 0F && !isMaxAge(state) && random.nextInt((int) (25F / growth) + 1) == 0) {
            level.setBlock(pos, state.setValue(AGE, getAge(state) + 1), 2);
            FoodAgriculture.onCropGrowth(level, pos.below(), random);
        }
    }
    public static float growthRate(BlockState state, ServerLevel level, BlockPos pos) {
        BlockState soil = level.getBlockState(pos.below());
        if (!soil.is(Blocks.FARMLAND) || soil.getValue(FarmBlock.MOISTURE) == 0) return 0F;
        float temperature = level.getBiome(pos).value().getBaseTemperature();
        float delta = temperature < .8F ? .8F - temperature : Math.max(0F, temperature - 1.2F);
        return getGrowthSpeed(state, level, pos) * Math.max(1F - delta, 0F) * .25F
                * FoodAgriculture.proximity(level, pos, state.getBlock());
    }
    @Override protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        if (params.getOptionalParameter(LootContextParams.EXPLOSION_RADIUS) != null) return List.of();
        if (getAge(state) == 0 || params.getOptionalParameter(LootContextParams.THIS_ENTITY) instanceof Player)
            return super.getDrops(state, params);
        var drought = DROUGHT_DROP.get();
        var origin = params.getOptionalParameter(LootContextParams.ORIGIN);
        if (drought != null && drought.level() == params.getLevel() && origin != null
                && drought.pos().equals(BlockPos.containing(origin))) return super.getDrops(state, params);
        // Native loss of supporting soil is a self-drop, not a player harvest.
        return List.of();
    }
    @Override public boolean canDropFromExplosion(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
        return false;
    }
    private static void dropFromDrought(BlockState state, ServerLevel level, BlockPos pos) {
        var previous = DROUGHT_DROP.get();
        DROUGHT_DROP.set(new DroughtDrop(level, pos.immutable()));
        try { Block.dropResources(state, level, pos); }
        finally {
            if (previous == null) DROUGHT_DROP.remove();
            else DROUGHT_DROP.set(previous);
        }
    }
    @Override public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return state.getValue(BLIGHTED) && !state.getValue(DEAD);
    }
    @Override public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) { return true; }
    @Override public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        if (!state.getValue(DEAD)) level.setBlock(pos, state.setValue(BLIGHTED, false), 2);
    }
}
