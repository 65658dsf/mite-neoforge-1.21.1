package io.github.ningmeng123.mite.survival;

import io.github.ningmeng123.mite.entity.CubeCropBlight;
import io.github.ningmeng123.mite.entity.ai.ConspicuousCactus;
import io.github.ningmeng123.mite.rules.SpecialPlantRules;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.CommonHooks;

/** Source crop-family ticks without delegating back into the modern accelerated formulas. @author NingMeng123 */
public final class SpecialPlantGrowth {
    private static final Map<ServerLevel, byte[]> CACTUS_HEIGHTS = new WeakHashMap<>();
    private SpecialPlantGrowth() {}
    public static boolean stem(BlockState state) { return state.getBlock() instanceof StemBlock || state.getBlock() instanceof AttachedStemBlock; }
    public static boolean managedStem(BlockState state) { return state.is(Blocks.MELON_STEM)||state.is(Blocks.PUMPKIN_STEM)||state.is(Blocks.ATTACHED_MELON_STEM)||state.is(Blocks.ATTACHED_PUMPKIN_STEM); }
    public static boolean dead(BlockState state) { return state.hasProperty(CubeCropBlight.DEAD) && state.getValue(CubeCropBlight.DEAD); }
    public static boolean melon(BlockState state) { return state.is(Blocks.MELON_STEM) || state.is(Blocks.ATTACHED_MELON_STEM); }
    public static boolean sameStem(BlockState one, BlockState two) { return managedStem(two) && melon(one) == melon(two); }
    public static float stemGrowth(ServerLevel level, BlockPos pos, BlockState state) {
        BlockState soil = level.getBlockState(pos.below());
        if (!(soil.getBlock() instanceof FarmBlock) || soil.getValue(FarmBlock.MOISTURE) == 0) return 0F;
        float rate = 1F;
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            BlockState nearby = level.getBlockState(pos.offset(x, -1, z));
            if (!(nearby.getBlock() instanceof FarmBlock)) continue;
            float contribution = nearby.getValue(FarmBlock.MOISTURE) > 0 || FoodAgriculture.fertilized(nearby) ? 3F : 1F;
            rate += x == 0 && z == 0 ? contribution : contribution / 4F;
        }
        boolean alongX = sameStem(state, level.getBlockState(pos.east())) || sameStem(state, level.getBlockState(pos.west()));
        boolean alongZ = sameStem(state, level.getBlockState(pos.north())) || sameStem(state, level.getBlockState(pos.south()));
        boolean diagonal = false;
        for (int x : new int[]{-1, 1}) for (int z : new int[]{-1, 1}) diagonal |= sameStem(state, level.getBlockState(pos.offset(x, 0, z)));
        if (diagonal || alongX && alongZ) rate /= 2F;
        if (FoodAgriculture.fertilized(soil)) rate *= 1.5F;
        return rate * SpecialPlantRules.stemClimate(melon(state), level.getBiome(pos).value().getBaseTemperature(), FoodAgriculture.humid(level, pos));
    }
    public static void stemTick(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        if (dead(state) || !level.isAreaLoaded(pos, 1)) return;
        if (!state.canSurvive(level, pos)) { level.destroyBlock(pos, true); return; }
        float growth = stemGrowth(level, pos, state);
        if (growth == 0F && !FoodAgriculture.hasWater(level,pos.below())) {
            if (random.nextFloat() < .05F) level.setBlockAndUpdate(pos, state.setValue(CubeCropBlight.DEAD, true));
            return;
        }
        if (growth == 0F || level.getMaxLocalRawBrightness(pos.above()) < 15) return;
        if (!CommonHooks.canCropGrow(level, pos, state, random.nextInt((int) (25F / growth) + 1) == 0)) return;
        int age = state.hasProperty(StemBlock.AGE) ? state.getValue(StemBlock.AGE) : 7;
        if (age < 7) level.setBlock(pos, state.setValue(StemBlock.AGE, age + 1), 2);
        else {
            if (random.nextInt(2) == 0) return;
            Block fruit = melon(state) ? Blocks.MELON : Blocks.PUMPKIN;
            for (Direction direction : Direction.Plane.HORIZONTAL) if (level.getBlockState(pos.relative(direction)).is(fruit)) return;
            // The source direction order is west, east, north, south.
            Direction direction = new Direction[]{Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH}[random.nextInt(4)];
            BlockPos target = pos.relative(direction);
            BlockState support = level.getBlockState(target.below());
            if (level.isEmptyBlock(target) && (support.getBlock() instanceof FarmBlock || support.is(Blocks.DIRT) || support.is(Blocks.GRASS_BLOCK))) {
                level.setBlockAndUpdate(target, fruit.defaultBlockState());
                BlockState attached = (melon(state) ? Blocks.ATTACHED_MELON_STEM : Blocks.ATTACHED_PUMPKIN_STEM).defaultBlockState()
                        .setValue(AttachedStemBlock.FACING, direction).setValue(CubeCropBlight.DEAD, random.nextFloat() < .1F);
                level.setBlockAndUpdate(pos, attached);
            }
        }
        FoodAgriculture.onCropGrowth(level, pos.below(), random);
        CommonHooks.fireCropGrowPost(level, pos, state);
    }
    public static void caneTick(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        if (!level.isAreaLoaded(pos, 1)) return;
        if (!state.canSurvive(level, pos)) { level.destroyBlock(pos, true); return; }
        if (random.nextFloat() > level.getBiome(pos).value().getBaseTemperature() - .2F || random.nextFloat() < .8F
                || level.getMaxLocalRawBrightness(pos) < 15 || !caneOpenSky(level,pos.above(2)) || !level.isEmptyBlock(pos.above())) return;
        int height = 1;
        while (level.getBlockState(pos.below(height)).is(Blocks.SUGAR_CANE)) height++;
        if (height >= 3 || !CommonHooks.canCropGrow(level, pos, state, true)) return;
        int age = state.getValue(SugarCaneBlock.AGE);
        if (age == 15) {
            level.setBlockAndUpdate(pos.above(), Blocks.SUGAR_CANE.defaultBlockState());
            level.setBlock(pos, state.setValue(SugarCaneBlock.AGE, 0), 4);
        } else level.setBlock(pos, state.setValue(SugarCaneBlock.AGE, age + 1), 4);
        CommonHooks.fireCropGrowPost(level, pos, state);
    }
    /** adr.d compares against an opacity heightmap, not delayed light-engine brightness. */
    public static boolean caneOpenSky(LevelReader level,BlockPos pos) {
        int top=level.getHeight(Heightmap.Types.WORLD_SURFACE,pos.getX(),pos.getZ());
        for(BlockPos.MutableBlockPos above=pos.mutable();above.getY()<top;above.move(0,1,0))
            if(level.getBlockState(above).getLightBlock(level,above)>0) return false;
        return true;
    }
    public static int cactusHeight(ServerLevel level, BlockPos support) {
        return CACTUS_HEIGHTS.computeIfAbsent(level, key -> SpecialPlantRules.cactusHeights(key.getSeed()))
                [SpecialPlantRules.cactusIndex(support.getX(), support.getY(), support.getZ())];
    }
    public static void cactusTick(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        if (!level.isAreaLoaded(pos, 1) || level.getBlockState(pos.above()).is(Blocks.CACTUS) || ConspicuousCactus.randomDecay(level, pos)) return;
        if (random.nextFloat() < .9F) return;
        // Badlands retain modern cactus farming; the source's desert remains the normal habitat.
        if (!level.getBiome(pos).is(BiomeTags.IS_BADLANDS) && !level.getBiome(pos).is(net.minecraft.world.level.biome.Biomes.DESERT)) {
            level.destroyBlock(pos, true); return;
        }
        if (!level.isEmptyBlock(pos.above()) || !Blocks.CACTUS.defaultBlockState().canSurvive(level, pos.above())) return;
        int height = 1;
        while (level.getBlockState(pos.below(height)).is(Blocks.CACTUS)) height++;
        if (height >= cactusHeight(level, pos.below(height)) || !CommonHooks.canCropGrow(level, pos.above(), state, true)) return;
        int age = state.getValue(CactusBlock.AGE);
        if (age == 15) {
            level.setBlockAndUpdate(pos.above(), Blocks.CACTUS.defaultBlockState());
            level.setBlock(pos, state.setValue(CactusBlock.AGE, 0), 4);
        } else level.setBlock(pos, state.setValue(CactusBlock.AGE, age + 1), 4);
        CommonHooks.fireCropGrowPost(level, pos, state);
    }
}
