package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.network.GameplayConfigPayload;
import io.github.ningmeng123.mite.rules.R196OreRules;
import io.github.ningmeng123.mite.rules.R196OreRules.Dimension;
import io.github.ningmeng123.mite.rules.R196OreRules.Kind;
import io.github.ningmeng123.mite.rules.OreHeightRange;
import io.netty.buffer.Unpooled;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Independent deep ore editing, migration and actual pre-light placement. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class DeepslateOreConfigGameTests {
    private DeepslateOreConfigGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void independentHeightRangesSaveSyncAndConstrainEntireGeneratedVeins(GameTestHelper helper) {
        try (var ignored = new Settings()) {
            var regular = regularValues();
            helper.assertTrue(MiteConfig.saveOreMultipliers(regular, Map.of(),
                    Map.of(Dimension.OVERWORLD, Map.of(Kind.COPPER, "[20,22]")),
                    Map.of(Dimension.OVERWORLD, Map.of(Kind.COPPER, "[-40,-38]"))), "Separate actual Y ranges save atomically");
            helper.assertValueEqual(MiteConfig.oreHeightRange(Kind.COPPER, Dimension.OVERWORLD, false), new OreHeightRange(20, 22), "Regular copper height");
            helper.assertValueEqual(MiteConfig.oreHeightRange(Kind.COPPER, Dimension.OVERWORLD, true), new OreHeightRange(-40, -38), "Deep copper height");
            var snapshot = MiteConfig.localSnapshot();
            var bytes = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
            try {
                GameplayConfigPayload.CODEC.encode(bytes, new GameplayConfigPayload(snapshot));
                var decoded = GameplayConfigPayload.CODEC.decode(bytes).snapshot();
                helper.assertValueEqual(decoded, snapshot, "Actual payload retains both height maps");
                MiteConfig.receiveServerSnapshot(decoded);
                helper.assertValueEqual(MiteConfig.oreHeightRange(Kind.COPPER, Dimension.OVERWORLD, true), new OreHeightRange(-40, -38), "Remote ore page sees server heights");
            } finally { bytes.release(); MiteConfig.clearServerSnapshot(); }
            helper.assertFalse(MiteConfig.saveOreMultipliers(regular, Map.of(),
                    Map.of(Dimension.OVERWORLD, Map.of(Kind.COPPER, "[100,-100]")), Map.of()), "Reversed ranges are rejected before any settings mutate");
            helper.assertTrue(MiteConfig.localSnapshot() == snapshot, "Rejected height draft cannot publish a partial revision");

            MiteConfig.OVERWORLD_ORES.values().forEach(setting -> setting.set(0D));
            MiteConfig.OVERWORLD_DEEPSLATE_ORES.values().forEach(setting -> setting.set(0D));
            MiteConfig.OVERWORLD_ORES.get(Kind.COPPER).set(1D);
            MiteConfig.OVERWORLD_DEEPSLATE_ORES.get(Kind.COPPER).set(1D);
            MiteConfig.OVERWORLD_ORE_CANDIDATES.get(Kind.COPPER).set(1000D);
            MiteConfig.OVERWORLD_ORE_CHANCES.get(Kind.COPPER).set(100D);
            MiteConfig.refreshEnchantmentRules();
            var fixture = new OreGenerationGameTests.GenerationFixture(helper.getLevel(), new ChunkPos(6412, -6410), false);
            fixture.fill(-64, 129);
            fixture.place("ores_overworld", 196L);
            int regularCount = 0, deepCount = 0;
            for (var chunk : fixture.all) {
                var position = new BlockPos.MutableBlockPos();
                int startX = chunk.getPos().getMinBlockX(), startZ = chunk.getPos().getMinBlockZ();
                for (int offsetX = 0; offsetX < 16; offsetX++) for (int offsetZ = 0; offsetZ < 16; offsetZ++) for (int height = -64; height < 129; height++) {
                    var state = chunk.getBlockState(position.set(startX + offsetX, height, startZ + offsetZ));
                    if (state.is(Blocks.COPPER_ORE)) {
                        helper.assertTrue(height >= 20 && height <= 22, "Entire regular vein stays inside inclusive configured bounds");
                        regularCount++;
                    }
                    if (state.is(Blocks.DEEPSLATE_COPPER_ORE)) {
                        helper.assertTrue(height >= -40 && height <= -38, "Entire deep vein stays inside inclusive configured bounds");
                        deepCount++;
                    }
                }
            }
            helper.assertTrue(regularCount > 0 && deepCount > 0, "Different configured bands both generate real ore");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void deepRatesInheritOldFilesUntilEditedAndSaveWithoutChangingAdvancedRules(GameTestHelper helper) {
        try (var ignored = new Settings()) {
            MiteConfig.UNDERWORLD_ORES.get(Kind.SILVER).set(2D);
            MiteConfig.UNDERWORLD_DEEPSLATE_ORES.get(Kind.SILVER).set(-1D);
            MiteConfig.UNDERWORLD_ORE_CANDIDATES.get(Kind.SILVER).set(123.5D);
            MiteConfig.UNDERWORLD_ORE_CHANCES.get(Kind.SILVER).set(37.5D);
            MiteConfig.refreshEnchantmentRules();
            equal(helper, MiteConfig.oreMultiplier(Kind.SILVER, Dimension.UNDERWORLD, true), 2D, "An old file keeps its deep silver multiplier");
            var regular = regularValues();
            regular.get(Dimension.UNDERWORLD).put(Kind.SILVER, 3D);
            helper.assertTrue(MiteConfig.saveOreMultipliers(regular), "The existing regular-only API stays compatible");
            equal(helper, MiteConfig.deepslateOreMultiplierOverride(Kind.SILVER, Dimension.UNDERWORLD), -1D, "Saving an untouched deep row preserves inheritance");
            equal(helper, MiteConfig.oreMultiplier(Kind.SILVER, Dimension.UNDERWORLD, true), 3D, "Inherited deep rate follows a changed legacy setting");

            var deep = Map.of(Dimension.UNDERWORLD, Map.of(Kind.SILVER, .5D));
            helper.assertTrue(MiteConfig.saveOreMultipliers(regular, deep), "An explicitly edited deep rate saves independently");
            MiteConfig.refreshEnchantmentRules();
            equal(helper, MiteConfig.oreMultiplier(Kind.SILVER, Dimension.UNDERWORLD), 3D, "Deep save preserves regular silver");
            equal(helper, MiteConfig.oreMultiplier(Kind.SILVER, Dimension.UNDERWORLD, true), .5D, "Deep override survives snapshot reload");
            equal(helper, MiteConfig.UNDERWORLD_DEEPSLATE_ORES.get(Kind.SILVER).get(), .5D, "Deep override is written to the persistent config setting");
            equal(helper, MiteConfig.oreBaseCandidates(Kind.SILVER, Dimension.UNDERWORLD), 123.5D, "Advanced candidate base is preserved");
            equal(helper, MiteConfig.orePassChance(Kind.SILVER, Dimension.UNDERWORLD), .375D, "Advanced pass probability is preserved");
            var before = MiteConfig.localSnapshot();
            helper.assertFalse(MiteConfig.saveOreMultipliers(regular, Map.of(Dimension.UNDERWORLD, Map.of(Kind.SILVER, -.5D))), "Invalid negative deep drafts are rejected");
            helper.assertTrue(before == MiteConfig.localSnapshot(), "Rejected deep draft cannot partially publish other rows");

            var bytes = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
            try {
                GameplayConfigPayload.CODEC.encode(bytes, new GameplayConfigPayload(before));
                var decoded = GameplayConfigPayload.CODEC.decode(bytes).snapshot();
                helper.assertValueEqual(decoded, before, "The existing payload preserves deep multiplier keys");
                MiteConfig.receiveServerSnapshot(decoded);
                equal(helper, MiteConfig.oreMultiplier(Kind.SILVER, Dimension.UNDERWORLD, true), .5D, "Remote settings see the independent deep multiplier");
            } finally { bytes.release(); MiteConfig.clearServerSnapshot(); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void actualFeatureCanEnableEitherOreSubstrateWithoutGeneratingTheOther(GameTestHelper helper) {
        try (var ignored = new Settings()) {
            MiteConfig.OVERWORLD_ORES.values().forEach(setting -> setting.set(0D));
            MiteConfig.OVERWORLD_DEEPSLATE_ORES.values().forEach(setting -> setting.set(0D));
            MiteConfig.OVERWORLD_ORE_CANDIDATES.get(Kind.COPPER).set(1000D);
            MiteConfig.OVERWORLD_ORE_CHANCES.get(Kind.COPPER).set(100D);
            for (boolean deepOnly : new boolean[]{true, false}) {
                MiteConfig.OVERWORLD_ORES.get(Kind.COPPER).set(deepOnly ? 0D : 1D);
                MiteConfig.OVERWORLD_DEEPSLATE_ORES.get(Kind.COPPER).set(deepOnly ? 1D : 0D);
                MiteConfig.refreshEnchantmentRules();
                var fixture = new OreGenerationGameTests.GenerationFixture(helper.getLevel(), new ChunkPos(deepOnly ? 6410 : 6411, -6410), false);
                fixture.fill(-64, 129);
                fixture.place("ores_overworld", 196L);
                int enabled = 0;
                for (var chunk : fixture.all) {
                    int x = chunk.getPos().getMinBlockX(), z = chunk.getPos().getMinBlockZ();
                    for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) for (int y = -64; y < 129; y++) {
                        var state = chunk.getBlockState(new BlockPos(x + dx, y, z + dz));
                        helper.assertFalse(state.is(deepOnly ? Blocks.COPPER_ORE : Blocks.DEEPSLATE_COPPER_ORE), "A disabled substrate cannot receive copper, including cross-boundary vein growth");
                        if (state.is(deepOnly ? Blocks.DEEPSLATE_COPPER_ORE : Blocks.COPPER_ORE)) enabled++;
                    }
                }
                helper.assertTrue(enabled > 0, "The enabled copper variant must actually generate: deepOnly=" + deepOnly);
            }
        }
        helper.succeed();
    }

    private static Map<Dimension, Map<Kind, Double>> regularValues() {
        var values = new EnumMap<Dimension, Map<Kind, Double>>(Dimension.class);
        for (var dimension : List.of(Dimension.OVERWORLD, Dimension.UNDERWORLD)) {
            var minerals = new EnumMap<Kind, Double>(Kind.class);
            R196OreRules.configurableKinds(dimension).forEach(kind -> minerals.put(kind, MiteConfig.oreMultiplier(kind, dimension)));
            values.put(dimension, minerals);
        }
        return values;
    }

    private static void equal(GameTestHelper helper, double actual, double expected, String label) {
        helper.assertTrue(actual == expected, label + ": " + actual + " vs " + expected);
    }

    private static final class Settings implements AutoCloseable {
        private final Map<ModConfigSpec.DoubleValue, Double> values = new java.util.HashMap<>();
        private final Map<ModConfigSpec.ConfigValue<String>, String> heights = new java.util.HashMap<>();
        private Settings() {
            for (var settings : List.of(MiteConfig.OVERWORLD_ORES, MiteConfig.UNDERWORLD_ORES,
                    MiteConfig.OVERWORLD_DEEPSLATE_ORES, MiteConfig.UNDERWORLD_DEEPSLATE_ORES,
                    MiteConfig.OVERWORLD_ORE_CANDIDATES, MiteConfig.UNDERWORLD_ORE_CANDIDATES,
                    MiteConfig.OVERWORLD_ORE_CHANCES, MiteConfig.UNDERWORLD_ORE_CHANCES)) {
                settings.values().forEach(setting -> values.put(setting, setting.get()));
            }
            for (var settings : List.of(MiteConfig.OVERWORLD_ORE_HEIGHTS, MiteConfig.UNDERWORLD_ORE_HEIGHTS,
                    MiteConfig.OVERWORLD_DEEPSLATE_ORE_HEIGHTS, MiteConfig.UNDERWORLD_DEEPSLATE_ORE_HEIGHTS))
                settings.values().forEach(setting -> heights.put(setting, setting.get()));
        }
        @Override public void close() {
            values.forEach((setting, value) -> setting.set(value));
            heights.forEach((setting, value) -> setting.set(value));
            MiteConfig.clearServerSnapshot();
            MiteConfig.refreshEnchantmentRules();
            MiteConfig.SPEC.save();
        }
    }
}
