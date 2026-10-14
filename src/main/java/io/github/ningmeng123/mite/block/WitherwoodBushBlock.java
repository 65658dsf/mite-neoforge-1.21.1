package io.github.ningmeng123.mite.block;

import com.mojang.serialization.MapCodec;
import io.github.ningmeng123.mite.registry.MiteResourceBlocks;
import io.github.ningmeng123.mite.rules.DecorativeRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Source metadata-1 ant: gravel-only support, withering contact, recoverable with shears. @author NingMeng123 */
public final class WitherwoodBushBlock extends BushBlock {
    public static final MapCodec<WitherwoodBushBlock> CODEC = simpleCodec(WitherwoodBushBlock::new);
    public WitherwoodBushBlock(Properties properties) { super(properties); }
    @Override public MapCodec<WitherwoodBushBlock> codec() { return CODEC; }
    @Override protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).is(MiteResourceBlocks.NETHER_GRAVEL.get());
    }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return box(1.6, 0, 1.6, 14.4, 12.8, 14.4);
    }
    @Override protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (!state.canSurvive(level, pos)) {
            // Source wasNotLegal drops the plant itself, rather than rolling the half-chance stick.
            if (level instanceof ServerLevel server) popResource(server, pos, new ItemStack(this));
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }
    @Override protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.canSurvive(level, pos)) { level.removeBlock(pos, false); popResource(level, pos, new ItemStack(this)); }
    }
    @Override protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide || !(entity instanceof LivingEntity living)) return;
        MobEffectInstance current = living.getEffect(MobEffects.WITHER);
        if (current == null) living.addEffect(new MobEffectInstance(MobEffects.WITHER, 200, 0));
        else {
            int duration = DecorativeRules.witherContactDuration(current.getDuration(), current.getAmplifier());
            if (duration != current.getDuration()) living.addEffect(new MobEffectInstance(MobEffects.WITHER, duration,
                    current.getAmplifier(), current.isAmbient(), current.isVisible(), current.showIcon()));
        }
    }
    @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        int count = 1 + random.nextInt(3);
        for (int i = 0; i < count; i++) level.addParticle(ParticleTypes.WITCH,
                pos.getX() + random.nextFloat(), pos.getY() + random.nextFloat(), pos.getZ() + random.nextFloat(), 0, 0, 0);
    }
    @Override public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction face) { return 100; }
    @Override public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction face) { return 60; }
}
