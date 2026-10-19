package io.github.ningmeng123.mite.gametest;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.ningmeng123.mite.config.EnchantmentCatalog;
import io.github.ningmeng123.mite.config.MiteConfig;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Runtime defaults are collected independently of user overrides. @author NingMeng123 */
@GameTestHolder("mite_config")
@PrefixGameTestTemplate(false)
public final class EnchantmentCatalogGameTests {
    private EnchantmentCatalogGameTests() {}

    @GameTest(template = "empty")
    public static void enteringWorldRefreshesRegistryDefaultsWithoutOverwritingOfflineEdits(GameTestHelper helper) {
        var originalCaps = MiteConfig.ENCHANTMENT_MAX_LEVELS.get();
        var originalDisabled = MiteConfig.DISABLED_ENCHANTMENTS.get();
        try {
            var registry = helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
            var sharpness = registry.getHolderOrThrow(Enchantments.SHARPNESS);
            int nativeMaximum = sharpness.value().definition().maxLevel();
            var offlineCaps = List.of("minecraft:sharpness=2", "missing_mod:preserved=7");
            var offlineDisabled = List.of("minecraft:sharpness", "missing_mod:preserved");
            MiteConfig.ENCHANTMENT_MAX_LEVELS.set(offlineCaps);
            MiteConfig.DISABLED_ENCHANTMENTS.set(offlineDisabled);
            MiteConfig.refreshEnchantmentRules();

            EnchantmentCatalog.observe(helper.getLevel().registryAccess());
            var defaults = defaults();
            for (var registered : registry.entrySet()) {
                String id = registered.getKey().location().toString();
                var discovered = defaults.get(id);
                helper.assertTrue(discovered != null, "The current registry contributes an editable catalog entry: " + id);
                helper.assertValueEqual(discovered.defaultMaxLevel(), registered.getValue().definition().maxLevel(),
                        "The catalog reads the registry definition rather than a configured maximum: " + id);
            }
            helper.assertValueEqual(defaults.get("minecraft:sharpness").defaultMaxLevel(), nativeMaximum,
                    "Disabling and capping Sharpness does not poison its recorded default");
            helper.assertValueEqual(sharpness.value().getMaxLevel(), 2,
                    "The separately retained offline cap still reaches native enchantment metadata");
            helper.assertValueEqual(MiteConfig.ENCHANTMENT_MAX_LEVELS.get(), offlineCaps,
                    "Registry discovery retains existing and temporarily absent enchantment caps verbatim");
            helper.assertValueEqual(MiteConfig.DISABLED_ENCHANTMENTS.get(), offlineDisabled,
                    "Registry discovery retains existing and temporarily absent disabled IDs verbatim");
            helper.assertValueEqual(MiteConfig.localSnapshot().caps().get("missing_mod:preserved"), 7,
                    "Temporarily removed mods retain their active configuration override");
            helper.assertFalse(MiteConfig.enchantmentEnabled("minecraft:sharpness"),
                    "Entering a world does not re-enable an enchantment disabled outside the world");
        } finally {
            MiteConfig.ENCHANTMENT_MAX_LEVELS.set(originalCaps);
            MiteConfig.DISABLED_ENCHANTMENTS.set(originalDisabled);
            MiteConfig.refreshEnchantmentRules();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void catalogRetainsAbsentDiscoveriesWhileRefreshingPresentDefaults(GameTestHelper helper) {
        var previouslyDiscovered = new EnchantmentCatalog.Entry("missing_mod:catalog_retained", 7,
                "{\"translate\":\"enchantment.missing_mod.catalog_retained\"}");
        EnchantmentCatalog.mergeDefaults(List.of(previouslyDiscovered));
        EnchantmentCatalog.observe(helper.getLevel().registryAccess());
        var retained = defaults().get(previouslyDiscovered.id());
        helper.assertValueEqual(retained, previouslyDiscovered,
                "Loading a world without a previously discovered mod keeps its offline catalog row");
        helper.assertTrue(!retained.name().getString().isBlank(), "Retained names can be displayed without a world registry");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void exportActualRegistryDefaultsAndVerifyBundledNativeAndMiteCoverage(GameTestHelper helper) throws Exception {
        var access = helper.getLevel().registryAccess();
        var registry = access.registryOrThrow(Registries.ENCHANTMENT);
        var rows = new JsonArray();
        registry.entrySet().stream().sorted(Comparator.comparing(entry -> entry.getKey().location().toString())).forEach(registered -> {
            var row = new JsonObject();
            row.addProperty("id", registered.getKey().location().toString());
            row.addProperty("defaultMaxLevel", registered.getValue().definition().maxLevel());
            row.addProperty("descriptionJson", Component.Serializer.toJson(registered.getValue().description(), access));
            rows.add(row);
        });
        var exported = new JsonObject();
        exported.addProperty("schema", 1);
        exported.add("enchantments", rows);
        // The isolated GameTest directory is ignored. This provides reproducible source data for the bundled seed.
        Path output = Path.of("enchantment-defaults.json").toAbsolutePath();
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(exported) + "\n", StandardCharsets.UTF_8);

        try (var stream = EnchantmentCatalogGameTests.class.getResourceAsStream("/mite/enchantment-defaults.json")) {
            // First bootstrap exports the actual registry; the client audit separately requires the shipped seed.
            if (stream != null) {
                var bundled = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                helper.assertValueEqual(bundled.get("schema").getAsInt(), 1, "The bundled catalog uses the supported schema");
                Map<String, JsonObject> seed = new java.util.HashMap<>();
                for (var element : bundled.getAsJsonArray("enchantments")) {
                    var row = element.getAsJsonObject();
                    helper.assertTrue(seed.put(row.get("id").getAsString(), row) == null, "The bundled catalog has no duplicate IDs");
                }
                for (var registered : registry.entrySet()) {
                    var id = registered.getKey().location();
                    if (!id.getNamespace().equals("minecraft") && !id.getNamespace().equals("mite")) continue;
                    var row = seed.get(id.toString());
                    helper.assertTrue(row != null, "The main menu seed covers the full native and MITE registry: " + id);
                    helper.assertValueEqual(row.get("defaultMaxLevel").getAsInt(), registered.getValue().definition().maxLevel(),
                            "The bundled seed retains the actual current default level: " + id);
                    helper.assertTrue(Component.Serializer.fromJson(row.get("descriptionJson").getAsString(), access) != null,
                            "The bundled name is valid native component JSON: " + id);
                }
            }
        }
        helper.succeed();
    }

    private static Map<String, EnchantmentCatalog.Entry> defaults() {
        return EnchantmentCatalog.entries().stream().collect(Collectors.toMap(EnchantmentCatalog.Entry::id, Function.identity()));
    }
}
