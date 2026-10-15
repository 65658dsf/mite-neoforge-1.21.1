package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.CubeCropBlight;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual native precipitation and fluid spreading preserve crop destruction causes. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class CropWeatherDestructionGameTests {
    @GameTest(template = "empty")
    public static void realSnowfallHarvestsMatureCropBeforeReplacingItWithSnow(GameTestHelper h) {
        try (var f = new Fixture(h);
                var spawned = new SpawnedEntityCapture(f.level, entity -> entity instanceof ItemEntity
                        && entity.getBoundingBox().intersects(new AABB(f.pos).inflate(2)))) {
            f.set(f.pos.below(), Blocks.FARMLAND.defaultBlockState());
            f.set(f.pos, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
            h.assertTrue(Blocks.SNOW.defaultBlockState().canSurvive(f.level, f.pos), "Source farmland supports snow despite modern shortened collision shape");
            f.level.tickPrecipitation(f.pos);
            h.assertTrue(f.level.getBlockState(f.pos).is(Blocks.SNOW), "The actual native precipitation tick replaces a crop with snow");
            int wheat = spawned.live(ItemEntity.class).stream().filter(entity -> entity.getItem().is(Items.WHEAT))
                    .mapToInt(entity -> entity.getItem().getCount()).sum();
            h.assertValueEqual(wheat, 1, "A mature snowfall crop retains its source output");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void snowfallReturnsAgeZeroStemSeedBeforeCheckingWhetherStemIsDead(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            for (boolean dead : new boolean[]{false, true}) {
                f.set(f.pos.below(), Blocks.FARMLAND.defaultBlockState());
                f.set(f.pos, Blocks.MELON_STEM.defaultBlockState().setValue(StemBlock.AGE, 0).setValue(CubeCropBlight.DEAD, dead));
                f.level.tickPrecipitation(f.pos);
                h.assertTrue(f.level.getBlockState(f.pos).is(Blocks.SNOW), "Snow replaces both live and dead source stem states");
                h.assertValueEqual(f.collect(Items.MELON_SEEDS), 1, "Source snowfall age-zero exception runs before the dead-stem guard");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void snowfallRequiresActualSnowClimateRainAccumulationAndExposure(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            f.set(f.pos.below(), Blocks.FARMLAND.defaultBlockState()); f.set(f.pos, Blocks.WHEAT.defaultBlockState());
            f.level.getGameRules().getRule(GameRules.RULE_SNOW_ACCUMULATION_HEIGHT).set(0, f.level.getServer());
            f.level.tickPrecipitation(f.pos);
            h.assertTrue(f.level.getBlockState(f.pos).is(Blocks.WHEAT), "Disabled snow accumulation does not destroy crops");
            f.level.getGameRules().getRule(GameRules.RULE_SNOW_ACCUMULATION_HEIGHT).set(1, f.level.getServer());
            f.level.setRainLevel(0F); f.level.tickPrecipitation(f.pos);
            h.assertTrue(f.level.getBlockState(f.pos).is(Blocks.WHEAT), "Cold clear weather is not simulated frost damage");
            f.level.setRainLevel(1F); f.setBiome(false); f.level.tickPrecipitation(f.pos);
            h.assertTrue(f.level.getBlockState(f.pos).is(Blocks.WHEAT), "Actual warm rain does not invoke snowfall destruction");
            f.setBiome(true); f.set(f.pos.above(2), Blocks.STONE.defaultBlockState()); f.level.tickPrecipitation(f.pos);
            h.assertTrue(f.level.getBlockState(f.pos).is(Blocks.WHEAT), "A roof receives the snowfall and protects the crop below");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void actualWaterSpreadDestroysAgeZeroCropWithoutReturningItsSeed(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            f.flowInto(Blocks.WHEAT.defaultBlockState());
            h.assertTrue(f.level.getFluidState(f.pos).is(FluidTags.WATER), "Real water-fluid spreading reaches the crop cell");
            h.assertValueEqual(f.collect(Items.WHEAT_SEEDS), 0, "Flood reason suppresses the age-zero self-drop seed");
            Block.dropResources(Blocks.WHEAT.defaultBlockState(), f.level, f.pos);
            h.assertValueEqual(f.collect(Items.WHEAT_SEEDS), 1, "Flood reason is cleared after native water destruction returns");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void actualWaterSpreadRetainsOnlyTheSourcePoisonousPotatoException(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            f.flowInto(Blocks.POTATOES.defaultBlockState().setValue(CropBlock.AGE, 2));
            h.assertTrue(f.level.getFluidState(f.pos).is(FluidTags.WATER), "Flow physically replaces an intermediate potato plant");
            h.assertValueEqual(f.collect(Items.POISONOUS_POTATO), 1, "The source potato override survives the flood reason filter");
            f.flowInto(Blocks.CARROTS.defaultBlockState());
            h.assertValueEqual(f.collect(Items.CARROT), 0, "Ordinary root-crop planting items are not recovered from flood destruction");
        }
        h.succeed();
    }

    private static final class Fixture implements AutoCloseable {
        final ServerLevel level; final BlockPos pos; final Map<BlockPos, BlockState> saved = new LinkedHashMap<>();
        final Runnable restoreBiome; final java.util.function.Consumer<Boolean> changeBiome;
        final float rain, thunder; final int snow; final boolean drops;
        Fixture(GameTestHelper h) {
            level = h.getLevel(); var reference = h.absolutePos(new BlockPos(2, 2, 2));
            pos = new BlockPos((reference.getX() & ~15) + 8, 300, (reference.getZ() & ~15) + 8);
            rain = level.getRainLevel(1F); thunder = level.getThunderLevel(1F);
            snow = level.getGameRules().getInt(GameRules.RULE_SNOW_ACCUMULATION_HEIGHT);
            drops = level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS);
            level.setRainLevel(1F); level.setThunderLevel(0F);
            level.getGameRules().getRule(GameRules.RULE_SNOW_ACCUMULATION_HEIGHT).set(1, level.getServer());
            level.getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS).set(true, level.getServer());
            var chunk = level.getChunkAt(pos); var section = chunk.getSection(chunk.getSectionIndex(pos.getY())); var old = section.getBiomes();
            int x = QuartPos.fromBlock(chunk.getPos().getMinBlockX()), y = QuartPos.fromBlock(pos.getY()) & ~3,
                    z = QuartPos.fromBlock(chunk.getPos().getMinBlockZ()); var sampler = level.getChunkSource().randomState().sampler();
            var registry = level.registryAccess().registryOrThrow(Registries.BIOME);
            changeBiome = cold -> section.fillBiomesFromNoise((qx, qy, qz, climate) -> registry.getHolderOrThrow(cold ? Biomes.SNOWY_PLAINS : Biomes.FOREST), sampler, x, y, z);
            restoreBiome = () -> section.fillBiomesFromNoise((qx, qy, qz, climate) -> old.get(qx & 3, qy & 3, qz & 3), sampler, x, y, z);
            setBiome(true);
            for (var cell : BlockPos.betweenClosed(pos.offset(-3, -1, -2), pos.offset(2, 3, 2))) set(cell, Blocks.AIR.defaultBlockState());
        }
        void setBiome(boolean cold) { changeBiome.accept(cold); }
        void set(BlockPos p, BlockState state) { saved.putIfAbsent(p.immutable(), level.getBlockState(p)); level.setBlock(p, state, 2); }
        void flowInto(BlockState crop) {
            clearDrops();
            for (var cell : BlockPos.betweenClosed(pos.offset(-3, -1, -2), pos.offset(2, -1, 2))) set(cell, Blocks.STONE.defaultBlockState());
            var source = pos.west();
            set(pos.below(), Blocks.FARMLAND.defaultBlockState()); set(pos, crop);
            set(source.west(), Blocks.STONE.defaultBlockState()); set(source.north(), Blocks.STONE.defaultBlockState());
            set(source.south(), Blocks.STONE.defaultBlockState()); set(source, Blocks.WATER.defaultBlockState());
            level.getFluidState(source).tick(level, source);
        }
        int collect(net.minecraft.world.item.Item item) {
            int count = 0;
            for (var entity : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2))) {
                if (entity.getItem().is(item)) count += entity.getItem().getCount(); entity.discard();
            }
            return count;
        }
        void clearDrops() { level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3)).forEach(ItemEntity::discard); }
        @Override public void close() {
            clearDrops(); saved.forEach((p, state) -> level.setBlock(p, state, 2)); restoreBiome.run();
            level.setRainLevel(rain); level.setThunderLevel(thunder);
            level.getGameRules().getRule(GameRules.RULE_SNOW_ACCUMULATION_HEIGHT).set(snow, level.getServer());
            level.getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS).set(drops, level.getServer());
        }
    }
}
