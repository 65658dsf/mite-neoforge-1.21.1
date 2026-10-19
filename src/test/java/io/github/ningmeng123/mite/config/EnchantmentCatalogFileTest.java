package io.github.ningmeng123.mite.config;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Registry metadata survives offline starts and later discoveries independently of overrides. @author NingMeng123 */
class EnchantmentCatalogFileTest {
    @TempDir Path directory;

    private static EnchantmentCatalogFile.Definition definition(String id, int maximum) {
        return new EnchantmentCatalogFile.Definition(id, maximum, "{\"translate\":\"enchantment." + id.replace(':', '.') + "\"}");
    }

    @Test void atomicPersistenceRoundTripsAllLevelsAndDescriptionsInStableOrder() throws IOException {
        var first = definition("minecraft:sharpness", 9);
        var second = new EnchantmentCatalogFile.Definition("addon:new_enchantment", 7, "{\"text\":\"新增附魔\"}");
        Path file = directory.resolve("config/catalog.json");
        EnchantmentCatalogFile.write(file, List.of(first, second));
        try (var reader = Files.newBufferedReader(file)) {
            assertEquals(Map.of(first.id(), first, second.id(), second), EnchantmentCatalogFile.read(reader));
        }
        String json = Files.readString(file);
        assertTrue(json.indexOf("addon:new_enchantment") < json.indexOf("minecraft:sharpness"));
        assertTrue(json.contains("新增附魔"));
        try (var files = Files.list(file.getParent())) { assertEquals(List.of(file), files.toList()); }
    }

    @Test void newRegistriesAddAndRefreshMetadataWithoutDroppingTemporarilyMissingMods() throws IOException {
        var original = definition("minecraft:sharpness", 9);
        var missing = definition("removed_mod:remember_me", 3);
        var replacement = definition("minecraft:sharpness", 11);
        var added = definition("new_mod:new_enchantment", 4);
        Map<String, EnchantmentCatalogFile.Definition> before = Map.of(original.id(), original, missing.id(), missing);
        var merged = EnchantmentCatalogFile.merge(before, List.of(replacement, added));
        assertEquals(Map.of(replacement.id(), replacement, missing.id(), missing, added.id(), added), merged);
        assertEquals(original, before.get(original.id()), "Publishing an updated catalog cannot mutate a previous revision");
        Path file = directory.resolve("catalog.json");
        EnchantmentCatalogFile.write(file, before.values());
        EnchantmentCatalogFile.write(file, merged.values());
        try (var reader = Files.newBufferedReader(file)) { assertEquals(merged, EnchantmentCatalogFile.read(reader)); }
    }

    @Test void invalidSchemasIdsFractionalLevelsAndBrokenComponentsCannotBecomeDefaults() {
        for (String input : List.of("{}", "{\"schema\":2,\"enchantments\":[]}",
                document("Bad Namespace:sharpness", "5", "{}"), document("minecraft:sharpness", "0", "{}"),
                document("minecraft:sharpness", "256", "{}"), document("minecraft:sharpness", "1.5", "{}"),
                document("minecraft:sharpness", "5", "not-json")))
            assertThrows(IOException.class, () -> EnchantmentCatalogFile.read(new StringReader(input)), input);
    }

    private static String document(String id, String level, String description) {
        return "{\"schema\":1,\"enchantments\":[{\"id\":\"" + id + "\",\"defaultMaxLevel\":" + level
                + ",\"descriptionJson\":" + new com.google.gson.Gson().toJson(description) + "}]}";
    }
}
