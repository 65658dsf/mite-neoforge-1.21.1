package io.github.ningmeng123.mite.gametest;

import com.google.gson.Gson;
import io.github.ningmeng123.mite.config.GameplayConfigEdits;
import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.network.GameplayConfigPayload;
import io.github.ningmeng123.mite.rules.OreHeightRange;
import io.github.ningmeng123.mite.rules.R196OreRules.Dimension;
import io.github.ningmeng123.mite.rules.R196OreRules.Kind;
import io.github.ningmeng123.mite.worldgen.R196OreFeature;
import io.netty.buffer.Unpooled;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Saved Nether settings reach the client and the native chunk decoration paths. @author NingMeng123 */
@GameTestHolder("mite_feedback")
@PrefixGameTestTemplate(false)
public final class NetherOreConfigGameTests {
    private NetherOreConfigGameTests() {}

    private record Deposit(Kind kind, String featureId) {}

    @GameTest(template = "empty", batch = "netherOreSettings", timeoutTicks = 400)
    public static void netherOreSettingsSaveSyncAndConstrainPlacedFeatures(GameTestHelper helper) {
        var nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        helper.assertTrue(nether != null, "The Nether dimension is available");
        var originalRates = new EnumMap<Kind, Double>(Kind.class);
        var originalHeights = new EnumMap<Kind, String>(Kind.class);
        MiteConfig.NETHER_ORES.forEach((kind, setting) -> originalRates.put(kind, setting.get()));
        MiteConfig.NETHER_ORE_HEIGHTS.forEach((kind, setting) -> originalHeights.put(kind, setting.get()));
        var previousRemote = MiteConfig.effectiveSnapshot() == MiteConfig.localSnapshot() ? null : MiteConfig.effectiveSnapshot();

        try {
            var rates = new EnumMap<Kind, Double>(Kind.class);
            originalRates.keySet().forEach(kind -> rates.put(kind, 0D));
            rates.put(Kind.QUARTZ, 2.5D);
            helper.assertTrue(save(rates, Map.of(Kind.QUARTZ, "[30,32]")), "The settings editor payload saves Nether rates and heights");
            var snapshot = MiteConfig.localSnapshot();
            helper.assertFalse(save(rates, Map.of(Kind.QUARTZ, "[32,30]")), "An invalid height rejects the entire edit");
            helper.assertTrue(MiteConfig.localSnapshot() == snapshot, "A rejected edit leaves the previous revision in place");
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), nether.registryAccess());
            try {
                GameplayConfigPayload.CODEC.encode(buffer, new GameplayConfigPayload(snapshot));
                var decoded = GameplayConfigPayload.CODEC.decode(buffer).snapshot();
                helper.assertValueEqual(decoded, snapshot, "The client payload round-trips Nether settings");
                MiteConfig.receiveServerSnapshot(decoded);
                helper.assertValueEqual(MiteConfig.oreMultiplier(Kind.QUARTZ, Dimension.NETHER), 2.5D, "The client reads the saved rate");
                helper.assertValueEqual(MiteConfig.oreHeightOverride(Kind.QUARTZ, Dimension.NETHER, false),
                        new OreHeightRange(30, 32), "The client reads the saved height");
            } finally {
                buffer.release();
            }

            var deposits = List.of(new Deposit(Kind.QUARTZ, "mite:ores_nether"),
                    new Deposit(Kind.NETHER_GOLD, "mite:ores_nether"), new Deposit(Kind.NETHER_INFESTED, "mite:ores_nether"),
                    new Deposit(Kind.ANCIENT_DEBRIS, "minecraft:ore_ancient_debris_large"),
                    new Deposit(Kind.ANCIENT_DEBRIS, "minecraft:ore_debris_small"),
                    new Deposit(Kind.NETHER_ADAMANTIUM, "modern_ite:nether_adamantium_ore"));
            var features = nether.registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
            for (var deposit : deposits) {
                var block = R196OreFeature.generationBlock(deposit.kind());
                if (deposit.kind() == Kind.NETHER_ADAMANTIUM && block == Blocks.AIR) continue;
                var feature = features.get(ResourceLocation.parse(deposit.featureId()));
                helper.assertTrue(feature != null, "The native placed feature is registered for " + deposit);
                for (double rate : new double[]{0D, 40D}) {
                    MiteConfig.NETHER_ORES.values().forEach(setting -> setting.set(0D));
                    MiteConfig.NETHER_ORES.get(deposit.kind()).set(rate);
                    MiteConfig.NETHER_ORE_HEIGHTS.get(deposit.kind()).set("[30,32]");
                    MiteConfig.refreshEnchantmentRules();
                    MiteConfig.receiveServerSnapshot(MiteConfig.localSnapshot());
                    var fixture = new OreGenerationGameTests.GenerationFixture(nether, new ChunkPos(6740, -6740), true);
                    fixture.fill(0, 128);
                    boolean placed = feature.placeWithBiomeCheck(fixture.region, nether.getChunkSource().getGenerator(),
                            new LegacyRandomSource(196L), fixture.center.getPos().getWorldPosition());
                    helper.assertValueEqual(placed, rate > 0D, "The configured rate controls placement of " + deposit);
                    int found = 0;
                    var pos = new BlockPos.MutableBlockPos();
                    for (var chunk : fixture.all) {
                        int startX = chunk.getPos().getMinBlockX(), startZ = chunk.getPos().getMinBlockZ();
                        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = 0; y < 128; y++) {
                            if (!chunk.getBlockState(pos.set(startX + x, y, startZ + z)).is(block)) continue;
                            helper.assertTrue(y >= 30 && y <= 32, "The entire deposit stays inside the edited height range: " + deposit);
                            found++;
                        }
                    }
                    helper.assertValueEqual(found > 0, rate > 0D, "Fresh Nether chunks contain ore only when enabled: " + deposit);
                }
            }
        } finally {
            originalRates.forEach((kind, rate) -> MiteConfig.NETHER_ORES.get(kind).set(rate));
            originalHeights.forEach((kind, height) -> MiteConfig.NETHER_ORE_HEIGHTS.get(kind).set(height));
            MiteConfig.clearServerSnapshot();
            MiteConfig.refreshEnchantmentRules();
            MiteConfig.SPEC.save();
            if (previousRemote != null) MiteConfig.receiveServerSnapshot(previousRemote);
        }
        helper.succeed();
    }

    private static boolean save(Map<Kind, Double> rates, Map<Kind, String> heights) {
        var draft = new GameplayConfigEdits.OreMultipliers(Map.of(Dimension.NETHER, rates), Map.of(),
                Map.of(Dimension.NETHER, heights), Map.of());
        return GameplayConfigEdits.save("ore_multipliers", new Gson().toJson(draft));
    }
}
