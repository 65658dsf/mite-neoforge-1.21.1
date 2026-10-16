package io.github.ningmeng123.mite.block;

import com.mojang.serialization.MapCodec;
import io.github.ningmeng123.mite.registry.MiteFoodPlants;
import io.github.ningmeng123.mite.registry.MiteFoods;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** R196 regrowth and yield, with native contact thorns enabled for modern survival. @author NingMeng123 */
public final class BlueberryBushBlock extends SweetBerryBushBlock {
    public static final MapCodec<SweetBerryBushBlock> CODEC = simpleCodec(BlueberryBushBlock::new);
    public static final TagKey<Biome> HABITAT = TagKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath("mite", "blueberry_habitat"));
    public BlueberryBushBlock(BlockBehaviour.Properties properties) { super(properties); }
    @Override public MapCodec<SweetBerryBushBlock> codec() { return CODEC; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Block.box(3.2D, 0D, 3.2D, 12.8D, 9.6D, 12.8D);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state == null ? null : state.setValue(AGE, context.getPlayer() != null && context.getPlayer().isCreative() ? 3 : 0);
    }
    @Override protected boolean isRandomlyTicking(BlockState state) { return true; }
    @Override protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBiome(pos).is(HABITAT) && level.canSeeSky(pos) && !level.getBlockState(pos.below()).is(Blocks.FARMLAND)
                && super.canSurvive(state, level, pos);
    }
    @Override protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.canSurvive(level, pos)) { level.destroyBlock(pos, true); return; }
        if (state.getValue(AGE) >= 3 || level.getMaxLocalRawBrightness(pos.above()) != 15) return;
        float temperature = level.getBiome(pos).value().getBaseTemperature();
        float chance = .025F * Math.max(1F - Math.abs(temperature - .7F), 0F);
        if (random.nextFloat() < chance) level.setBlock(pos, state.setValue(AGE, state.getValue(AGE) + 1), 2);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (state.getValue(AGE) != 3) return InteractionResult.PASS;
        if (!level.isClientSide) {
            popResource(level, pos, new ItemStack(MiteFoods.get("blueberries")));
            level.setBlock(pos, state.setValue(AGE, 0), 2);
            level.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1F, 1F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    // BlockBush.fertilize is dormant: R196's xl.tryFertilize dispatches only to grass/anr.
    @Override public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) { return false; }
    @Override public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {}
    @Override public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(MiteFoodPlants.BLUEBERRY_BUSH_ITEM.get());
    }
}
