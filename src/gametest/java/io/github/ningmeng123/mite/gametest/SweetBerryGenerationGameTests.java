package io.github.ningmeng123.mite.gametest;

import com.mojang.serialization.JsonOps;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.RandomPatchConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.SimpleBlockConfiguration;
import net.minecraft.world.level.levelgen.placement.RarityFilter;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real datapack registries retain native patches and use the native rarity lottery. @author NingMeng123 */
@GameTestHolder("mite_gameplay")
@PrefixGameTestTemplate(false)
public final class SweetBerryGenerationGameTests {
    private SweetBerryGenerationGameTests() {}

    @GameTest(template = "empty")
    public static void loadedNaturalEntrypointsKeepNativePatchContentsAndTaigaDistribution(GameTestHelper helper) {
        var registry = helper.getLevel().registryAccess();
        var configured = registry.registryOrThrow(Registries.CONFIGURED_FEATURE)
                .get(ResourceLocation.withDefaultNamespace("patch_berry_bush"));
        helper.assertTrue(configured != null && configured.feature() == Feature.RANDOM_PATCH
                        && configured.config() instanceof RandomPatchConfiguration,
                "The native configured berry patch is retained rather than replaced");
        var patch = (RandomPatchConfiguration) configured.config();
        helper.assertValueEqual(patch.tries(), 96, "Every accepted patch retains all ninety-six native attempts");
        helper.assertTrue(patch.xzSpread() == 7 && patch.ySpread() == 3, "The native patch spreads stay unchanged");
        var simple = patch.feature().value().feature().value();
        helper.assertTrue(simple.feature() == Feature.SIMPLE_BLOCK && simple.config() instanceof SimpleBlockConfiguration,
                "Each accepted patch still places the native simple block");
        var state = ((SimpleBlockConfiguration) simple.config()).toPlace().getState(RandomSource.create(0L), BlockPos.ZERO);
        helper.assertTrue(state.is(Blocks.SWEET_BERRY_BUSH) && state.getValue(SweetBerryBushBlock.AGE) == 3,
                "Accepted natural patches retain mature vanilla sweet berry bushes");
        var placed = registry.registryOrThrow(Registries.PLACED_FEATURE);
        for (String name : Set.of("patch_berry_common", "patch_berry_rare", "patch_berry_bush"))
            helper.assertTrue(placed.get(ResourceLocation.withDefaultNamespace(name)).feature().value() == configured,
                    "The loaded wild or village entry retains the same native configured feature: " + name);
        for (var entry : Map.of(
                "patch_berry_common", Set.of("minecraft:taiga", "minecraft:old_growth_pine_taiga", "minecraft:old_growth_spruce_taiga"),
                "patch_berry_rare", Set.of("minecraft:snowy_taiga"),
                "patch_berry_bush", Set.<String>of()).entrySet()) {
            var key = ResourceKey.create(Registries.PLACED_FEATURE, ResourceLocation.withDefaultNamespace(entry.getKey()));
            Set<String> actual = new HashSet<>();
            registry.registryOrThrow(Registries.BIOME).holders().forEach(biome -> {
                if (biome.value().getGenerationSettings().features().stream().flatMap(features -> features.stream())
                        .anyMatch(feature -> feature.is(key)))
                    actual.add(biome.unwrapKey().orElseThrow().location().toString());
            });
            helper.assertValueEqual(actual, entry.getValue(), "Native biome membership stays unchanged: " + entry.getKey());
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeRarityFiltersKeepExactlyOneOfTenOriginalBerryLotteryIntervals(GameTestHelper helper) {
        var placed = helper.getLevel().registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
        for (var entry : Map.of("minecraft:patch_berry_common", 32, "minecraft:patch_berry_rare", 384,
                "minecraft:patch_berry_bush", 1, "mite:blueberry_patch", 5).entrySet()) {
            var feature = placed.get(ResourceLocation.parse(entry.getKey()));
            helper.assertTrue(feature.placement().getFirst() instanceof RarityFilter, "The loaded entry uses native rarity: " + entry.getKey());
            RarityFilter reduced = (RarityFilter) feature.placement().getFirst();
            int expectedChance = entry.getValue() * 10;
            int loadedChance = RarityFilter.CODEC.codec().encodeStart(JsonOps.INSTANCE, reduced).getOrThrow()
                    .getAsJsonObject().get("chance").getAsInt();
            helper.assertValueEqual(loadedChance, expectedChance, "The runtime registry loaded the tenfold rarity denominator");
            RarityFilter original = RarityFilter.onAverageOnceEvery(entry.getValue());
            int originalAccepted = 0;
            int reducedAccepted = 0;
            FixedFloatRandom random = new FixedFloatRandom();
            for (int sample = 0; sample < expectedChance; sample++) {
                random.value = (sample + .5F) / expectedChance;
                if (original.getPositions(null, random, BlockPos.ZERO).findAny().isPresent()) originalAccepted++;
                if (reduced.getPositions(null, random, BlockPos.ZERO).findAny().isPresent()) reducedAccepted++;
            }
            helper.assertValueEqual(originalAccepted, 10, "The original native lottery accepts ten equally sized probe intervals");
            helper.assertValueEqual(reducedAccepted, 1, "The reduced native lottery accepts only one of those ten intervals");
            random.value = Math.nextDown(1F / expectedChance);
            helper.assertTrue(reduced.getPositions(null, random, BlockPos.ZERO).findAny().isPresent(), "The native rarity threshold accepts the lower boundary");
            random.value = 1F / expectedChance;
            helper.assertTrue(reduced.getPositions(null, random, BlockPos.ZERO).findAny().isEmpty(), "The native rarity threshold rejects equality");
        }
        helper.succeed();
    }

    private static final class FixedFloatRandom extends LegacyRandomSource {
        private float value;
        private FixedFloatRandom() { super(0L); }
        @Override public float nextFloat() { return value; }
    }
}
