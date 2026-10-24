package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.config.MiteConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The complete user preset becomes schema defaults without touching live values. @author NingMeng123 */
@GameTestHolder("mite_defaults")
@PrefixGameTestTemplate(false)
public final class BundledDefaultsGameTests {
    private BundledDefaultsGameTests() {}
    @GameTest(template="empty")
    public static void everyExportedSettingIsTheActualFactoryDefault(GameTestHelper helper) throws Exception {
        try (var stream = BundledDefaultsGameTests.class.getResourceAsStream("/mite/gameplay-defaults.toml");
                var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            compare(helper, new TomlParser().parse(reader), List.of());
        }
        helper.assertValueEqual(MiteConfig.localSnapshot(), MiteConfig.factorySnapshot(),
                "A fresh installation publishes the same complete server snapshot as its factory preset");
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void newCoalAndEmeraldDefaultsWinWhileOldExportValuesRemainExact(GameTestHelper helper) throws Exception {
        try (var stream = BundledDefaultsGameTests.class.getResourceAsStream("/mite/legacy-export-20261024.toml")) {
            String text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            var legacy = new TomlParser().parse(text);
            var original = new TomlParser().parse(text);
            MiteConfig.SPEC.correct(legacy);
            preserve(helper, original, legacy, List.of());
            helper.assertValueEqual(((Number)legacy.get("overworld_ores.coal")).doubleValue(), 4D, "Missing regular coal uses the latest requested default");
            helper.assertValueEqual(((Number)legacy.get("overworld_ores.deepslate_coal")).doubleValue(), 4D, "Missing deep coal uses four");
            helper.assertValueEqual(((Number)legacy.get("overworld_ores.emerald")).doubleValue(), 1.5D, "Missing regular emerald uses one point five");
            helper.assertValueEqual(((Number)legacy.get("overworld_ores.deepslate_emerald")).doubleValue(), 1.5D, "Missing deep emerald uses one point five");
            helper.assertValueEqual(((Number)legacy.get("underworld_generation.coal.candidates")).doubleValue(), 50D, "Old exports gain actual underworld coal candidates");
            helper.assertValueEqual(((Number)legacy.get("underworld_generation.emerald.chance_percent")).doubleValue(), 100D, "Underworld emerald preserves every singleton attempt");
        }
        helper.succeed();
    }
    private static void preserve(GameTestHelper helper, UnmodifiableConfig expected, UnmodifiableConfig actual, List<String> parent) {
        for (var entry : expected.entrySet()) {
            var path = new ArrayList<>(parent); path.add(entry.getKey());
            if (entry.getValue() instanceof UnmodifiableConfig child) preserve(helper, child, actual, path);
            else helper.assertValueEqual(actual.get(path), entry.getValue(), "An explicitly saved original export leaf remains unchanged: " + path);
        }
    }

    private static void compare(GameTestHelper helper, UnmodifiableConfig expected, List<String> parent) {
        for (var entry : expected.entrySet()) {
            var path = new ArrayList<>(parent); path.add(entry.getKey());
            if (entry.getValue() instanceof UnmodifiableConfig child) compare(helper, child, path);
            else {
                Object raw = MiteConfig.SPEC.getValues().get(path);
                helper.assertTrue(raw instanceof ModConfigSpec.ConfigValue<?>, "Exported setting exists: " + path);
                Object actual = ((ModConfigSpec.ConfigValue<?>)raw).getDefault(), wanted = entry.getValue();
                boolean equal = actual instanceof Number a && wanted instanceof Number b
                        ? Double.compare(a.doubleValue(), b.doubleValue()) == 0 : java.util.Objects.equals(actual, wanted);
                helper.assertTrue(equal, "Factory default " + path + ": " + actual + " vs " + wanted);
                Object live = ((ModConfigSpec.ConfigValue<?>)raw).get();
                boolean loadedEqual = live instanceof Number a && wanted instanceof Number b
                        ? Double.compare(a.doubleValue(), b.doubleValue()) == 0 : java.util.Objects.equals(live, wanted);
                helper.assertTrue(loadedEqual, "Fresh config loads bundled value " + path + ": " + live + " vs " + wanted);
            }
        }
    }
}
