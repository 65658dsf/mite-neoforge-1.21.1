package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.registry.MiteWorldBlocks;
import io.github.ningmeng123.mite.config.MiteConfig;
import java.util.HashMap;
import java.util.Map;
import net.neoforged.neoforge.common.ModConfigSpec;
import io.github.ningmeng123.mite.rules.R196OreRules;
import io.github.ningmeng123.mite.worldgen.MiteWorldDimensions;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** All-terrain emerald candidates retain their native height, substrate and dimension boundaries. @author NingMeng123 */
@GameTestHolder("mite_emerald")
@PrefixGameTestTemplate(false)
public final class EmeraldGenerationGameTests {
    private static final ChunkPos ORIGIN = new ChunkPos(6480, -6480);
    private static final long SEED = 196L;

    private EmeraldGenerationGameTests() {}

    @GameTest(template = "empty")
    public static void everyOverworldBiomeRetainsExactlyOneSharedOreEntryWithoutDuplicateVanillaEmeralds(GameTestHelper helper) {
        int checked = 0;
        for (Holder.Reference<Biome> biome : helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME).holders().toList()) {
            var features = biome.value().getGenerationSettings().features().stream().flatMap(set -> set.stream())
                    .flatMap(feature -> feature.unwrapKey().stream()).map(ResourceKey::location).toList();
            long entries = features.stream().filter(id -> id.equals(ResourceLocation.parse("mite:ores_overworld"))).count();
            if (biome.is(BiomeTags.IS_OVERWORLD)) {
                checked++;
                helper.assertValueEqual(entries, 1L, "Every overworld biome has one actual shared emerald candidate entry: " + biome.key().location());
                helper.assertFalse(features.contains(ResourceLocation.withDefaultNamespace("ore_emerald")), "Vanilla mountain emeralds cannot duplicate the unchanged candidate budget");
            } else if (biome.is(BiomeTags.IS_NETHER) || biome.is(BiomeTags.IS_END))
                helper.assertValueEqual(entries, 0L, "Removing terrain restrictions never adds the overworld ore entry to another dimension's biomes");
        }
        helper.assertTrue(checked >= 4, "The loaded registry check covers real modern overworld biomes");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void plainsDesertOceanAndFormerMountainBiomeGenerateTheSameRealUnlitEmeraldCandidates(GameTestHelper helper) {
        var kind = R196OreRules.Kind.EMERALD;
        var regular = MiteConfig.OVERWORLD_ORES.get(kind);
        var deep = MiteConfig.OVERWORLD_DEEPSLATE_ORES.get(kind);
        var regularHeight = MiteConfig.OVERWORLD_ORE_HEIGHTS.get(kind);
        var deepHeight = MiteConfig.OVERWORLD_DEEPSLATE_ORE_HEIGHTS.get(kind);
        double regularBefore = regular.get(), deepBefore = deep.get();
        String regularHeightBefore = regularHeight.get(), deepHeightBefore = deepHeight.get();
        try {
            // Use the original candidate budget and height distribution to compare biomes.
            regular.set(1D);
            deep.set(1D);
            regularHeight.set("");
            deepHeight.set("");
            MiteConfig.refreshEnchantmentRules();
            Set<BlockPos> reference = null;
            for (var biome : List.of(Biomes.PLAINS, Biomes.DESERT, Biomes.OCEAN, Biomes.WINDSWEPT_HILLS)) {
                var fixture = new OreGenerationGameTests.GenerationFixture(helper.getLevel(), ORIGIN, false, biome);
                fixture.fill(-64, 129);
                helper.assertTrue(fixture.region.getBiome(ORIGIN.getWorldPosition().offset(8, 0, 8)).is(biome), "The actual proto-chunk biome is the requested test terrain");
                fixture.place("ores_overworld", SEED);
                Set<BlockPos> found = emeralds(fixture);
                helper.assertTrue(!found.isEmpty() && found.size() <= 8, "Each terrain has real emerald opportunities within the unchanged three-to-eight candidate budget: " + biome.location());
                int minimum = R196OreRules.modernY(R196OreRules.Dimension.OVERWORLD, R196OreRules.minHeight(R196OreRules.Kind.EMERALD, R196OreRules.Dimension.OVERWORLD));
                int maximum = R196OreRules.modernY(R196OreRules.Dimension.OVERWORLD, R196OreRules.maxHeight(R196OreRules.Kind.EMERALD, R196OreRules.Dimension.OVERWORLD));
                for (BlockPos position : found) {
                    helper.assertTrue(position.getY() >= minimum && position.getY() <= maximum, "All-terrain generation retains the original emerald Y range");
                    helper.assertTrue(fixture.region.getBlockState(position).is(Blocks.DEEPSLATE_EMERALD_ORE), "Real negative-height deepslate becomes the correct deep emerald variant");
                }
                if (reference == null) reference = found;
                else helper.assertValueEqual(found, reference, "Changing only the biome does not change candidate placement or inflate the old mountain ore rate");
                helper.assertFalse(fixture.center.isLightCorrect(), "Actual feature placement takes place on an unlit proto chunk");
            }
        } finally {
            regular.set(regularBefore);
            deep.set(deepBefore);
            regularHeight.set(regularHeightBefore);
            deepHeight.set(deepHeightBefore);
            MiteConfig.refreshEnchantmentRules();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void ordinaryStoneCanProduceEmeraldsButAirFluidsAndProtectedBlocksCannot(GameTestHelper helper) {
        var stone = new OreGenerationGameTests.GenerationFixture(helper.getLevel(), ORIGIN, false, Biomes.DESERT);
        fillCenter(stone, Blocks.STONE.defaultBlockState());
        stone.place("ores_overworld", SEED);
        Set<BlockPos> found = emeralds(stone);
        helper.assertFalse(found.isEmpty(), "Eligible ordinary stone retains the regular emerald variant even outside mountain terrain");
        for (BlockPos position : found) helper.assertTrue(stone.region.getBlockState(position).is(Blocks.EMERALD_ORE), "Stone is never converted into a deepslate-looking emerald");
        for (BlockState protectedState : List.of(Blocks.AIR.defaultBlockState(), Blocks.WATER.defaultBlockState(),
                Blocks.LAVA.defaultBlockState(), Blocks.BEDROCK.defaultBlockState(), MiteWorldBlocks.MANTLE.get().defaultBlockState())) {
            var fixture = new OreGenerationGameTests.GenerationFixture(helper.getLevel(), ORIGIN, false, Biomes.OCEAN);
            fillCenter(fixture, protectedState);
            fixture.place("ores_overworld", SEED);
            helper.assertTrue(emeralds(fixture).isEmpty(), "Removing biome restrictions cannot place emeralds inside air, fluids or protected blocks");
            var origin = ORIGIN.getWorldPosition();
            for (int offsetX = 0; offsetX < 16; offsetX++) for (int offsetZ = 0; offsetZ < 16; offsetZ++) for (int height = -64; height < 129; height++)
                helper.assertValueEqual(fixture.center.getBlockState(origin.offset(offsetX, height, offsetZ)), protectedState, "The existing rock-replacement protection remains intact");
            helper.assertFalse(fixture.center.isLightCorrect(), "Protected substrate checks also use genuine unlit proto chunks");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void overworldBiomeChangesKeepNetherUnchangedAndModernUnderworldOresIndependent(GameTestHelper helper) {
        helper.assertFalse(R196OreRules.UNDERWORLD.contains(R196OreRules.Kind.EMERALD)
                        || R196OreRules.UNDERWORLD.contains(R196OreRules.Kind.COAL),
                "The archived R196 underworld table still contains neither modern addition");
        helper.assertTrue(R196OreRules.configurableKinds(R196OreRules.Dimension.UNDERWORLD)
                        .containsAll(List.of(R196OreRules.Kind.COAL, R196OreRules.Kind.EMERALD)),
                "The current underworld catalog explicitly includes the separately configured modern additions");
        helper.assertFalse(R196OreRules.generationKinds(R196OreRules.Dimension.NETHER)
                        .stream().anyMatch(kind -> kind == R196OreRules.Kind.COAL || kind == R196OreRules.Kind.EMERALD),
                "The Nether generation catalog still gains neither coal nor emerald");
        var nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        var underworld = helper.getLevel().getServer().getLevel(MiteWorldDimensions.UNDERWORLD);
        helper.assertTrue(nether != null && underworld != null, "Both registered target dimensions are available");
        var netherFixture = new OreGenerationGameTests.GenerationFixture(nether, ORIGIN, true);
        netherFixture.fill(-64,129); netherFixture.place("ores_nether",SEED);
        helper.assertTrue(emeralds(netherFixture).isEmpty(), "Neither overworld biome eligibility nor the new underworld catalog adds Nether emeralds");
        Map<ModConfigSpec.DoubleValue,Double> values = new HashMap<>();
        Map<ModConfigSpec.ConfigValue<String>,String> heights = new HashMap<>();
        for (var kind : List.of(R196OreRules.Kind.COAL,R196OreRules.Kind.EMERALD)) {
            for (var settings : List.of(MiteConfig.OVERWORLD_ORES,MiteConfig.OVERWORLD_DEEPSLATE_ORES,
                    MiteConfig.UNDERWORLD_ORES,MiteConfig.UNDERWORLD_DEEPSLATE_ORES,
                    MiteConfig.UNDERWORLD_ORE_CANDIDATES,MiteConfig.UNDERWORLD_ORE_CHANCES))
                values.put(settings.get(kind),settings.get(kind).get());
            for (var settings : List.of(MiteConfig.UNDERWORLD_ORE_HEIGHTS,MiteConfig.UNDERWORLD_DEEPSLATE_ORE_HEIGHTS))
                heights.put(settings.get(kind),settings.get(kind).get());
        }
        try {
            for (var kind : List.of(R196OreRules.Kind.COAL,R196OreRules.Kind.EMERALD)) {
                MiteConfig.OVERWORLD_ORES.get(kind).set(0D); MiteConfig.OVERWORLD_DEEPSLATE_ORES.get(kind).set(0D);
                MiteConfig.UNDERWORLD_ORE_CANDIDATES.get(kind).set(1000D); MiteConfig.UNDERWORLD_ORE_CHANCES.get(kind).set(100D);
                MiteConfig.UNDERWORLD_ORE_HEIGHTS.get(kind).set("[-64,319]");
                MiteConfig.UNDERWORLD_DEEPSLATE_ORE_HEIGHTS.get(kind).set("[-64,319]");
            }
            for (boolean enabled : new boolean[]{false,true}) {
                for (var kind : List.of(R196OreRules.Kind.COAL,R196OreRules.Kind.EMERALD)) {
                    MiteConfig.UNDERWORLD_ORES.get(kind).set(enabled?1D:0D);
                    MiteConfig.UNDERWORLD_DEEPSLATE_ORES.get(kind).set(enabled?1D:0D);
                }
                MiteConfig.refreshEnchantmentRules();
                var fixture = new OreGenerationGameTests.GenerationFixture(underworld,ORIGIN,false);
                fixture.fill(-64,129); fixture.place("ores_underworld",SEED);
                helper.assertTrue(!emeralds(fixture).isEmpty()==enabled,
                        "Underworld emerald admission follows its own switch even while overworld emeralds are disabled");
                boolean coal=false;
                var origin=ORIGIN.getWorldPosition();
                for(int x=0;x<16;x++)for(int z=0;z<16;z++)for(int y=-64;y<129;y++) {
                    var state=fixture.center.getBlockState(origin.offset(x,y,z));
                    coal|=state.is(Blocks.COAL_ORE)||state.is(Blocks.DEEPSLATE_COAL_ORE);
                }
                helper.assertTrue(coal==enabled,"Underworld coal admission follows its own switch independently of the overworld tab");
                helper.assertFalse(fixture.center.isLightCorrect(),"Dimension checks use genuine unlit feature placement");
            }
        } finally {
            values.forEach(ModConfigSpec.DoubleValue::set); heights.forEach(ModConfigSpec.ConfigValue::set);
            MiteConfig.refreshEnchantmentRules();
        }
        helper.succeed();
    }

    private static void fillCenter(OreGenerationGameTests.GenerationFixture fixture, BlockState state) {
        var origin = fixture.center.getPos().getWorldPosition();
        for (int offsetX = 0; offsetX < 16; offsetX++) for (int offsetZ = 0; offsetZ < 16; offsetZ++) for (int height = -64; height < 129; height++)
            fixture.center.setBlockState(origin.offset(offsetX, height, offsetZ), state, false);
    }

    private static Set<BlockPos> emeralds(OreGenerationGameTests.GenerationFixture fixture) {
        Set<BlockPos> found = new HashSet<>();
        var origin = fixture.center.getPos().getWorldPosition();
        for (int offsetX = 0; offsetX < 16; offsetX++) for (int offsetZ = 0; offsetZ < 16; offsetZ++) for (int height = -64; height < 129; height++) {
            BlockPos position = origin.offset(offsetX, height, offsetZ);
            var state = fixture.center.getBlockState(position);
            if (state.is(Blocks.EMERALD_ORE) || state.is(Blocks.DEEPSLATE_EMERALD_ORE)) found.add(position);
        }
        return Set.copyOf(found);
    }
}
