package io.github.ningmeng123.mite.gametest;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableCommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import io.github.ningmeng123.mite.config.GameplayConfigExport;
import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.rules.MonsterGrowthRules;
import io.github.ningmeng123.mite.rules.R196OreRules;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Exports remain complete, independently usable and isolated from client/server state. @author NingMeng123 */
@GameTestHolder("mite_config")
@PrefixGameTestTemplate(false)
public final class GameplayConfigExportGameTests {
    private GameplayConfigExportGameTests() {}

    @GameTest(template = "empty")
    public static void exportedTomlRetainsEverySettingAndUnknownEnchantmentOverride(GameTestHelper helper) throws Exception {
        try (var ignored = new Settings()) {
            configureDistinctLocalValues();
            var expected = currentValues();
            var published = MiteConfig.localSnapshot();
            byte[] source = sourceBytes();
            Path directory = Files.createTempDirectory("mite-config-complete-export-");
            Path exported = GameplayConfigExport.export(directory);
            helper.assertTrue(exported.toAbsolutePath().normalize().startsWith(directory.toAbsolutePath().normalize().resolve("exports")),
                    "The generated configuration stays inside this game's exports directory");
            helper.assertValueEqual(exported.getFileName().toString(), "mite-common.toml",
                    "The exported file uses the dedicated server's common-config filename");
            var parsed = parse(exported);
            assertCompleteValues(helper, parsed, expected);
            helper.assertValueEqual(parsed.get(List.of("enchantments", "disabled")),
                    List.of("minecraft:sharpness", "missing_mod:preserved"),
                    "Disabled IDs include temporarily absent mods without registry filtering");
            helper.assertValueEqual(parsed.get(List.of("enchantments", "max_levels")),
                    List.of("minecraft:sharpness=3", "missing_mod:preserved=11"),
                    "Explicit enchantment caps are retained verbatim for a server with more mods");
            helper.assertValueEqual(currentValues(), expected, "Exporting never rewrites mutable editor values");
            helper.assertValueEqual(MiteConfig.localSnapshot(), published, "Exporting preserves the published local gameplay revision");
            helper.assertTrue(Arrays.equals(sourceBytes(), source), "Exporting does not write the loaded common configuration file");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void repeatedExportsPreserveEarlierFilesAndResolveInheritedBloodMoonExpressions(GameTestHelper helper) throws Exception {
        try (var ignored = new Settings()) {
            restoreDefaults();
            String health = "base + reference * rate * days * 2";
            String damage = "base + reference * rate * days * 3";
            MiteConfig.MONSTER_HEALTH_FORMULA.set(health);
            MiteConfig.MONSTER_DAMAGE_FORMULA.set(damage);
            MiteConfig.MONSTER_HEALTH_BLOOD_MOON_FORMULA.set("");
            MiteConfig.MONSTER_DAMAGE_BLOOD_MOON_FORMULA.set("  ");
            MiteConfig.refreshEnchantmentRules();
            helper.assertValueEqual(MiteConfig.localSnapshot().monsterSettings().damageFormula(), damage,
                    "The validated attack expression is active before export");
            byte[] source = sourceBytes();
            var editorValues = currentValues();
            Path directory = Files.createTempDirectory("mite-config-repeat-export-");
            Path existing = directory.resolve("config").resolve("mite-common.toml");
            Files.createDirectories(existing.getParent());
            Files.writeString(existing, "# Existing local configuration must remain untouched.\n", StandardCharsets.UTF_8);
            byte[] existingBytes = Files.readAllBytes(existing);
            Path first = GameplayConfigExport.export(directory);
            byte[] firstBytes = Files.readAllBytes(first);
            Path second = GameplayConfigExport.export(directory);
            helper.assertFalse(first.equals(second), "Repeated export creates a fresh package instead of overwriting the prior package");
            helper.assertTrue(Files.isRegularFile(first) && Files.isRegularFile(second), "Both exported packages remain available");
            helper.assertTrue(Arrays.equals(Files.readAllBytes(first), firstBytes), "A second export leaves the original package unchanged");
            helper.assertTrue(Arrays.equals(Files.readAllBytes(existing), existingBytes), "Exporting does not replace an existing config in the target game directory");
            for (Path output : List.of(first, second)) {
                var parsed = parse(output);
                helper.assertTrue(MiteConfig.SPEC.isCorrect(parsed), "An inherited-formula export passes native common-config validation");
                helper.assertValueEqual(parsed.get(List.of("monsters", "health_blood_moon_formula")), health,
                        "An omitted health settlement exports its effective inherited formula");
                helper.assertValueEqual(parsed.get(List.of("monsters", "damage_blood_moon_formula")), damage,
                        "A blank attack settlement exports the inherited attack expression");
            }
            helper.assertValueEqual(currentValues(), editorValues, "Resolving inheritance for export does not mutate the client configuration");
            helper.assertTrue(Arrays.equals(sourceBytes(), source), "Repeated export never saves the loaded client config");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void invalidReloadExportsTheWholeLastValidGrowthRevision(GameTestHelper helper) throws Exception {
        try (var ignored = new Settings()) {
            configureDistinctLocalValues();
            var valid = MiteConfig.localSnapshot();
            MiteConfig.MONSTER_GROWTH.set(9D);
            MiteConfig.MONSTER_HEALTH_GROWTH.set(8D);
            MiteConfig.MONSTER_DAMAGE_GROWTH.set(7D);
            MiteConfig.MONSTER_HEALTH_BASE.set(99D);
            MiteConfig.MONSTER_MINIMUM_HEALTH_REFERENCE.set(75D);
            MiteConfig.MONSTER_DAMAGE_BASE.set(44D);
            MiteConfig.MONSTER_SKELETON_REFERENCE.set(55D);
            MiteConfig.MONSTER_HEALTH_DAILY_PERCENT.set(2D);
            MiteConfig.MONSTER_DAMAGE_DAILY_PERCENT.set(3D);
            MiteConfig.MONSTER_HEALTH_FORMULA.set("base + 999");
            MiteConfig.MONSTER_DAMAGE_FORMULA.set("base + 888");
            MiteConfig.MONSTER_HEALTH_BLOOD_MOON_FORMULA.set("base / (days - days)");
            MiteConfig.MONSTER_DAMAGE_BLOOD_MOON_FORMULA.set("base + 777");
            MiteConfig.WEAPON_REACH_REDUCTION.set(1.125D);
            MiteConfig.refreshEnchantmentRules();
            helper.assertValueEqual(MiteConfig.localSnapshot().monsterSettings(), valid.monsterSettings(),
                    "The deliberately invalid blood-moon reload retains the previous effective growth rules");
            var raw = currentValues();
            byte[] source = sourceBytes();
            var parsed = parse(GameplayConfigExport.export(Files.createTempDirectory("mite-config-valid-export-")));
            var settings = valid.monsterSettings();
            assertNumber(helper, parsed, "growth_multiplier", valid.growth());
            assertNumber(helper, parsed, "health_growth_multiplier", valid.healthGrowth());
            assertNumber(helper, parsed, "damage_growth_multiplier", valid.damageGrowth());
            assertNumber(helper, parsed, "health_base", settings.healthBase());
            assertNumber(helper, parsed, "minimum_health_reference", settings.minimumHealthReference());
            assertNumber(helper, parsed, "damage_base", settings.damageBase());
            assertNumber(helper, parsed, "skeleton_reference", settings.skeletonReference());
            assertNumber(helper, parsed, "health_daily_percent", settings.healthRate() * 100D);
            assertNumber(helper, parsed, "damage_daily_percent", settings.damageRate() * 100D);
            helper.assertValueEqual(parsed.get(List.of("monsters", "health_formula")), settings.healthFormula(), "Export retains the last valid period health formula");
            helper.assertValueEqual(parsed.get(List.of("monsters", "damage_formula")), settings.damageFormula(), "Export retains the last valid period attack formula");
            helper.assertValueEqual(parsed.get(List.of("monsters", "health_blood_moon_formula")), settings.healthBloodMoonFormula(), "Export excludes the failing health settlement expression");
            helper.assertValueEqual(parsed.get(List.of("monsters", "damage_blood_moon_formula")), settings.damageBloodMoonFormula(), "Export does not mix a rejected attack settlement with the valid growth revision");
            helper.assertValueEqual(((Number)parsed.get(List.of("combat", "weapon_reach_reduction"))).doubleValue(), 1.125D,
                    "An unrelated valid combat edit survives a rejected growth reload");
            helper.assertTrue(MonsterGrowthRules.validate(settings, valid.growth() * valid.healthGrowth(), valid.growth() * valid.damageGrowth()).valid(),
                    "The exported retained growth revision executes successfully with its retained multipliers");
            helper.assertTrue(MiteConfig.SPEC.isCorrect(parsed), "The fallback export is a complete valid native config");
            helper.assertValueEqual(currentValues(), raw, "Fallback export does not silently rewrite invalid file/editor input");
            helper.assertTrue(Arrays.equals(sourceBytes(), source), "Fallback export does not alter the client's existing config file");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void connectedServerSnapshotCannotReplaceTheExportedLocalSettings(GameTestHelper helper) throws Exception {
        try (var ignored = new Settings()) {
            configureDistinctLocalValues();
            var localValues = currentValues();
            var local = MiteConfig.localSnapshot();
            var remote = new MiteConfig.Snapshot(Map.of("COPPER", 0D), 9D, 8D, 7D,
                    9D, 6D, true, true, true, Map.of("minecraft:sharpness", 1), Set.of("missing_mod:remote_only"),
                    MonsterGrowthRules.DEFAULTS, Map.of("COPPER", 999D), Map.of("COPPER", .99D));
            MiteConfig.receiveServerSnapshot(remote);
            byte[] source = sourceBytes();
            var parsed = parse(GameplayConfigExport.export(Files.createTempDirectory("mite-config-local-export-")));
            assertCompleteValues(helper, parsed, localValues);
            helper.assertTrue(MiteConfig.effectiveSnapshot() == remote, "Export leaves the connected server's authoritative view intact");
            helper.assertValueEqual(MiteConfig.localSnapshot(), local, "Export preserves the separately stored local configuration");
            helper.assertValueEqual(currentValues(), localValues, "Receiving and exporting never writes remote values into local editor fields");
            helper.assertTrue(Arrays.equals(sourceBytes(), source), "A remote snapshot cannot cause export to rewrite the local file");
        }
        helper.succeed();
    }

    private static void configureDistinctLocalValues() {
        restoreDefaults();
        int surfaceIndex = 0;
        for (var kind : R196OreRules.CONFIGURABLE_OVERWORLD) {
            MiteConfig.OVERWORLD_ORES.get(kind).set(.5D + surfaceIndex * .25D);
            MiteConfig.OVERWORLD_ORE_CANDIDATES.get(kind).set(3.5D + surfaceIndex * 2.25D);
            MiteConfig.OVERWORLD_ORE_CHANCES.get(kind).set(12.5D + surfaceIndex * 7.25D);
            surfaceIndex++;
        }
        int index = 0;
        for (var kind : R196OreRules.configurableKinds(R196OreRules.Dimension.UNDERWORLD)) {
            MiteConfig.UNDERWORLD_ORES.get(kind).set(1.25D + index * .125D);
            MiteConfig.UNDERWORLD_ORE_CANDIDATES.get(kind).set(10.25D + index * 7.5D);
            MiteConfig.UNDERWORLD_ORE_CHANCES.get(kind).set(5.5D + index * 3.25D);
            index++;
        }
        MiteConfig.MONSTER_GROWTH.set(1.25D);
        MiteConfig.MONSTER_HEALTH_GROWTH.set(1.5D);
        MiteConfig.MONSTER_DAMAGE_GROWTH.set(.75D);
        MiteConfig.MONSTER_HEALTH_BASE.set(31D);
        MiteConfig.MONSTER_MINIMUM_HEALTH_REFERENCE.set(45D);
        MiteConfig.MONSTER_DAMAGE_BASE.set(4D);
        MiteConfig.MONSTER_SKELETON_REFERENCE.set(27D);
        MiteConfig.MONSTER_HEALTH_DAILY_PERCENT.set(.75D);
        MiteConfig.MONSTER_DAMAGE_DAILY_PERCENT.set(.5D);
        MiteConfig.MONSTER_HEALTH_FORMULA.set("base + reference * rate * days + blood_moons * 0.01");
        MiteConfig.MONSTER_DAMAGE_FORMULA.set("base + reference * rate * days + initial * 0.001");
        MiteConfig.MONSTER_HEALTH_BLOOD_MOON_FORMULA.set("base + reference * rate * days + 2");
        MiteConfig.MONSTER_DAMAGE_BLOOD_MOON_FORMULA.set("base + reference * rate * days + 1");
        MiteConfig.WEAPON_REACH_MULTIPLIER.set(1.25D);
        MiteConfig.WEAPON_REACH_REDUCTION.set(.75D);
        MiteConfig.HEIGHT_REACH_PENALTY.set(false);
        MiteConfig.UNDERWORLD_TRIALS.set(false);
        MiteConfig.OVERWORLD_TRIALS.set(false);
        MiteConfig.ENCHANTMENT_MAX_LEVELS.set(List.of("minecraft:sharpness=3", "missing_mod:preserved=11"));
        MiteConfig.DISABLED_ENCHANTMENTS.set(List.of("minecraft:sharpness", "missing_mod:preserved"));
        MiteConfig.refreshEnchantmentRules();
    }

    private static void restoreDefaults() {
        for (var value : values()) {
            if (!value.getPath().equals(List.of("monsters", "damage_rate_migrated"))
                    && !value.getPath().equals(List.of("combat", "better_combat_damage_migrated"))) set(value, value.getDefault());
        }
        MiteConfig.clearServerSnapshot();
        MiteConfig.refreshEnchantmentRules();
    }

    private static CommentedConfig parse(Path path) throws Exception {
        return new TomlParser().parse(Files.readString(path, StandardCharsets.UTF_8));
    }

    private static byte[] sourceBytes() throws Exception {
        return Files.readAllBytes(FMLPaths.CONFIGDIR.get().resolve("mite-common.toml"));
    }

    private static void assertCompleteValues(GameTestHelper helper, UnmodifiableCommentedConfig parsed, Map<List<String>, Object> expected) {
        helper.assertTrue(MiteConfig.SPEC.isCorrect(parsed), "The generated TOML passes the real native common-config schema");
        var actual = new LinkedHashMap<List<String>, Object>();
        collectLeaves(parsed, List.of(), actual);
        helper.assertValueEqual(actual.keySet(), expected.keySet(), "The export covers every registered config path without silently dropping settings");
        for (var entry : expected.entrySet()) helper.assertValueEqual(actual.get(entry.getKey()), entry.getValue(),
                "The exported TOML preserves " + String.join(".", entry.getKey()));
    }

    private static void assertNumber(GameTestHelper helper, UnmodifiableConfig parsed, String key, double expected) {
        helper.assertValueEqual(((Number)parsed.get(List.of("monsters", key))).doubleValue(), expected,
                "Export keeps the last valid monster setting: " + key);
    }

    private static Map<List<String>, Object> currentValues() {
        var result = new LinkedHashMap<List<String>, Object>();
        for (var value : values()) result.put(List.copyOf(value.getPath()), copy(value.get()));
        return result;
    }

    private static List<ModConfigSpec.ConfigValue<?>> values() {
        var result = new ArrayList<ModConfigSpec.ConfigValue<?>>();
        collectValues(MiteConfig.SPEC.getValues(), result);
        return result;
    }

    private static void collectValues(UnmodifiableConfig config, List<ModConfigSpec.ConfigValue<?>> result) {
        for (var entry : config.entrySet()) {
            if (entry.getValue() instanceof UnmodifiableConfig child) collectValues(child, result);
            else if (entry.getValue() instanceof ModConfigSpec.ConfigValue<?> value) result.add(value);
        }
    }

    private static void collectLeaves(UnmodifiableConfig config, List<String> parent, Map<List<String>, Object> result) {
        for (var entry : config.entrySet()) {
            var path = new ArrayList<>(parent);
            path.add(entry.getKey());
            if (entry.getValue() instanceof UnmodifiableConfig child) collectLeaves(child, path, result);
            else result.put(List.copyOf(path), entry.getValue());
        }
    }

    private static Object copy(Object value) { return value instanceof List<?> list ? List.copyOf(list) : value; }

    @SuppressWarnings("unchecked")
    private static void set(ModConfigSpec.ConfigValue<?> value, Object contents) {
        ((ModConfigSpec.ConfigValue<Object>)value).set(contents);
    }

    private static final class Settings implements AutoCloseable {
        private final Map<ModConfigSpec.ConfigValue<?>, Object> original = new LinkedHashMap<>();
        private final MiteConfig.Snapshot remote;
        private final Lock configLock = GameplayConfigEditGameTests.lockCommonConfig();

        private Settings() throws ReflectiveOperationException {
            try {
                remote = MiteConfig.effectiveSnapshot() == MiteConfig.localSnapshot() ? null : MiteConfig.effectiveSnapshot();
                for (var value : values()) original.put(value, copy(value.get()));
            } catch (RuntimeException | Error failure) {
                configLock.unlock();
                throw failure;
            }
        }

        @Override public void close() {
            try {
                original.forEach(GameplayConfigExportGameTests::set);
                MiteConfig.clearServerSnapshot();
                MiteConfig.refreshEnchantmentRules();
                if (remote != null) MiteConfig.receiveServerSnapshot(remote);
            } finally {
                configLock.unlock();
            }
        }
    }
}
