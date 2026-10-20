package io.github.ningmeng123.mite.gametest;

import com.electronwill.nightconfig.toml.TomlParser;
import com.google.gson.Gson;
import io.github.ningmeng123.mite.config.GameplayConfigExport;
import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.network.GameplayConfigPayload;
import io.github.ningmeng123.mite.rules.MonsterGrowthRules;
import io.netty.buffer.Unpooled;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Percentage overrides survive saving and synchronization without changing old defaults. @author NingMeng123 */
@GameTestHolder("mite_enchanting_chance")
@PrefixGameTestTemplate(false)
public final class EnchantmentChanceConfigGameTests {
    private EnchantmentChanceConfigGameTests() {}

    @GameTest(template = "empty", templateNamespace = "mite")
    public static void chanceOverridesValidateFinitePercentagesAndPublishImmutableSnapshots(GameTestHelper helper) {
        for (String entry : List.of("minecraft:sharpness=0", "minecraft:sharpness=37.5", "missing_mod:preserved=100"))
            helper.assertTrue(MiteConfig.validEnchantmentChanceOverride(entry), "Valid percentage override: " + entry);
        for (Object entry : List.of("sharpness=50", "Bad Namespace:sharpness=50", "minecraft:sharpness",
                "minecraft:sharpness=", "minecraft:sharpness=50=0", "minecraft:sharpness=NaN",
                "minecraft:sharpness=Infinity", "minecraft:sharpness=-0.01", "minecraft:sharpness=100.01", 50))
            helper.assertFalse(MiteConfig.validEnchantmentChanceOverride(entry), "Invalid percentage override: " + entry);
        helper.assertFalse(MiteConfig.validEnchantmentChanceOverride(null), "A null entry is not a percentage override");

        var draft = new HashMap<>(Map.of("minecraft:sharpness", 37.5D, "minecraft:unbreaking", 0D, "minecraft:efficiency", 100D));
        var published = snapshot(draft);
        draft.put("minecraft:sharpness", 99D);
        helper.assertValueEqual(published.enchantmentChances(), Map.of("minecraft:sharpness", 37.5D, "minecraft:unbreaking", 0D),
                "A published revision owns its percentage overrides and omits vanilla defaults");
        boolean immutable = false;
        try { published.enchantmentChances().put("minecraft:sharpness", 1D); }
        catch (UnsupportedOperationException expected) { immutable = true; }
        helper.assertTrue(immutable, "Remote and local readers cannot mutate the published chance map");
        for (double invalid : new double[]{Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, -.01D, 100.01D}) {
            boolean rejected = false;
            try { snapshot(Map.of("minecraft:sharpness", invalid)); }
            catch (IllegalArgumentException expected) { rejected = true; }
            helper.assertTrue(rejected, "Invalid snapshot percentages cannot become a runtime rule: " + invalid);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "mite")
    public static void chanceOverridesSurviveSaveReloadPacketExportAndLegacyEditors(GameTestHelper helper) throws Exception {
        try (var ignored = new Settings()) {
            var caps = Map.of("minecraft:sharpness", 3);
            var disabled = Set.of("minecraft:unbreaking");
            var chanceDraft = Map.of("minecraft:sharpness", 37.5D, "minecraft:efficiency", 100D,
                    "minecraft:unbreaking", 0D, "missing_mod:preserved", 12.5D);
            MiteConfig.saveEnchantments(caps, disabled, chanceDraft);
            var entries = List.of("minecraft:sharpness=37.5", "minecraft:unbreaking=0.0", "missing_mod:preserved=12.5");
            helper.assertValueEqual(MiteConfig.ENCHANTMENT_CHANCES.get(), entries,
                    "Saving writes stable registry IDs and explicit percentages without redundant defaults");
            MiteConfig.refreshEnchantmentRules();
            var published = MiteConfig.localSnapshot();
            helper.assertValueEqual(published.enchantmentChances().get("missing_mod:preserved"), 12.5D,
                    "Temporarily removed mods keep their probability override after reload");
            helper.assertValueEqual(MiteConfig.enchantmentChancePercent("minecraft:efficiency"), 100D,
                    "An omitted override preserves vanilla candidate selection");
            helper.assertValueEqual(published.caps(), caps, "Probability saving preserves explicit level caps");
            helper.assertValueEqual(published.disabled(), disabled, "Probability saving preserves disabled effects");

            var bytes = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
            try {
                GameplayConfigPayload.CODEC.encode(bytes, new GameplayConfigPayload(published));
                helper.assertValueEqual(GameplayConfigPayload.CODEC.decode(bytes).snapshot(), published,
                        "The registered packet codec carries every chance override");
                helper.assertValueEqual(bytes.readableBytes(), 0, "The packet decoder consumes the complete snapshot");
            } finally { bytes.release(); }

            MiteConfig.receiveServerSnapshot(snapshot(Map.of("minecraft:sharpness", 25D)));
            helper.assertValueEqual(MiteConfig.enchantmentChancePercent("minecraft:sharpness"), 25D,
                    "The settings browser uses the server's authoritative probability");
            var exported = GameplayConfigExport.export(Files.createTempDirectory("mite-enchantment-chance-export-"));
            try (var reader = Files.newBufferedReader(exported)) {
                helper.assertValueEqual(new TomlParser().parse(reader).get(List.of("enchantments", "chance_percent")), entries,
                        "The standalone configuration exports local probabilities while connected to a different server");
            }
            MiteConfig.clearServerSnapshot();
            helper.assertValueEqual(MiteConfig.enchantmentChancePercent("minecraft:sharpness"), 37.5D,
                    "Disconnect restores the local probability");
            MiteConfig.saveEnchantments(caps, disabled);
            helper.assertValueEqual(MiteConfig.localSnapshot().enchantmentChances(), published.enchantmentChances(),
                    "A legacy two-argument editor preserves the saved probability draft");

            var before = MiteConfig.localSnapshot();
            boolean rejected = false;
            try { MiteConfig.saveEnchantments(Map.of(), Set.of(), Map.of("minecraft:sharpness", Double.NaN)); }
            catch (IllegalArgumentException expected) { rejected = true; }
            helper.assertTrue(rejected, "An invalid probability draft is rejected before saving");
            helper.assertTrue(MiteConfig.localSnapshot() == before, "Rejected probability drafts cannot publish a partial revision");
            helper.assertValueEqual(MiteConfig.ENCHANTMENT_MAX_LEVELS.get(), List.of("minecraft:sharpness=3"),
                    "Rejected probability drafts cannot clear an unrelated cap");
            helper.assertValueEqual(MiteConfig.DISABLED_ENCHANTMENTS.get(), List.of("minecraft:unbreaking"),
                    "Rejected probability drafts cannot clear disabled effects");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "mite")
    public static void olderConstructorsAndPacketsRetainVanillaProbabilityDefaults(GameTestHelper helper) {
        var legacy = new MiteConfig.Snapshot(Map.of(), 1D, 1D, 1D, 1D, 0D, true, true, true, Map.of(), Set.of());
        helper.assertTrue(legacy.enchantmentChances().isEmpty(), "Old constructor signatures preserve vanilla probability defaults");
        var gson = new Gson();
        var json = gson.toJsonTree(snapshot(Map.of("minecraft:sharpness", 50D))).getAsJsonObject();
        json.remove("enchantmentChances");
        var bytes = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            bytes.writeUtf(gson.toJson(json), 262144);
            var decoded = GameplayConfigPayload.CODEC.decode(bytes).snapshot();
            helper.assertTrue(decoded.enchantmentChances().isEmpty(), "An older server packet with no chance field remains readable");
        } finally { bytes.release(); }
        helper.succeed();
    }

    private static MiteConfig.Snapshot snapshot(Map<String, Double> chances) {
        return new MiteConfig.Snapshot(Map.of(), 1D, 1D, 1D, 1D, 0D, true, true, true, Map.of(), Set.of(),
                MonsterGrowthRules.DEFAULTS, Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), chances);
    }

    private static final class Settings implements AutoCloseable {
        private final List<? extends String> caps = List.copyOf(MiteConfig.ENCHANTMENT_MAX_LEVELS.get());
        private final List<? extends String> disabled = List.copyOf(MiteConfig.DISABLED_ENCHANTMENTS.get());
        private final List<? extends String> chances = List.copyOf(MiteConfig.ENCHANTMENT_CHANCES.get());
        private final MiteConfig.Snapshot remote = MiteConfig.effectiveSnapshot() == MiteConfig.localSnapshot() ? null : MiteConfig.effectiveSnapshot();

        private Settings() { MiteConfig.clearServerSnapshot(); }

        @Override public void close() {
            MiteConfig.ENCHANTMENT_MAX_LEVELS.set(caps);
            MiteConfig.DISABLED_ENCHANTMENTS.set(disabled);
            MiteConfig.ENCHANTMENT_CHANCES.set(chances);
            MiteConfig.refreshEnchantmentRules();
            MiteConfig.SPEC.save();
            MiteConfig.clearServerSnapshot();
            if (remote != null) MiteConfig.receiveServerSnapshot(remote);
        }
    }
}
