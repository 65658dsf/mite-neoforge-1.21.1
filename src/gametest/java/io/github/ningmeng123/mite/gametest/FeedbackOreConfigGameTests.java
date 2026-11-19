package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.network.GameplayConfigPayload;
import io.github.ningmeng123.mite.rules.R196OreRules;
import io.github.ningmeng123.mite.rules.R196OreRules.Dimension;
import io.github.ningmeng123.mite.rules.R196OreRules.Kind;
import io.netty.buffer.Unpooled;
import java.util.HashMap;
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

/** Real coal/emerald generation respects independent substrate rates, heights and the network snapshot. @author NingMeng123 */
@GameTestHolder("mite_feedback")
@PrefixGameTestTemplate(false)
public final class FeedbackOreConfigGameTests {
    private FeedbackOreConfigGameTests() {}
    @GameTest(template="empty", timeoutTicks=400)
    public static void coalAndEmeraldRatesHeightsAndPayloadActuallyControlGeneration(GameTestHelper helper) {
        verifyGeneration(helper, Dimension.OVERWORLD);
        helper.succeed();
    }
    @GameTest(template="empty", timeoutTicks=400)
    public static void underworldCoalAndEmeraldUseIndependentRegularAndDeepRatesOnLegacyStone(GameTestHelper helper) {
        verifyGeneration(helper, Dimension.UNDERWORLD);
        helper.succeed();
    }
    @GameTest(template="empty", timeoutTicks=400)
    public static void freshNativeUnderworldChunksContainBothNewOresAndTheirDeepVariants(GameTestHelper helper) {
        var level = helper.getLevel().getServer().getLevel(io.github.ningmeng123.mite.worldgen.MiteWorldDimensions.UNDERWORLD);
        helper.assertTrue(level != null, "The registered underworld generator is available");
        Map<ModConfigSpec.DoubleValue, Double> values = new HashMap<>();
        Map<ModConfigSpec.ConfigValue<String>, String> heights = new HashMap<>();
        var previousRemote = MiteConfig.effectiveSnapshot() == MiteConfig.localSnapshot() ? null : MiteConfig.effectiveSnapshot();
        for (Kind kind : List.of(Kind.COAL, Kind.EMERALD)) {
            for (var settings : List.of(MiteConfig.UNDERWORLD_ORES, MiteConfig.UNDERWORLD_DEEPSLATE_ORES,
                    MiteConfig.UNDERWORLD_ORE_CANDIDATES, MiteConfig.UNDERWORLD_ORE_CHANCES))
                values.put(settings.get(kind), settings.get(kind).get());
            for (var settings : List.of(MiteConfig.UNDERWORLD_ORE_HEIGHTS, MiteConfig.UNDERWORLD_DEEPSLATE_ORE_HEIGHTS))
                heights.put(settings.get(kind), settings.get(kind).get());
        }
        try {
            for (Kind kind : List.of(Kind.COAL, Kind.EMERALD)) {
                MiteConfig.UNDERWORLD_ORES.get(kind).set(1D);
                MiteConfig.UNDERWORLD_DEEPSLATE_ORES.get(kind).set(1D);
                // Dense deterministic admission isolates registration and native chunk decoration from rare sampling luck.
                MiteConfig.UNDERWORLD_ORE_CANDIDATES.get(kind).set(1000D);
                MiteConfig.UNDERWORLD_ORE_CHANCES.get(kind).set(100D);
                MiteConfig.UNDERWORLD_ORE_HEIGHTS.get(kind).set("[-64,319]");
                MiteConfig.UNDERWORLD_DEEPSLATE_ORE_HEIGHTS.get(kind).set("[-64,319]");
            }
            MiteConfig.refreshEnchantmentRules();
            // Native cross-dimension generation can pump queued file-watcher reload work.
            // Consume one explicit test revision while the real generator runs.
            var revision = MiteConfig.localSnapshot();
            MiteConfig.receiveServerSnapshot(revision);
            var chunk = level.getChunk(6690, -6690);
            helper.assertTrue(MiteConfig.effectiveSnapshot() == revision, "Native chunk decoration retains the same intended ore revision instance");
            int[] count = new int[4];
            for (int x=0; x<16; x++) for (int z=0; z<16; z++) {
                int bx=chunk.getPos().getMinBlockX()+x, bz=chunk.getPos().getMinBlockZ()+z;
                helper.assertTrue(chunk.getBlockState(new BlockPos(bx,-64,bz)).is(io.github.ningmeng123.mite.registry.MiteWorldBlocks.MANTLE.get()), "Native mantle remains protected");
                helper.assertTrue(chunk.getBlockState(new BlockPos(bx,319,bz)).is(Blocks.BEDROCK), "Native roof remains protected");
                for (int y=-63; y<319; y++) {
                    var state = chunk.getBlockState(new BlockPos(bx,y,bz));
                    if (state.is(Blocks.COAL_ORE)) { count[0]++; helper.assertTrue(y>=0, "Regular coal follows the ordinary underworld host"); }
                    if (state.is(Blocks.DEEPSLATE_COAL_ORE)) { count[1]++; helper.assertTrue(y<0, "Deep coal follows the negative-height deepslate host"); }
                    if (state.is(Blocks.EMERALD_ORE)) { count[2]++; helper.assertTrue(y>=0, "Regular emerald follows the ordinary underworld host"); }
                    if (state.is(Blocks.DEEPSLATE_EMERALD_ORE)) { count[3]++; helper.assertTrue(y<0, "Deep emerald follows the negative-height deepslate host"); }
                }
            }
            for (int found : count) helper.assertTrue(found>0, "Actual fresh chunk decoration places each requested ore variant");
        } finally {
            values.forEach(ModConfigSpec.DoubleValue::set); heights.forEach(ModConfigSpec.ConfigValue::set);
            MiteConfig.clearServerSnapshot(); MiteConfig.refreshEnchantmentRules();
            if (previousRemote != null) MiteConfig.receiveServerSnapshot(previousRemote);
        }
        helper.succeed();
    }

    private static void verifyGeneration(GameTestHelper helper, Dimension dimension) {
        var rates = dimension == Dimension.OVERWORLD ? MiteConfig.OVERWORLD_ORES : MiteConfig.UNDERWORLD_ORES;
        var deepRates = dimension == Dimension.OVERWORLD ? MiteConfig.OVERWORLD_DEEPSLATE_ORES : MiteConfig.UNDERWORLD_DEEPSLATE_ORES;
        var candidates = dimension == Dimension.OVERWORLD ? MiteConfig.OVERWORLD_ORE_CANDIDATES : MiteConfig.UNDERWORLD_ORE_CANDIDATES;
        var chances = dimension == Dimension.OVERWORLD ? MiteConfig.OVERWORLD_ORE_CHANCES : MiteConfig.UNDERWORLD_ORE_CHANCES;
        var heights = dimension == Dimension.OVERWORLD ? MiteConfig.OVERWORLD_ORE_HEIGHTS : MiteConfig.UNDERWORLD_ORE_HEIGHTS;
        var deepHeights = dimension == Dimension.OVERWORLD ? MiteConfig.OVERWORLD_DEEPSLATE_ORE_HEIGHTS : MiteConfig.UNDERWORLD_DEEPSLATE_ORE_HEIGHTS;
        var level = dimension == Dimension.OVERWORLD ? helper.getLevel() : helper.getLevel().getServer().getLevel(
                io.github.ningmeng123.mite.worldgen.MiteWorldDimensions.UNDERWORLD);
        helper.assertTrue(level != null, "The actual target dimension is registered");
        Map<ModConfigSpec.DoubleValue, Double> saved = new HashMap<>();
        Map<ModConfigSpec.ConfigValue<String>, String> savedHeights = new HashMap<>();
        var previousRemote = MiteConfig.effectiveSnapshot() == MiteConfig.localSnapshot() ? null : MiteConfig.effectiveSnapshot();
        for (var settings : List.of(rates, deepRates, candidates, chances))
            settings.values().forEach(setting -> saved.put(setting, setting.get()));
        for (var settings : List.of(heights, deepHeights))
            settings.values().forEach(setting -> savedHeights.put(setting, setting.get()));
        try {
            int index = 0;
            for (Kind kind : List.of(Kind.COAL, Kind.EMERALD)) {
                helper.assertTrue(R196OreRules.configurableKinds(dimension).contains(kind), "Both settings tabs enumerate " + kind);
                for (boolean deepOnly : new boolean[]{false, true}) {
                    rates.values().forEach(setting -> setting.set(0D));
                    deepRates.values().forEach(setting -> setting.set(0D));
                    rates.get(kind).set(deepOnly ? 0D : 1D);
                    deepRates.get(kind).set(deepOnly ? 1D : 0D);
                    candidates.get(kind).set(1000D);
                    chances.get(kind).set(100D);
                    heights.get(kind).set("[20,22]");
                    deepHeights.get(kind).set("[-40,-38]");
                    MiteConfig.refreshEnchantmentRules();
                    var revision = MiteConfig.localSnapshot();
                    var bytes = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
                    try {
                        GameplayConfigPayload.CODEC.encode(bytes, new GameplayConfigPayload(revision));
                        var decoded = GameplayConfigPayload.CODEC.decode(bytes).snapshot();
                        helper.assertValueEqual(decoded, revision, "Payload includes both dimensions and independent coal/emerald rates and bounds");
                    } finally { bytes.release(); }
                    // Other tests save actual configs; their queued native reloads must not replace this case mid-feature.
                    MiteConfig.receiveServerSnapshot(revision);
                    helper.assertValueEqual(MiteConfig.oreMultiplier(kind, dimension), deepOnly ? 0D : 1D,
                            "The published regular rate matches this isolated variant case");
                    helper.assertValueEqual(MiteConfig.oreMultiplier(kind, dimension, true), deepOnly ? 1D : 0D,
                            "The published deep rate matches this isolated variant case");
                    var fixture = new OreGenerationGameTests.GenerationFixture(level, new ChunkPos(6490 + index++, -6490), false);
                    fixture.fill(-64, 129);
                    if (dimension == Dimension.UNDERWORLD) {
                        // Older underworld chunks used stone below zero, with a deep coal and emerald fallback.
                        for (var chunk : fixture.all) {
                            int x=chunk.getPos().getMinBlockX(), z=chunk.getPos().getMinBlockZ();
                            for (int dx=0; dx<16; dx++) for (int dz=0; dz<16; dz++) for (int y=-64; y<0; y++)
                                chunk.setBlockState(new BlockPos(x+dx,y,z+dz), Blocks.STONE.defaultBlockState(), false);
                        }
                    }
                    helper.assertTrue(MiteConfig.effectiveSnapshot() == revision, "The same intended ore revision instance survives fixture preparation");
                    fixture.place(dimension == Dimension.OVERWORLD ? "ores_overworld" : "ores_underworld", 196L);
                    helper.assertTrue(MiteConfig.effectiveSnapshot() == revision, "The entire feature consumes the same intended ore revision instance");
                    var regular = kind == Kind.COAL ? Blocks.COAL_ORE : Blocks.EMERALD_ORE;
                    var deep = kind == Kind.COAL ? Blocks.DEEPSLATE_COAL_ORE : Blocks.DEEPSLATE_EMERALD_ORE;
                    int found = 0;
                    for (var chunk : fixture.all) {
                        int x = chunk.getPos().getMinBlockX(), z = chunk.getPos().getMinBlockZ();
                        for (int dx=0; dx<16; dx++) for (int dz=0; dz<16; dz++) for (int y=-64; y<129; y++) {
                            var pos = new BlockPos(x+dx, y, z+dz);
                            var state = chunk.getBlockState(pos);
                            helper.assertFalse(state.is(deepOnly ? regular : deep), "Disabled variant stays empty in " + dimension + ": " + kind
                                    + ", deepOnly=" + deepOnly + ", at=" + pos.toShortString() + ", state=" + state);
                            if (state.is(deepOnly ? deep : regular)) {
                                found++;
                                helper.assertTrue(deepOnly ? y>=-40 && y<=-38 : y>=20 && y<=22, "Configured height bounds the complete deposit");
                            }
                        }
                    }
                    helper.assertTrue(found>0, "Enabled ore actually generates in " + dimension + ": " + kind + ", deep=" + deepOnly);
                }
            }
        } finally {
            saved.forEach(ModConfigSpec.DoubleValue::set);
            savedHeights.forEach(ModConfigSpec.ConfigValue::set);
            MiteConfig.clearServerSnapshot(); MiteConfig.refreshEnchantmentRules();
            if (previousRemote != null) MiteConfig.receiveServerSnapshot(previousRemote);
        }
    }
}
